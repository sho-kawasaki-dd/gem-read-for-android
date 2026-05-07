package io.github.ikinocore.gemread.android.data.api

import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GeminiClientTest {

    private val securePreferences: SecurePreferences = mockk()
    private val appPreferences: AppPreferences = mockk()
    private val client = GeminiClient(securePreferences, appPreferences)

    @Test
    fun `normalizeException should convert API_KEY_INVALID to Auth error`() {
        val exception = Exception("API_KEY_INVALID")
        val normalized = client.normalizeException(exception)
        assertTrue(normalized is GeminiError.Auth)
    }

    @Test
    fun `normalizeException should convert 401 to Auth error`() {
        val exception = Exception("HTTP 401 Unauthorized")
        val normalized = client.normalizeException(exception)
        assertTrue(normalized is GeminiError.Auth)
    }

    @Test
    fun `normalizeException should convert 429 to RateLimited error`() {
        val exception = Exception("HTTP 429 Too Many Requests")
        val normalized = client.normalizeException(exception)
        assertTrue(normalized is GeminiError.RateLimited)
    }

    @Test
    fun `normalizeException should convert IOException to Network error`() {
        val exception = IOException("No internet")
        val normalized = client.normalizeException(exception)
        assertTrue(normalized is GeminiError.Network)
    }
}
