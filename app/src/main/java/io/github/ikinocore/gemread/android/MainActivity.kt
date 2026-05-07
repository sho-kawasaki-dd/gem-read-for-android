package io.github.ikinocore.gemread.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.domain.usecase.IsSettingsCompletedUseCase
import io.github.ikinocore.gemread.android.ui.history.HistoryActivity
import io.github.ikinocore.gemread.android.ui.result.ResultActivity
import io.github.ikinocore.gemread.android.ui.result.ResultViewModel
import io.github.ikinocore.gemread.android.ui.settings.SettingsActivity
import io.github.ikinocore.gemread.android.ui.template.PromptTemplateActivity
import io.github.ikinocore.gemread.android.ui.theme.GemReadForAndroidTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var isSettingsCompletedUseCase: IsSettingsCompletedUseCase

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startupMessage = intent.getStringExtra(EXTRA_STARTUP_MESSAGE)
        val requireSettings = intent.getBooleanExtra(EXTRA_REQUIRE_SETTINGS, false) || !isSettingsCompletedUseCase()

        enableEdgeToEdge()
        setContent {
            GemReadForAndroidTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.app_name)) })
                    },
                ) { innerPadding ->
                    HomeScreen(
                        startupMessage = startupMessage,
                        requireSettings = requireSettings,
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onOpenTemplates = {
                            startActivity(Intent(this, PromptTemplateActivity::class.java))
                        },
                        onOpenHistory = {
                            startActivity(Intent(this, HistoryActivity::class.java))
                        },
                        onGenerate = { text ->
                            val intent = Intent(this, ResultActivity::class.java).apply {
                                putExtra(ResultViewModel.KEY_SOURCE, "manual_input")
                                putExtra(ResultViewModel.KEY_INPUT_TEXT, text)
                            }
                            startActivity(intent)
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_STARTUP_MESSAGE = "startup_message"
        const val EXTRA_REQUIRE_SETTINGS = "require_settings"
    }
}

@Composable
private fun HomeScreen(
    startupMessage: String?,
    requireSettings: Boolean,
    onOpenSettings: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenHistory: () -> Unit,
    onGenerate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var manualText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.main_description),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (startupMessage != null || requireSettings) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = startupMessage ?: stringResource(R.string.main_settings_required_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (requireSettings) {
                        Button(onClick = onOpenSettings) {
                            Text(stringResource(R.string.action_open_settings))
                        }
                    }
                }
            }
        }

        if (!requireSettings) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.main_manual_input_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.main_manual_input_placeholder)) },
                        minLines = 3,
                    )
                    Button(
                        onClick = {
                            onGenerate(manualText)
                            manualText = ""
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = manualText.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_generate))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.title_history))
            }

            Button(
                onClick = onOpenTemplates,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.title_templates))
            }
        }

        if (!requireSettings) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.title_settings))
            }
        }
    }
}
