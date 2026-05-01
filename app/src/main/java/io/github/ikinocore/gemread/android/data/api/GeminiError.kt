package io.github.ikinocore.gemread.android.data.api

/**
 * Normalized errors for Gemini API.
 */
sealed class GeminiError : Throwable() {
    data object Auth : GeminiError()
    data object Network : GeminiError()
    data object RateLimited : GeminiError()
    data class Unknown(val original: Throwable) : GeminiError()
}
