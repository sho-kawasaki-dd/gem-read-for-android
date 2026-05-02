package io.github.ikinocore.gemread.android.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import dagger.hilt.android.AndroidEntryPoint
import io.github.ikinocore.gemread.android.R
import io.github.ikinocore.gemread.android.data.api.GeminiError
import io.github.ikinocore.gemread.android.ui.settings.SettingsActivity
import io.github.ikinocore.gemread.android.ui.theme.GemReadForAndroidTheme

/**
 * ResultActivity: Gemini API の実行結果をボトムシート形式で表示する Activity。
 * 透明テーマ（Theme.GemReadForAndroid.Translucent）を適用し、ModalBottomSheet を使用する。
 */
@AndroidEntryPoint
class ResultActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // エッジトゥエッジ表示を有効化
        enableEdgeToEdge()

        setContent {
            GemReadForAndroidTheme {
                val viewModel: ResultViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = false, // 常に最大展開またはハーフ展開を目指す
                )

                // ViewModel からのワンショットイベント（UIEffect）を処理
                LaunchedEffect(Unit) {
                    viewModel.uiEffect.collect { effect ->
                        when (effect) {
                            is ResultUiEffect.CopyToClipboard -> {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Gemini Output", effect.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.action_copy, Toast.LENGTH_SHORT).show()
                            }
                            ResultUiEffect.NavigateToSettings -> {
                                startActivity(Intent(this@ResultActivity, SettingsActivity::class.java))
                            }
                            ResultUiEffect.ShowPinnedMessage -> {
                                Toast.makeText(context, R.string.action_pin, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // ボトムシート本体
                ModalBottomSheet(
                    onDismissRequest = {
                        // シートが閉じられたら Activity も終了する
                        viewModel.onEvent(ResultUiEvent.OnDismiss)
                        finish()
                    },
                    sheetState = sheetState,
                    // 画面の 90% 程度の高さに制限（背後の Activity が少し見えるようにする）
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
                ) {
                    ResultScreen(
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        }
    }
}

/**
 * 結果画面のメインコンテンツ。
 * スクロール可能で、入力プレビュー、テンプレート選択、Markdown 出力、アクションボタンを含む。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    uiState: ResultUiState,
    onEvent: (ResultUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            // IME 出現時や長文時にスクロール可能にする
            .verticalScroll(rememberScrollState()),
    ) {
        // 複数画像共有時の通知バナー
        if (uiState.isMultipleImages) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.msg_multiple_images),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 入力情報のプレビュー（画像 + テキスト）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.imageUri != null) {
                AsyncImage(
                    model = uiState.imageUri,
                    contentDescription = stringResource(R.string.content_description_input_preview),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = uiState.inputText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // プロンプトテンプレートの選択チップ
        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.templates) { template ->
                FilterChip(
                    selected = uiState.selectedTemplate?.id == template.id,
                    onClick = { onEvent(ResultUiEvent.OnTemplateSelected(template)) },
                    label = { Text(template.title) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ヘッダーとアクションボタン
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.result_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row {
                IconButton(onClick = { onEvent(ResultUiEvent.OnCopy) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                }
                IconButton(onClick = { onEvent(ResultUiEvent.OnPin) }) {
                    Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.action_pin))
                }
                IconButton(onClick = { onEvent(ResultUiEvent.OnSettings) }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Gemini の出力エリア
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .padding(8.dp),
        ) {
            when (uiState.status) {
                ResultUiState.Status.Loading -> {
                    // 生成開始直後のローディング表示
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.status_loading))
                    }
                }
                ResultUiState.Status.Streaming, ResultUiState.Status.Success -> {
                    // ストリーミング中または生成完了後の表示
                    Column {
                        if (uiState.status == ResultUiState.Status.Streaming) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                        }
                        // Markdown 形式で回答を表示。
                        // mergeDescendants=true で TalkBack が出力全体を 1 ブロックとして読み上げる。
                        Markdown(
                            content = uiState.outputText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {},
                        )
                    }
                }
                ResultUiState.Status.Error -> {
                    // エラー発生時の表示（再生成ボタン付き）
                    ErrorContent(uiState, onEvent)
                }
                ResultUiState.Status.Preparing -> {
                    // 初期化中（何も表示しない）
                }
            }
        }
    }
}

/**
 * エラー状態に応じたメッセージと再試行ボタンを表示する。
 */
@Composable
fun ErrorContent(
    uiState: ResultUiState,
    onEvent: (ResultUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val errorMessage = when {
            uiState.isProcessDeath -> stringResource(R.string.error_process_death)
            uiState.error is GeminiError.Auth -> stringResource(R.string.error_auth)
            uiState.error is GeminiError.Network -> stringResource(R.string.error_network)
            uiState.error is GeminiError.RateLimited -> stringResource(R.string.error_rate_limited)
            else -> stringResource(R.string.error_unknown)
        }

        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Process Death からの復帰時、または通常のエラー時に再生成を促す
        if (uiState.isProcessDeath || uiState.status == ResultUiState.Status.Error) {
            Button(onClick = { onEvent(ResultUiEvent.OnRetry) }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_regenerate))
            }
        }
    }
}
