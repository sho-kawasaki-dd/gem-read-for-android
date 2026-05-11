package io.github.ikinocore.gemread.android.domain.usecase

import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsSettingsCompletedUseCaseTest {

    private val securePreferences: SecurePreferences = mockk()
    private val useCase = IsSettingsCompletedUseCase(securePreferences)

    @Test
    fun `invoke should return true when API key is present`() {
        every { securePreferences.getApiKey() } returns "valid_api_key"
        assertTrue(useCase())
    }

    @Test
    fun `invoke should return false when API key is null`() {
        every { securePreferences.getApiKey() } returns null
        assertFalse(useCase())
    }

    @Test
    fun `invoke should return false when API key is empty`() {
        every { securePreferences.getApiKey() } returns ""
        assertFalse(useCase())
    }

    @Test
    fun `invoke should return false when API key is blank`() {
        every { securePreferences.getApiKey() } returns "   "
        assertFalse(useCase())
    }
}
