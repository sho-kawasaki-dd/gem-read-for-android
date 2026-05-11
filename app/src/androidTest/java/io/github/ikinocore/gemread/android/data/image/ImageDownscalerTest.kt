package io.github.ikinocore.gemread.android.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ImageDownscalerTest {

    private lateinit var appPreferences: AppPreferences
    private lateinit var imageDownscaler: ImageDownscaler
    private var originalResizeEnabled: Boolean = true
    private lateinit var filesDir: File

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        filesDir = context.filesDir
        appPreferences = AppPreferences(context)
        imageDownscaler = ImageDownscaler(context, appPreferences, kotlinx.coroutines.Dispatchers.IO)
        originalResizeEnabled = appPreferences.isImageResizeEnabled.first()
        imageDownscaler.clearCache()
        File(filesDir, "history").deleteRecursively()
    }

    @After
    fun tearDown() = runBlocking {
        appPreferences.setImageResizeEnabled(originalResizeEnabled)
        imageDownscaler.clearCache()
        File(filesDir, "history").deleteRecursively()
    }

    @Test
    fun processIncomingImageShouldDownscaleLargeImageWhenResizeEnabled() = runBlocking {
        appPreferences.setImageResizeEnabled(true)
        val source = createImageFile(name = "large.jpg", width = 3000, height = 1000, format = Bitmap.CompressFormat.JPEG)

        val output = imageDownscaler.processIncomingImage(Uri.fromFile(source))
        val decoded = BitmapFactory.decodeFile(output.absolutePath)

        assertEquals("jpg", output.extension.lowercase())
        assertTrue(maxOf(decoded.width, decoded.height) <= 1568)
        decoded.recycle()
    }

    @Test
    fun processIncomingImageShouldPassThroughSmallPngWhenResizeDisabled() = runBlocking {
        appPreferences.setImageResizeEnabled(false)
        val source = createImageFile(name = "small.png", width = 200, height = 100, format = Bitmap.CompressFormat.PNG)
        val sourceBytes = source.readBytes()

        val output = imageDownscaler.processIncomingImage(Uri.fromFile(source))

        assertEquals("png", output.extension.lowercase())
        assertArrayEquals(sourceBytes, output.readBytes())
    }

    @Test
    fun promoteToHistoryShouldCopyFileIntoHistoryDirectory() = runBlocking {
        val cacheDir = File(filesDir, "cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "temp.jpg").apply { writeText("temporary") }

        val historyFile = imageDownscaler.promoteToHistory(tempFile, 12L)

        assertTrue(historyFile.exists())
        assertTrue(historyFile.absolutePath.endsWith("history${File.separator}12.jpg"))
        assertEquals("temporary", historyFile.readText())
    }

    @Test
    fun clearCacheShouldDeleteTemporaryFiles() = runBlocking {
        val cacheDir = File(filesDir, "cache").apply { mkdirs() }
        File(cacheDir, "one.jpg").writeText("1")
        File(cacheDir, "two.png").writeText("2")

        imageDownscaler.clearCache()

        assertEquals(emptyList<String>(), cacheDir.listFiles()?.map { it.name } ?: emptyList<String>())
    }

    private fun createImageFile(
        name: String,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
    ): File {
        val source = File(filesDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use { output ->
            bitmap.compress(format, 100, output)
        }
        bitmap.recycle()
        return source
    }
}