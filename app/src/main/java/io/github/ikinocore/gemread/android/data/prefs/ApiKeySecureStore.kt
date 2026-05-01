package io.github.ikinocore.gemread.android.data.prefs

interface ApiKeySecureStore {
    suspend fun getApiKey(): String?

    suspend fun setApiKey(apiKey: String)

    suspend fun clearApiKey()
}