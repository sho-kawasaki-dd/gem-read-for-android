package io.github.ikinocore.gemread.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val modelName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MODEL_NAME] ?: "gemini-2.5-flash"
    }

    val baseSystemPrompt: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_BASE_SYSTEM_PROMPT] ?: ""
    }

    val isImageResizeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IMAGE_RESIZE_ENABLED] ?: true
    }

    val isStreamingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_STREAMING_ENABLED] ?: true
    }

    val isAutoDeleteEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_DELETE_ENABLED] ?: false
    }

    val historyRetentionCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_HISTORY_RETENTION_COUNT] ?: 200
    }

    val historyRetentionDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_HISTORY_RETENTION_DAYS] ?: 90
    }

    suspend fun setModelName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MODEL_NAME] = name
        }
    }

    suspend fun setBaseSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BASE_SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun setImageResizeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IMAGE_RESIZE_ENABLED] = enabled
        }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STREAMING_ENABLED] = enabled
        }
    }

    suspend fun setAutoDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_DELETE_ENABLED] = enabled
        }
    }

    suspend fun setHistoryRetentionCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HISTORY_RETENTION_COUNT] = count
        }
    }

    suspend fun setHistoryRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HISTORY_RETENTION_DAYS] = days
        }
    }

    companion object {
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_BASE_SYSTEM_PROMPT = stringPreferencesKey("base_system_prompt")
        private val KEY_IMAGE_RESIZE_ENABLED = booleanPreferencesKey("image_resize_enabled")
        private val KEY_STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        private val KEY_AUTO_DELETE_ENABLED = booleanPreferencesKey("auto_delete_enabled")
        private val KEY_HISTORY_RETENTION_COUNT = intPreferencesKey("history_retention_count")
        private val KEY_HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
    }
}
