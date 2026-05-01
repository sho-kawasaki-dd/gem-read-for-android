package io.github.ikinocore.gemread.android.data.repository

import android.graphics.BitmapFactory
import android.net.Uri
import io.github.ikinocore.gemread.android.data.api.GeminiClient
import io.github.ikinocore.gemread.android.data.image.ImageDownscaler
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.domain.repository.GenerationRepository
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GenerationRepository] の実装。
 * テンプレート解決 → 画像処理 → Gemini 呼び出し の一連のパイプラインを調整する。
 * 履歴保存は Step 9 で HistoryRepository が追加された段階で本クラスに組み込む。
 */
@Singleton
class GenerationRepositoryImpl @Inject constructor(
    private val geminiClient: GeminiClient,
    private val imageDownscaler: ImageDownscaler,
    private val promptTemplateRepository: PromptTemplateRepository,
    private val appPreferences: AppPreferences,
) : GenerationRepository {

    override fun generate(
        prompt: String,
        imageUri: Uri?,
        templateId: Long?,
    ): Flow<String> = flow {
        // テンプレート取得: 指定 ID があればそれを、なければデフォルトを使用する。
        val template = if (templateId != null) {
            promptTemplateRepository.getTemplateById(templateId)
        } else {
            promptTemplateRepository.getDefaultTemplate()
        }

        // システムプロンプト構築: ベースシステムプロンプト → テンプレート固有プロンプト の順で結合する。
        // どちらか一方でも空なら区切り文字（改行2つ）は挿入しない。
        val baseSystemPrompt = appPreferences.baseSystemPrompt.first().trim()
        val templateSystemPrompt = template?.systemPrompt?.trim().orEmpty()
        val systemPrompt = buildString {
            if (baseSystemPrompt.isNotEmpty()) append(baseSystemPrompt)
            if (baseSystemPrompt.isNotEmpty() && templateSystemPrompt.isNotEmpty()) append("\n\n")
            if (templateSystemPrompt.isNotEmpty()) append(templateSystemPrompt)
        }.takeIf { it.isNotEmpty() }

        // 画像処理: Uri から内部キャッシュへコピー・ダウンスケール後、Bitmap に変換して Gemini へ渡す。
        // grant Uri の永続化は行わず、この時点で即座に内部コピーする。
        val bitmap = imageUri?.let { uri ->
            val processedFile = imageDownscaler.processIncomingImage(uri)
            BitmapFactory.decodeFile(processedFile.absolutePath)
        }

        // Gemini 呼び出し: 生成 Flow をそのまま転送する。
        // 自動再試行は行わず、エラー時は GeminiError としてスローし呼び出し元に委ねる。
        emitAll(geminiClient.generateContent(prompt, systemPrompt, bitmap))
    }
}
