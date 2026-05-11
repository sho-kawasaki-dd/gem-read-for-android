package io.github.ikinocore.gemread.android.data.history

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ikinocore.gemread.android.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 履歴画像ディレクトリ（filesDir/history/）のファイル寿命を管理する。
 * DB 削除後の best effort cleanup と、起動時の孤児ファイル sweep をここに集約する。
 */
@Singleton
class HistoryImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    companion object {
        private const val HISTORY_DIR_NAME = "history"
    }

    suspend fun deleteManagedImages(imagePaths: Collection<String>) = withContext(ioDispatcher) {
        if (imagePaths.isEmpty()) return@withContext

        imagePaths.asSequence()
            .mapNotNull(::resolveManagedFile)
            .distinctBy { it.absolutePath }
            .forEach { file ->
                runCatching {
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
    }

    suspend fun sweepOrphanedImages(trackedImagePaths: Collection<String>) = withContext(ioDispatcher) {
        val historyDir = historyDirectory()
        if (!historyDir.exists()) return@withContext

        val trackedFiles = trackedImagePaths.asSequence()
            .mapNotNull(::resolveManagedFile)
            .map { it.absolutePath }
            .toSet()

        historyDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.filterNot { it.absolutePath in trackedFiles }
            ?.forEach { file ->
                runCatching {
                    file.delete()
                }
            }
    }

    private fun resolveManagedFile(imagePath: String): File? {
        val managedDirectory = runCatching { historyDirectory().canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(imagePath).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.parentFile == managedDirectory }
    }

    private fun historyDirectory(): File = File(context.filesDir, HISTORY_DIR_NAME)
}