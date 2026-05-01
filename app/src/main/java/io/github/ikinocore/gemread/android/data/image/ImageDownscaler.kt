package io.github.ikinocore.gemread.android.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageDownscaler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    companion object {
        private const val CACHE_DIR_NAME = "image_cache"
        private const val MAX_SIDE_RESIZE_ON = 1568
        private const val JPEG_QUALITY_RESIZE_ON = 85

        private const val MAX_SIDE_FAILSAFE = 4096
        private const val MAX_SIZE_BYTES_FAILSAFE = 4 * 1024 * 1024 // 4MB
    }

    /**
     * Copies the image from the given Uri to internal cache, downscaling it if necessary.
     * Returns the local File pointing to the processed image.
     */
    suspend fun processIncomingImage(uri: Uri): File = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }
        val extension = getExtensionFromUri(uri) ?: "jpg"
        val tempFile = File(cacheDir, "${UUID.randomUUID()}.$extension")

        val resizeEnabled = appPreferences.isImageResizeEnabled.first()

        context.contentResolver.openInputStream(uri)?.use { input ->
            if (!resizeEnabled && isPassthroughAllowed(uri)) {
                // Resize OFF and image is within failsafe limits: Copy directly
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
                return@withContext tempFile
            }
        } ?: throw IllegalArgumentException("Failed to open Uri: $uri")

        // Otherwise: Decode, downscale, and re-encode to JPEG
        val targetFile = if (extension == "jpg") tempFile else File(cacheDir, "${UUID.randomUUID()}.jpg")
        downscaleAndSave(uri, targetFile, if (resizeEnabled) MAX_SIDE_RESIZE_ON else MAX_SIDE_FAILSAFE)
        targetFile
    }

    /**
     * Cleans up all temporary files in the image cache directory.
     */
    suspend fun clearCache() = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun isPassthroughAllowed(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        val allowedMimeTypes = listOf("image/jpeg", "image/png", "image/webp")
        if (mimeType !in allowedMimeTypes) return false

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val maxSide = maxOf(options.outWidth, options.outHeight)
        if (maxSide > MAX_SIDE_FAILSAFE) return false

        val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            it.length
        } ?: 0
        return fileSize <= MAX_SIZE_BYTES_FAILSAFE
    }

    private suspend fun downscaleAndSave(uri: Uri, targetFile: File, maxSide: Int) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        var inSampleSize = 1
        if (options.outHeight > maxSide || options.outWidth > maxSide) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= maxSide && halfWidth / inSampleSize >= maxSide) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode bitmap")

        val rotatedBitmap = rotateIfRequired(context, bitmap, uri)
        val finalBitmap = if (rotatedBitmap.width > maxSide || rotatedBitmap.height > maxSide) {
            val scale = maxSide.toFloat() / maxOf(rotatedBitmap.width, rotatedBitmap.height)
            Bitmap.createScaledBitmap(
                rotatedBitmap,
                (rotatedBitmap.width * scale).toInt(),
                (rotatedBitmap.height * scale).toInt(),
                true,
            )
        } else {
            rotatedBitmap
        }

        FileOutputStream(targetFile).use { output ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_RESIZE_ON, output)
        }

        if (finalBitmap != bitmap && finalBitmap != rotatedBitmap) finalBitmap.recycle()
        if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
        bitmap.recycle()
    }

    private fun rotateIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        val input = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(input)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getExtensionFromUri(uri: Uri): String? = if (uri.scheme == "content") {
        val mimeType = context.contentResolver.getType(uri)
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    } else {
        MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    }
}
