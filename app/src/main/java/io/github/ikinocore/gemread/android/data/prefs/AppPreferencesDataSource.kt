package io.github.ikinocore.gemread.android.data.prefs

import io.github.ikinocore.gemread.android.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow

interface AppPreferencesDataSource {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences)
}