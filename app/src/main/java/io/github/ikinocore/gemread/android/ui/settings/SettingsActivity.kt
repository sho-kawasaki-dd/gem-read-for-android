package io.github.ikinocore.gemread.android.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.R
import io.github.ikinocore.gemread.android.ui.theme.GemReadForAndroidTheme

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemReadForAndroidTheme {
                val viewModel: SettingsViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_settings)) },
                            navigationIcon = {
                                TextButton(onClick = ::finish) {
                                    Text(stringResource(R.string.action_close))
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    SettingsScreen(
                        uiState = uiState,
                        onApiKeyChanged = viewModel::updateApiKey,
                        onModelSelected = viewModel::updateModelName,
                        onBaseSystemPromptChanged = viewModel::updateBaseSystemPrompt,
                        onImageResizeChanged = viewModel::updateImageResizeEnabled,
                        onStreamingChanged = viewModel::updateStreamingEnabled,
                        onHistoryRetentionCountChanged = viewModel::updateHistoryRetentionCount,
                        onHistoryRetentionDaysChanged = viewModel::updateHistoryRetentionDays,
                        onTestConnection = viewModel::testConnection,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onBaseSystemPromptChanged: (String) -> Unit,
    onImageResizeChanged: (Boolean) -> Unit,
    onStreamingChanged: (Boolean) -> Unit,
    onHistoryRetentionCountChanged: (Int) -> Unit,
    onHistoryRetentionDaysChanged: (Int) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isApiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                    Text(
                        text = stringResource(
                            if (isApiKeyVisible) R.string.action_hide else R.string.action_show,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = onTestConnection,
            enabled = uiState.apiKey.isNotBlank() && uiState.connectionStatus != SettingsConnectionStatus.Testing,
            modifier = Modifier.widthIn(min = 180.dp),
        ) {
            Text(
                text = stringResource(
                    if (uiState.connectionStatus == SettingsConnectionStatus.Testing) {
                        R.string.settings_connection_testing
                    } else {
                        R.string.settings_test_connection
                    },
                ),
            )
        }

        ConnectionStatusCard(connectionStatus = uiState.connectionStatus)

        HorizontalDivider()

        Text(
            text = stringResource(R.string.settings_model_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Box {
            OutlinedButton(
                onClick = { modelMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(uiState.modelName)
            }
            DropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false },
            ) {
                SettingsModelOptions.forEach { modelName ->
                    DropdownMenuItem(
                        text = { Text(modelName) },
                        onClick = {
                            modelMenuExpanded = false
                            onModelSelected(modelName)
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.baseSystemPrompt,
            onValueChange = onBaseSystemPromptChanged,
            label = { Text(stringResource(R.string.settings_system_prompt_label)) },
            placeholder = { Text(stringResource(R.string.settings_system_prompt_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )

        SettingSwitchRow(
            title = stringResource(R.string.settings_image_resize_label),
            summary = stringResource(R.string.settings_image_resize_summary),
            checked = uiState.isImageResizeEnabled,
            onCheckedChange = onImageResizeChanged,
        )

        SettingSwitchRow(
            title = stringResource(R.string.settings_streaming_label),
            summary = stringResource(R.string.settings_streaming_summary),
            checked = uiState.isStreamingEnabled,
            onCheckedChange = onStreamingChanged,
        )

        OutlinedTextField(
            value = uiState.historyRetentionCount.toString(),
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }
                if (digits.isNotEmpty()) {
                    digits.toIntOrNull()?.let(onHistoryRetentionCountChanged)
                }
            },
            label = { Text(stringResource(R.string.settings_history_count_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.historyRetentionDays.toString(),
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }
                if (digits.isNotEmpty()) {
                    digits.toIntOrNull()?.let(onHistoryRetentionDaysChanged)
                }
            },
            label = { Text(stringResource(R.string.settings_history_days_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

@Composable
private fun ConnectionStatusCard(connectionStatus: SettingsConnectionStatus) {
    if (connectionStatus == SettingsConnectionStatus.Idle) {
        return
    }

    val message = when (connectionStatus) {
        SettingsConnectionStatus.Testing -> stringResource(R.string.settings_connection_testing)
        SettingsConnectionStatus.Success -> stringResource(R.string.settings_connection_success)
        SettingsConnectionStatus.AuthError -> stringResource(R.string.error_auth)
        SettingsConnectionStatus.NetworkError -> stringResource(R.string.error_network)
        SettingsConnectionStatus.RateLimitedError -> stringResource(R.string.error_rate_limited)
        SettingsConnectionStatus.UnknownError -> stringResource(R.string.error_unknown)
        SettingsConnectionStatus.Idle -> ""
    }
    val isSuccess = connectionStatus == SettingsConnectionStatus.Success

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Text(
            text = message,
            color = if (isSuccess) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private val SettingsModelOptions = listOf(
    "gemini-2.5-flash",
    "gemini-2.5-pro",
    "gemini-2.5-flash-lite",
)
