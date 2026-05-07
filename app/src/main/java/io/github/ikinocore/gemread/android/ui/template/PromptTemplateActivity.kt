package io.github.ikinocore.gemread.android.ui.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.ui.theme.GemReadForAndroidTheme

@AndroidEntryPoint
class PromptTemplateActivity : ComponentActivity() {

    private val viewModel: PromptTemplateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            GemReadForAndroidTheme {
                val uiState by viewModel.uiState.collectAsState()
                PromptTemplateScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    onBack = { finish() }
                )
            }
        }
    }
}
