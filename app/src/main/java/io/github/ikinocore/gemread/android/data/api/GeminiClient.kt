package io.github.ikinocore.gemread.android.data.api

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val appPreferences: AppPreferences,
) {
    private suspend fun getGenerativeModel(systemPrompt: String? = null): GenerativeModel {
        val apiKey = securePreferences.getApiKey() ?: throw GeminiError.Auth
        val modelName = appPreferences.modelName.first()

        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            systemInstruction = systemPrompt?.let { content { text(it) } },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
            ),
        )
    }

    /**
     * Generates content from text and optional image.
     * Normalizes errors and supports streaming.
     */
    fun generateContent(
        prompt: String,
        systemPrompt: String? = null,
        image: Bitmap? = null,
    ): Flow<String> = flow {
        val model = getGenerativeModel(systemPrompt)
        val inputContent = content {
            image?.let { image(it) }
            text(prompt)
        }

        try {
            val isStreaming = appPreferences.isStreamingEnabled.first()
            if (isStreaming) {
                model.generateContentStream(inputContent).collect { response ->
                    response.text?.let { emit(it) }
                }
            } else {
                val response = model.generateContent(inputContent)
                response.text?.let { emit(it) }
            }
        } catch (e: Exception) {
            throw normalizeException(e)
        }
    }

    /**
     * Performs a connection test (ping) with minimal cost.
     */
    suspend fun testConnection(): Result<Unit> = runCatching {
        val model = getGenerativeModel()
        model.generateContent("ping")
        Unit
    }.onFailure {
        throw normalizeException(it)
    }

    private fun normalizeException(e: Throwable): GeminiError = when {
        e is GeminiError -> e
        // HTTP 認証エラーを Auth に正規化する。
        e.message?.contains("API_KEY_INVALID", ignoreCase = true) == true -> GeminiError.Auth
        e.message?.contains("401") == true -> GeminiError.Auth
        // レートリミット・クォータエラーを RateLimited に正規化する。
        e.message?.contains("429") == true -> GeminiError.RateLimited
        e.message?.contains("Quota exceeded", ignoreCase = true) == true -> GeminiError.RateLimited
        // ネットワーク接続エラー（ソケット切断・タイムアウト・DNS 解決失敗等）を Network に正規化する。
        e is IOException -> GeminiError.Network
        // SDK が例外をラップしている場合、原因の例外を再帰的に検査する。
        e.cause != null && e.cause !== e -> normalizeException(e.cause!!)
        else -> GeminiError.Unknown(e)
    }
}
