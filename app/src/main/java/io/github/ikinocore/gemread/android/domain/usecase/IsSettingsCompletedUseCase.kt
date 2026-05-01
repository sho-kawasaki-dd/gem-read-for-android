package io.github.ikinocore.gemread.android.domain.usecase

import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import javax.inject.Inject

/**
 * Use case to check if mandatory settings (e.g. API key) are completed.
 */
class IsSettingsCompletedUseCase @Inject constructor(
    private val securePreferences: SecurePreferences,
) {
    operator fun invoke(): Boolean {
        val apiKey = securePreferences.getApiKey()
        return !apiKey.isNullOrBlank()
    }
}
