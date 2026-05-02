package io.github.ikinocore.gemread.android.data.repository

import android.graphics.BitmapFactory
import android.net.Uri
import io.github.ikinocore.gemread.android.data.api.GeminiClient
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.data.db.history.HistoryType
import io.github.ikinocore.gemread.android.data.image.ImageDownscaler
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.domain.model.GenerationEvent
import io.github.ikinocore.gemread.android.domain.repository.GenerationRepository
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GenerationRepository] の実装。
 * テンプレート解決 → 画像処理 → Gemini 呼び出し の一連のパイプラインを調整する。
 * 生成完了後に [HistoryRepository] を通じて履歴を永続化する。
 */
@Singleton
class GenerationRepositoryImpl @Inject constructor(
    private val geminiClient: GeminiClient,
    private val imageDownscaler: ImageDownscaler,
    private val promptTemplateRepository: PromptTemplateRepository,
    private val historyRepository: HistoryRepository,
    private val appPreferences: AppPreferences,
) : GenerationRepository {

    override fun generate(
        prompt: String,
        imageUri: Uri?,
        templateId: Long?,
    ): Flow<GenerationEvent> = flow {
        // テンプレート取得
        val template = if (templateId != null) {
            promptTemplateRepository.getTemplateById(templateId)
        } else {
            promptTemplateRepository.getDefaultTemplate()
        }

        // システムプロンプト構築
        val baseSystemPrompt = appPreferences.baseSystemPrompt.first().trim()
        val templateSystemPrompt = template?.systemPrompt?.trim().orEmpty()
        val systemPrompt = buildString {
            if (baseSystemPrompt.isNotEmpty()) append(baseSystemPrompt)
            if (baseSystemPrompt.isNotEmpty() && templateSystemPrompt.isNotEmpty()) append("\n\n")
            if (templateSystemPrompt.isNotEmpty()) append(templateSystemPrompt)
        }.takeIf { it.isNotEmpty() }

        // 画像処理: 一時ファイルへのコピーとダウンスケール
        var tempImageFile: File? = null
        val bitmap = imageUri?.let { uri ->
            val processedFile = imageDownscaler.processIncomingImage(uri)
            tempImageFile = processedFile
            BitmapFactory.decodeFile(processedFile.absolutePath)
        }

        val fullResponse = StringBuilder()
        val modelName = appPreferences.modelName.first()

        try {
            geminiClient.generateContent(prompt, systemPrompt, bitmap).collect { chunk ->
                fullResponse.append(chunk)
                emit(GenerationEvent.Chunk(chunk))
            }

            // 生成成功時: 履歴を保存し、完了イベントを emit する
            val historyId = saveToHistory(
                type = if (imageUri != null) HistoryType.IMAGE else HistoryType.TEXT,
                inputText = prompt,
                outputText = fullResponse.toString(),
                modelName = modelName,
                templateId = template?.id,
                tempImageFile = tempImageFile,
            )
            // historyId を ViewModel に伝え、ピン留め等の操作を可能にする
            emit(GenerationEvent.Completed(historyId))
        } finally {
            bitmap?.recycle()
        }
    }.onCompletion {
        // Pruning: 履歴保存のタイミング、または定期的に発火させる。
        // ここでは生成完了ごとに実行する（設定に基づき最大件数と日数を適用）。
        val maxCount = appPreferences.historyRetentionCount.first()
        val maxDays = appPreferences.historyRetentionDays.first()
        historyRepository.pruneHistory(maxCount, maxDays)
    }

    private suspend fun saveToHistory(
        type: HistoryType,
        inputText: String,
        outputText: String,
        modelName: String,
        templateId: Long?,
        tempImageFile: File?,
    ): Long {
        // 1. DB にレコードを仮挿入して ID を取得する。
        val historyId = historyRepository.insertHistory(
            HistoryEntryEntity(
                type = type,
                inputText = inputText,
                outputText = outputText,
                modelName = modelName,
                templateId = templateId,
            ),
        )

        // 2. 画像がある場合、一時ファイルを履歴ディレクトリへ昇格させ、パスを更新する。
        tempImageFile?.let { file ->
            val historyFile = imageDownscaler.promoteToHistory(file, historyId)
            historyRepository.updateHistory(
                historyRepository.getHistoryById(historyId)!!.copy(
                    imagePath = historyFile.absolutePath,
                ),
            )
        }

        return historyId
    }
}
