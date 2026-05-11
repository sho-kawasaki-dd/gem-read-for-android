package io.github.ikinocore.gemread.android.ui.history

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.data.db.history.HistoryType
import io.github.ikinocore.gemread.android.ui.result.ResultActivity
import io.github.ikinocore.gemread.android.ui.result.ResultViewModel
import io.github.ikinocore.gemread.android.ui.theme.GemReadForAndroidTheme
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class HistoryActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            GemReadForAndroidTheme {
                val uiState by viewModel.uiState.collectAsState()
                HistoryScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    onBack = { finish() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is HistoryUiEffect.NavigateToResult -> {
                        val intent = Intent(this@HistoryActivity, ResultActivity::class.java).apply {
                            putExtra(ResultViewModel.KEY_SOURCE, "history_re_run")
                            putExtra(ResultViewModel.KEY_INPUT_TEXT, effect.entry.inputText)
                            putExtra(ResultViewModel.KEY_TEMPLATE_ID, effect.entry.templateId)
                            if (effect.entry.type == HistoryType.IMAGE && effect.entry.imagePath != null) {
                                val uri = File(effect.entry.imagePath).toUri()
                                putExtra(ResultViewModel.KEY_IMAGE_URI, uri.toString())
                            }
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
