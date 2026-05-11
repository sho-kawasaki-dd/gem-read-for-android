package io.github.ikinocore.gemread.android.data.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecurePreferencesTest {

    private lateinit var securePreferences: SecurePreferences
    private var originalApiKey: String? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        securePreferences = SecurePreferences(context)
        originalApiKey = securePreferences.getApiKey()
    }

    @After
    fun tearDown() {
        val original = originalApiKey
        if (original == null) {
            securePreferences.clearApiKey()
        } else {
            securePreferences.setApiKey(original)
        }
    }

    @Test
    fun apiKeyShouldRoundTripAndClear() {
        securePreferences.setApiKey("test-api-key")

        assertEquals("test-api-key", securePreferences.getApiKey())

        securePreferences.clearApiKey()

        assertNull(securePreferences.getApiKey())
    }

    @Test
    fun backingFileShouldNotContainPlaintextApiKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val plainApiKey = "plain-visible-api-key"

        securePreferences.setApiKey(plainApiKey)

        val xmlFile = File(context.applicationInfo.dataDir, "shared_prefs/secure_prefs.xml")
        assertFalse(xmlFile.readText().contains(plainApiKey))
        assertFalse(xmlFile.readText().contains("api_key"))
    }
}