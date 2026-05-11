package io.github.ikinocore.gemread.android.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.TransactionTooLargeException
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.MainActivity
import io.github.ikinocore.gemread.android.R
import io.github.ikinocore.gemread.android.domain.usecase.IsSettingsCompletedUseCase
import io.github.ikinocore.gemread.android.ui.result.ResultActivity
import io.github.ikinocore.gemread.android.ui.result.ResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var isSettingsCompletedUseCase: IsSettingsCompletedUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                if (!isSettingsCompletedUseCase()) {
                    launchMainActivity(
                        message = getString(R.string.share_requires_settings_message),
                        requireSettings = true,
                    )
                    return@launch
                }

                val resultIntent = withContext(Dispatchers.IO) {
                    buildResultIntent(intent)
                }

                if (resultIntent == null) {
                    launchMainActivity(message = getString(R.string.error_unsupported_share_input))
                    return@launch
                }

                startActivity(resultIntent)
            } catch (_: TransactionTooLargeException) {
                launchMainActivity(message = getString(R.string.error_transaction_too_large))
            } catch (_: IllegalStateException) {
                launchMainActivity(message = getString(R.string.error_transaction_too_large))
            }
            finish()
        }
    }

    private suspend fun buildResultIntent(sourceIntent: Intent?): Intent? {
        if (sourceIntent == null) return null

        val payload = normalizeIntent(sourceIntent) ?: return null
        return Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultViewModel.KEY_SOURCE, payload.source)
            putExtra(ResultViewModel.KEY_IS_MULTIPLE, payload.isMultipleImages)
            payload.imageUri?.let { imageUri ->
                putExtra(ResultViewModel.KEY_IMAGE_URI, imageUri.toString())
                type = payload.mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            payload.inputText?.let { putExtra(ResultViewModel.KEY_INPUT_TEXT, it) }
            payload.inputTextPath?.let { putExtra(ResultViewModel.KEY_INPUT_TEXT_PATH, it) }
            payload.templateId?.let { putExtra(ResultViewModel.KEY_TEMPLATE_ID, it) }
        }
    }

    private suspend fun normalizeIntent(sourceIntent: Intent): NormalizedSharePayload? {
        val action = sourceIntent.action ?: return null
        val templateId = sourceIntent.getLongExtra(ResultViewModel.KEY_TEMPLATE_ID, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }

        return when (action) {
            Intent.ACTION_SEND -> {
                if (sourceIntent.type == MIME_TEXT_PLAIN) {
                    val text = extractText(sourceIntent, Intent.EXTRA_TEXT) ?: return null
                    textPayload(text = text, source = SOURCE_SEND_TEXT, templateId = templateId)
                } else {
                    val imageUri = extractSingleStreamUri(sourceIntent) ?: return null
                    imagePayload(
                        imageUri = imageUri,
                        mimeType = sourceIntent.type,
                        source = SOURCE_SEND_IMAGE,
                        templateId = templateId,
                        isMultipleImages = false,
                    )
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val imageUris = extractMultipleStreamUris(sourceIntent)
                val firstImageUri = imageUris.firstOrNull() ?: return null
                imagePayload(
                    imageUri = firstImageUri,
                    mimeType = sourceIntent.type,
                    source = SOURCE_SEND_MULTIPLE,
                    templateId = templateId,
                    isMultipleImages = imageUris.size > 1,
                )
            }

            Intent.ACTION_PROCESS_TEXT -> {
                val text = extractText(sourceIntent, Intent.EXTRA_PROCESS_TEXT) ?: return null
                textPayload(text = text, source = SOURCE_PROCESS_TEXT, templateId = templateId)
            }

            else -> null
        }
    }

    private suspend fun textPayload(
        text: String,
        source: String,
        templateId: Long?,
    ): NormalizedSharePayload {
        val normalizedText = text.trim()

        // Binder 上限に近づく長文だけ cache file に退避し、Activity 間ではパスのみを渡す。
        return if (shouldWriteTextToCache(normalizedText)) {
            NormalizedSharePayload(
                inputTextPath = writeTextToCache(normalizedText),
                source = source,
                templateId = templateId,
            )
        } else {
            NormalizedSharePayload(
                inputText = normalizedText,
                source = source,
                templateId = templateId,
            )
        }
    }

    private fun imagePayload(
        imageUri: Uri,
        mimeType: String?,
        source: String,
        templateId: Long?,
        isMultipleImages: Boolean,
    ): NormalizedSharePayload? {
        if (!SUPPORTED_IMAGE_MIME_TYPES.contains(mimeType)) return null

        return NormalizedSharePayload(
            imageUri = imageUri,
            mimeType = mimeType,
            source = source,
            templateId = templateId,
            isMultipleImages = isMultipleImages,
        )
    }

    private fun extractText(intent: Intent, extraKey: String): String? {
        return intent.getCharSequenceExtra(extraKey)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun extractSingleStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun extractMultipleStreamUris(intent: Intent): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }

    private fun shouldWriteTextToCache(text: String): Boolean {
        return text.length > MAX_INLINE_TEXT_CHARS || text.toByteArray(Charsets.UTF_8).size > MAX_INLINE_TEXT_BYTES
    }

    private fun writeTextToCache(text: String): String {
        val directory = File(cacheDir, TEXT_CACHE_DIR_NAME).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.txt")
        file.writeText(text)
        return file.absolutePath
    }

    private fun launchMainActivity(
        message: String? = null,
        requireSettings: Boolean = false,
    ) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_STARTUP_MESSAGE, message)
                putExtra(MainActivity.EXTRA_REQUIRE_SETTINGS, requireSettings)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    private data class NormalizedSharePayload(
        val imageUri: Uri? = null,
        val mimeType: String? = null,
        val inputText: String? = null,
        val inputTextPath: String? = null,
        val source: String,
        val templateId: Long? = null,
        val isMultipleImages: Boolean = false,
    )

    companion object {
        private const val MIME_TEXT_PLAIN = "text/plain"
        private const val MAX_INLINE_TEXT_CHARS = 50_000
        private const val MAX_INLINE_TEXT_BYTES = 200 * 1024
        private const val TEXT_CACHE_DIR_NAME = "shared-text"
        private const val SOURCE_SEND_TEXT = "action_send_text"
        private const val SOURCE_SEND_IMAGE = "action_send_image"
        private const val SOURCE_SEND_MULTIPLE = "action_send_multiple"
        private const val SOURCE_PROCESS_TEXT = "action_process_text"

        private val SUPPORTED_IMAGE_MIME_TYPES = setOf(
            "image/png",
            "image/jpeg",
            "image/webp",
        )
    }
}
