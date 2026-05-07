package io.github.ikinocore.gemread.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.domain.usecase.IsSettingsCompletedUseCase
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
        } else {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.action_open_settings))
            }
        }

        Button(
            onClick = onOpenTemplates,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.title_templates))
        }
    }
}
