package io.github.ikinocore.gemread.android.data.history

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * 履歴画像 cleanup の単体テスト。
 * 履歴ディレクトリ配下のみを削除対象にし、tracked なファイルは sweep で残ることを確認する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryImageStoreTest {

    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var historyImageStore: HistoryImageStore

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        filesDir = createTempDirectory("history-image-store-test").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        historyImageStore = HistoryImageStore(context, testDispatcher)
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `deleteManagedImages removes files under history directory`() = runTest(testDispatcher) {
        val historyDir = File(filesDir, "history").apply { mkdirs() }
        val managedFile = File(historyDir, "1.jpg").apply { writeText("managed") }

        historyImageStore.deleteManagedImages(listOf(managedFile.absolutePath))

        assertFalse(managedFile.exists())
    }

    @Test
    fun `deleteManagedImages ignores files outside history directory`() = runTest(testDispatcher) {
        val externalDir = File(filesDir, "external").apply { mkdirs() }
        val externalFile = File(externalDir, "1.jpg").apply { writeText("external") }

        historyImageStore.deleteManagedImages(listOf(externalFile.absolutePath))

        assertTrue(externalFile.exists())
    }

    @Test
    fun `sweepOrphanedImages deletes only untracked history files`() = runTest(testDispatcher) {
        val historyDir = File(filesDir, "history").apply { mkdirs() }
        val trackedFile = File(historyDir, "10.jpg").apply { writeText("tracked") }
        val orphanFile = File(historyDir, "11.jpg").apply { writeText("orphan") }

        historyImageStore.sweepOrphanedImages(listOf(trackedFile.absolutePath))

        assertTrue(trackedFile.exists())
        assertFalse(orphanFile.exists())
    }
}