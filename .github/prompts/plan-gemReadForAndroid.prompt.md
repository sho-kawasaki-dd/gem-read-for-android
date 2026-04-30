# Plan: Gem Read for Android — 詳細実装計画

PC版「Gem Read」のAndroid版として、ブラウザのテキスト/画像をGemini APIで翻訳・解説するユーティリティを構築する。Phase 1（OS標準インテント連携 + 履歴 + 複数プロンプトテンプレート）で実用最小製品を確立し、Phase 2（MediaProjectionによる矩形キャプチャ）でUXを跳ね上げる。Kotlin + Jetpack Compose + MVVM、google-genai 公式 SDK、暗号化ストレージ、GitHub Actions CI を採用。

## 全体方針

- **言語/フレームワーク**: Kotlin / Jetpack Compose / Material 3
- **アーキテクチャ**: MVVM + クリーンアーキテクチャ簡易版（`ui` / `domain` / `data` の3層）
- **DI**: Hilt
- **非同期**: Coroutines + StateFlow
- **API**: `com.google.genai:google-genai`（公式 Android SDK、ストリーミング対応）
- **DB**: Room（履歴 + プロンプトテンプレート）
- **ストレージ**: AndroidX `EncryptedSharedPreferences` でAPIキー保存、設定値はDataStore（Preferences）
- **minSdk 29 / targetSdk 35 / compileSdk 35**
- **package**: `io.github.ikinocore.gemread.android`
- **license**: Apache-2.0
- **多言語**: ja（デフォルト）+ en
- **デフォルトモデル**: `gemini-2.5-flash`

---

## Phase 1: OS標準連携 MVP

### Step 1. プロジェクト基盤セットアップ

- Android Studio プロジェクト新規作成、applicationId と namespace を `io.github.ikinocore.gemread.android` に揃える（PC 版との衝突回避）
- Gradle Version Catalog（`gradle/libs.versions.toml`）整備
- 依存関係: Compose BOM, Material3, Hilt, Room, DataStore, Security-Crypto, google-genai, Coil（入力プレビュー / 履歴サムネイル用、Markdown 内画像レンダリングには兼用しない）, Coroutines
- KSP 設定（Room/Hilt用）
- `LICENSE`（Apache-2.0）, `NOTICE`, `README.md`（日英）, `.editorconfig`, `.gitignore`, ktlint or spotless 導入
- リソース: ja を一次、`values-en/strings.xml` は空スケルトンで将来の en 差分に備える
- パッケージ構造:
  - `ui/` — Compose 画面、Theme
  - `domain/` — UseCase, モデル
  - `data/` — Repository、Room DAO、APIクライアントラッパ、DataStore
  - `di/` — Hilt モジュール

### Step 2. 設定画面とAPIキー管理 (_Step 1 後_)

- `SettingsActivity` (Compose) を実装
  - APIキー入力（マスク表示 + 表示トグル + 接続テストボタン）
  - 使用モデル選択（flash / pro / flash-lite のドロップダウン）
  - ベースシステムプロンプト編集
  - 画像リサイズON/OFF（既定ON、長辺1568px）
  - ストリーミングON/OFF（既定ON）
- `SecurePreferences`: `EncryptedSharedPreferences`（`MasterKey` AES256-GCM）でAPIキー保存
- `AppPreferences`: DataStore Preferences で他の設定保存
- 起動時にAPIキー未設定なら設定画面へ誘導するチェックフロー

### Step 3. プロンプトテンプレート機能 (_Step 1 後、Step 2 と並行可_)

- Roomエンティティ `PromptTemplate(id, title, systemPrompt, isDefault, sortOrder, createdAt)`
- 初期投入: 「翻訳」「要約」「解説」「文法説明」の4種を初回起動時にシード
- テンプレート管理画面（一覧 / 追加 / 編集 / 削除 / デフォルト設定 / 並び替え）
- 結果画面のヘッダーでテンプレ切替＝即時再リクエスト

### Step 4. Geminiクライアント層 (_Step 1 後_)

- `GeminiClient`: google-genai SDK をラップ
  - `generateText(prompt, systemPrompt, model): Flow<GenerationChunk>`
  - `generateFromImage(bitmap, prompt, systemPrompt, model): Flow<GenerationChunk>`
  - 失敗時の例外を `GeminiError`（NetworkError / AuthError / RateLimited / Unknown）にマップし、自動再試行は入れず UI の「再生成」でのみ再実行させる
  - 接続テストは現在のモデルに対して短い固定テキスト（例: `"ping"`）を `generateContent` で 1 リクエスト投げて 200 を確認する方式に固定し、課金・レート影響を最小にする
- `GenerationRepository`: ViewModel と Client の橋渡し、Gemini 応答完了後に 1 回だけ履歴へ確定保存
- `ImageDownscaler`: リサイズ ON で長辺 1568px / JPEG quality 85 に縮小。リサイズ OFF でも (a) 内部キャッシュへのコピー、(b) JPEG への正規化、(c) フェイルセーフとして長辺 4096px / 4MB を上限とする再縮小、までは必ず実施する（Gemini への過大送信防止）
  - 入力 `Uri` → `ContentResolver` で開き、`Dispatchers.IO` 上でデコード・縮小・JPEG エンコード
  - 出力先は `filesDir/cache/{uuid}.jpg`（履歴採用時はそのまま `filesDir/history/{id}.jpg` へ昇格）
  - **注意**: ShareReceiver から渡される `Uri` は grant URI のため、`takePersistableUriPermission` は試みず、ViewModel 到達後すみやかに内部ストレージへコピーしてから処理する（権限失効耐性）

### Step 5. 結果表示画面（Bottom Sheet型 Activity） (_Step 4 後_)

- `ResultActivity`: `Theme.Material3.Translucent` 系テーマ + Compose `ModalBottomSheet` で Bottom Sheet 型として実装し、背景タップとスワイプで閉じる、IME 出現時は内容をスクロール対応にする
- 状態: Idle / PreparingImage / Loading / Streaming(text) / Success / Error
- UI 要素:
  - 入力プレビュー（テキスト or サムネイル画像）
  - 出力エリア（Markdown レンダリング: `com.mikepenz:multiplatform-markdown-renderer-m3`）
  - 上部にテンプレート選択チップ
  - 下部に「コピー」「再生成」「設定を開く」「ピン留め（履歴で目立たせる）」
- ダークテーマ追従と動的カラーに対応、結果テキストは TalkBack で 1 ブロックとして読み上げ可能にする
- ストリーミング時はチャンク到着ごとに `StateFlow` 更新（UI 側は `collectAsStateWithLifecycle()` で購読）
- 背景タップで dismiss、戻るボタンで dismiss
- **画面回転対応**:
  - Gemini ストリームは `ResultViewModel` の `viewModelScope` で保持し、Activity 再生成されても通信が切れない設計を必須とする
  - `configChanges` は付けず、Activity 再作成 + ViewModel 復元で成立させる（明確な不具合が検出されたときにのみ付与を再評価する）
- **入力データの受け取り**:
  - `Intent extras` で受け取るのは `Uri` 文字列 / `mimeType` / text 参照（短文は extras、長文は cache file path） / `templateId` / 起動元種別 のみ（Bitmap や生バイトは載せない）
  - 画像のデコード・ダウンスケールは ViewModel の `Dispatchers.IO` コルーチン内で実行し、進行中は `PreparingImage` 状態で UI に表示
  - SavedStateHandle により Uri / text path / templateId / source / 直近の partial output 長を復元し、process death 復帰時は自動再実行せず `Error(reason=ProcessDeath)` 状態で UI に「再生成」を促す

### Step 6. 共有/インテント受信エントリ (_Step 5 後_)

- `ShareReceiverActivity`（透明 Activity）を AndroidManifest に登録:
  - `ACTION_SEND` text/plain
  - `ACTION_SEND` image/\* (`image/png`, `image/jpeg`, `image/webp`)
  - `ACTION_SEND_MULTIPLE` image/\* （Phase 1 では先頭1枚のみ処理、UIで通知）
  - `ACTION_PROCESS_TEXT`（テキスト選択メニュー「翻訳(Gem Read)」、`processable=true`、読み取り専用で結果は送り返さない）
- **データ引き回しのルール（TransactionTooLargeException 回避）**:
  - 画像は `Uri` 文字列のみを `Intent extras` で `ResultActivity` に転送し、Bitmap・ByteArray は絶対に載せない
  - テキストは 50,000 文字（または 200KB）を閾値にそれ以下は extras、超過時はアプリ内 cache file へ書き出して path のみ受け渡す
  - `ResultActivity` 起動 Intent には `Intent.FLAG_GRANT_READ_URI_PERMISSION` を再付与する
  - API キー未設定時は MainActivity 経由の onboarding バナーへ遷移させる（設定画面へ直接跳ばせない）
- 共有元由来の grant URI は `takePersistableUriPermission` が効かない場合があるため、`ResultViewModel` 到達直後にバックグラウンドで内部ストレージへコピー → 以降はそのコピーを参照

### Step 7. 履歴機能 (_Step 4 後_)

- Roomエンティティ `HistoryEntry(id, type=TEXT|IMAGE, inputText, imagePath, modelName, templateId, output, createdAt, pinned)`
- 画像は内部ストレージ（`filesDir/history/{id}.jpg`）にダウンスケール後保存
- `HistoryActivity`: 一覧（日付グルーピング、検索バー、ピン留めフィルタ）、削除、再実行（結果画面で再生成）
- pruning デフォルトは「最大 200 件 / 90 日」（pinned は対象外）、設定画面から件数・日数を変更可能

### Step 8. ランチャー画面とナビゲーション (_Step 2,3,7 後_)

- `MainActivity`: 起動時のハブ（手動入力でテキスト送信、履歴を開く、テンプレ管理、設定）
- 通常起動時とインテント起動時で挙動を分岐

### Step 9. テストとCI (_他Step と並行可_)

- ユニットテスト: ViewModel（MockK + Turbine）, Repository, ImageDownscaler, SecurePreferences
- GitHub Actions:
  - `pull_request` / `push` で `./gradlew lintDebug testDebug assembleDebug`（lintDebug は警告ゼロ、例外は baseline で明示）
  - `tag v*` で `assembleRelease` + `bundleRelease` を実行し、**署名済み** APK と AAB を Releases に自動添付
  - Release ビルドは `minifyEnabled true` / `shrinkResources true` を有効化し、`proguard-rules.pro` に google-genai（リフレクション DTO）、Hilt 生成クラス、Room の `@Entity` / `@Dao`、Markdown renderer の AST ノードに対する keep ルールを置く
  - `networkSecurityConfig` で cleartext を全面拒否
  - 署名フロー:
    - GitHub Secrets: `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
    - workflow で `${{ runner.temp }}/release.keystore` に Base64 デコードして配置、ジョブ終了時に削除
    - `app/build.gradle.kts` の `signingConfigs.release` は環境変数 / `local.properties` フォールバック構成にして CI・ローカル両対応
    - パスワード等が Gradle ログに出ないよう `--no-daemon` + Secrets マスキングを徹底
  - キャッシュ（gradle, AVD）

---

## Phase 2: オーバーレイ＆セッション型キャプチャ

### Step 10. 権限導線 (_Phase 1 完了後_)

- 起動時オンボーディングで「他のアプリの上に表示」を案内（`Settings.canDrawOverlays`）
- `MediaProjection` 取得→ `ForegroundService` 起動の手順を案内
- `POST_NOTIFICATIONS`（Android 13+）の取得

### Step 11. フローティングトリガー (_Step 10 後_)

- `OverlayService` (Foreground Service, type `mediaProjection|specialUse`)
- `WindowManager` で円形ボタンを表示、ドラッグ移動 + 端への自動スナップ
- 通知: 「Gem Read 起動中」 + 停止アクション
- ボタンタップ→キャプチャモード起動

### Step 12. MediaProjection セッション管理 (_Step 11 後_)

- `MediaProjectionManager.createScreenCaptureIntent()` で初回セッション開始（透明 Activity 経由）
- 取得した `MediaProjection` を Service が保持し、セッション継続
- `VirtualDisplay` + `ImageReader` で都度キャプチャ→Bitmap化
- セッションがOSにキルされた場合の再認証フロー（自動再要求 + Snackbar 通知）
- `onConfigurationChanged` で画面回転対応（VirtualDisplay 再作成）

### Step 13. 矩形選択UI (_Step 12 後_)

- 全画面の半透明オーバーレイ（`WindowManager` 経由の Compose）
- ドラッグで矩形選択、確定ボタン/キャンセルボタン
- 前回の矩形を `runtime` で記憶（プロセス生存期間中、永続化はしない）
- 確定後、キャプチャ画像をクロップ→`ResultActivity` 起動

### Step 14. Phase 2 統合テスト (_Step 13 後_)

- 実機 Android 10/12/14/15 で動作確認
- 録画許可ダイアログがセッション中1回で済むことを検証

---

## 相対するファイル/モジュール

- `app/build.gradle.kts` — 依存追加・Compose・KSP・signingConfig
- `gradle/libs.versions.toml` — バージョン集中管理
- `app/src/main/AndroidManifest.xml` — Activity / Service / Intent filter / 権限
- `app/src/main/java/.../data/api/GeminiClient.kt`
- `app/src/main/java/.../data/security/SecurePreferences.kt`
- `app/src/main/java/.../data/db/AppDatabase.kt` + DAO 群
- `app/src/main/java/.../ui/result/ResultActivity.kt` + ViewModel
- `app/src/main/java/.../ui/share/ShareReceiverActivity.kt`
- `app/src/main/java/.../ui/settings/SettingsScreen.kt`
- `app/src/main/java/.../ui/template/TemplateScreen.kt`
- `app/src/main/java/.../ui/history/HistoryScreen.kt`
- `app/src/main/java/.../service/OverlayService.kt`（Phase 2）
- `app/src/main/java/.../capture/ScreenCaptureManager.kt`（Phase 2）
- `.github/workflows/android.yml`

## 検証

1. `./gradlew testDebug` がグリーン
2. `./gradlew lintDebug` 警告ゼロ（または baseline 許容のみ）
3. Chrome で記事のテキストを共有 → 結果画面に翻訳がストリーミング表示されること
4. ギャラリーから画像を共有 → リサイズ後に画像認識結果が表示されること
5. テキスト選択 → メニューに「翻訳(Gem Read)」が現れること
6. APIキー未設定で送信 → MainActivity 経由の onboarding バナーから設定画面へ誘導されること
7. APIキー誤入力 → 401相当のエラーが GeminiError(Auth) として UI 上に明示されること
8. 履歴一覧から再生成→新規エントリが追加されること
9. APIキーが暗号化ストレージに保存されていること（adb で平文露出しない）
10. ストリーミング中に画面を回転させてもリクエストが継続し、UI state が復元されること。process death 後は `Error(ProcessDeath)` 状態に落ち、手動再生成導線が出ること
11. テンプレートが 1 件のときは削除不可、default 削除時は sortOrder 最小の他テンプレが自動で default に昇格されること
12. 画像リサイズ OFF でも Uri コピー・JPEG 正規化・4096px / 4MB フェイルセーフ上限が適用されること
13. Phase 2: 録画許可ダイアログが連続キャプチャで毎回出ないこと
14. GitHub Actions: PR で test/lint が走り、tag push で 署名済み APK / AAB が Releases に自動添付されること

## 決定事項

- Scope: Phase 1 と Phase 2 両方を計画書に含める
- minSdk 29 / target 35
- API クライアント: google-genai 公式 SDK
- APIキー保存: EncryptedSharedPreferences
- UI: Bottom Sheet 型 Activity
- プロンプトテンプレート: Phase 1 から複数対応（4種シード）
- 画像: 長辺 1568px 自動ダウンスケール
- 履歴: Room DB
- ストリーミング: 有効
- CI: GitHub Actions で APK 自動添付
- パッケージ: `io.github.ikinocore.gemread.android`
- デフォルトモデル: `gemini-2.5-flash`
- 言語: ja + en
- ライセンス: Apache-2.0
- Phase 2 矩形選択範囲: セッション中のみ記憶
- テスト: ViewModel/Repository ユニットテストのみ
- **コード署名**: GitHub Secrets に Base64 エンコードした Release キーストアを保管し、CI で復元して署名済み APK / AAB を Releases に添付
- **Markdown レンダラ**: `com.mikepenz:multiplatform-markdown-renderer-m3` を採用
- **Coil の用途**: 入力プレビュー / 履歴サムネイルのみ。Markdown 内画像レンダリングには兼用しない
- **format/lint**: ktlint または spotless のいずれかを 1 つ選ぶ。lintDebug は警告ゼロを既定とし baseline で例外を明示
- **クラッシュ収集**: Phase 1/2 では導入しない（将来 Sentry オプトインを検討）
- **Intent 引き回し**: 画像は `Uri` 文字列のみ転送し、ダウンスケールは `ResultViewModel` のコルーチン内でバックグラウンド実行（`TransactionTooLargeException` 回避）。`Intent.FLAG_GRANT_READ_URI_PERMISSION` を必ず再付与
- **長文 text 閾値**: 50,000 文字（または 200KB）を超えたらアプリ内 cache file へ退避し path だけ渡す
- **画面回転**: `ResultActivity` には `configChanges` を付けず、Gemini ストリームを `viewModelScope` で保持し ViewModel 復元のみで成立させる
- **process death 復帰**: 自動再実行せず、`Error(reason=ProcessDeath)` 状態で UI に「再生成」ボタンを提示する
- **画像リサイズ OFF 時のフェイルセーフ**: Uri コピー・JPEG 正規化・長辺 4096px / 4MB 上限の再縮小は常に実施
- **テンプレート削除フォールバック**: 件数が 1 のときは不可、default 削除時は sortOrder 最小の他テンプレを default に自動昇格
- **接続テスト**: 現在のモデルに対する短い `generateContent`（固定文字列）1 リクエストで判定
- **共有起動での API キー未設定**: MainActivity 経由の onboarding に統一し、ShareReceiver から設定画面へ直接遷移させない
- **履歴保持のデフォルト**: 200 件 / 90 日（pinned は対象外）、設定画面から変更可能
- **Release ビルド**: R8（`minifyEnabled` / `shrinkResources` 有効） + keep ルール、`networkSecurityConfig` で cleartext 拒否
- **applicationId / namespace**: いずれも `io.github.ikinocore.gemread.android` に揃える

## 既知のリスクと方針

- **プロセスキル耐性**: OS によるバックグラウンドキルは防げない。`viewModelScope` 保持だけではプロセスごと消えるため、SavedStateHandle で入力情報だけを復元し、復帰時は自動再リクエストせず `Error(ProcessDeath)` 状態で手動再生成導線を出す。永続的なジョブ復元が必要になれば将来 `WorkManager` を検討。
- **`takePersistableUriPermission` 非対応 URI**: ACTION_SEND の grant URI はそのまま永続化できないため、永続化を試みず ViewModel 到達直後に内部ストレージへコピーする方針を全画像経路で徹底する。
