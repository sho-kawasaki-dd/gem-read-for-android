package io.github.ikinocore.gemread.android.data.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {

    private lateinit var appPreferences: AppPreferences
    private lateinit var originalValues: Snapshot

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        appPreferences = AppPreferences(context)
        originalValues = snapshot()
    }

    @After
    fun tearDown() = runBlocking {
        restore(originalValues)
    }

    @Test
    fun settersShouldPersistUpdatedValues() = runBlocking {
        appPreferences.setModelName("gemini-2.5-pro")
        appPreferences.setBaseSystemPrompt("base prompt")
        appPreferences.setImageResizeEnabled(false)
        appPreferences.setStreamingEnabled(false)
        appPreferences.setAutoDeleteEnabled(true)
        appPreferences.setHistoryRetentionCount(123)
        appPreferences.setHistoryRetentionDays(45)

        assertEquals("gemini-2.5-pro", appPreferences.modelName.first())
        assertEquals("base prompt", appPreferences.baseSystemPrompt.first())
        assertEquals(false, appPreferences.isImageResizeEnabled.first())
        assertEquals(false, appPreferences.isStreamingEnabled.first())
        assertEquals(true, appPreferences.isAutoDeleteEnabled.first())
        assertEquals(123, appPreferences.historyRetentionCount.first())
        assertEquals(45, appPreferences.historyRetentionDays.first())
    }

    private suspend fun snapshot() = Snapshot(
        modelName = appPreferences.modelName.first(),
        baseSystemPrompt = appPreferences.baseSystemPrompt.first(),
        imageResizeEnabled = appPreferences.isImageResizeEnabled.first(),
        streamingEnabled = appPreferences.isStreamingEnabled.first(),
        autoDeleteEnabled = appPreferences.isAutoDeleteEnabled.first(),
        historyRetentionCount = appPreferences.historyRetentionCount.first(),
        historyRetentionDays = appPreferences.historyRetentionDays.first(),
    )

    private suspend fun restore(snapshot: Snapshot) {
        appPreferences.setModelName(snapshot.modelName)
        appPreferences.setBaseSystemPrompt(snapshot.baseSystemPrompt)
        appPreferences.setImageResizeEnabled(snapshot.imageResizeEnabled)
        appPreferences.setStreamingEnabled(snapshot.streamingEnabled)
        appPreferences.setAutoDeleteEnabled(snapshot.autoDeleteEnabled)
        appPreferences.setHistoryRetentionCount(snapshot.historyRetentionCount)
        appPreferences.setHistoryRetentionDays(snapshot.historyRetentionDays)
    }

    private data class Snapshot(
        val modelName: String,
        val baseSystemPrompt: String,
        val imageResizeEnabled: Boolean,
        val streamingEnabled: Boolean,
        val autoDeleteEnabled: Boolean,
        val historyRetentionCount: Int,
        val historyRetentionDays: Int,
    )
}