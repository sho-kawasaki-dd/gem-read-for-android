package io.github.ikinocore.gemread.android.domain.repository

import io.github.ikinocore.gemread.android.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository for regular application preferences stored outside the secure API key store.
 */
interface AppPreferenceRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences)
}
