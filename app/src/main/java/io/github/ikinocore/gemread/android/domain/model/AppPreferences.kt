package io.github.ikinocore.gemread.android.domain.model

data class AppPreferences(
    val modelName: String = "gemini-2.5-flash",
    val baseSystemPrompt: String = "",
    val resizeImagesBeforeUpload: Boolean = true,
    val enableStreaming: Boolean = true,
    val historyRetentionCount: Int = 200,
    val historyRetentionDays: Int = 90,
)