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

- Android Studio プロジェクト新規作成、`io.github.ikinocore.gemread.android`
- Gradle Version Catalog（`gradle/libs.versions.toml`）整備
- 依存関係: Compose BOM, Material3, Hilt, Room, DataStore, Security-Crypto, google-genai, Coil（画像表示）, Coroutines
- KSP 設定（Room/Hilt用）
- `LICENSE`（Apache-2.0）, `README.md`（日英）, `.editorconfig`, `.gitignore`, ktlint or spotless 導入
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
  - 失敗時の例外を `GeminiError`（NetworkError / AuthError / RateLimited / Unknown）にマップ
- `GenerationRepository`: ViewModel と Client の橋渡し、履歴への自動保存
- `ImageDownscaler`: 長辺 1568px に縮小、JPEG quality 85 で変換するユーティリティ

### Step 5. 結果表示画面（Bottom Sheet型 Activity） (_Step 4 後_)

- `ResultActivity`: テーマに `Theme.AppCompat.Translucent` ベースのカスタム Dialog テーマ、画面下半分に Compose UI を配置
- 状態: Idle / Loading / Streaming(text) / Success / Error
- UI 要素:
  - 入力プレビュー（テキスト or サムネイル画像）
  - 出力エリア（Markdown レンダリング: `compose-markdown` 等の軽量ライブラリ）
  - 上部にテンプレート選択チップ
  - 下部に「コピー」「再生成」「設定を開く」「ピン留め（履歴で目立たせる）」
- ストリーミング時はチャンク到着ごとに `StateFlow` 更新
- 背景タップで dismiss、戻るボタンで dismiss

### Step 6. 共有/インテント受信エントリ (_Step 5 後_)

- `ShareReceiverActivity`（透明 Activity）を AndroidManifest に登録:
  - `ACTION_SEND` text/plain
  - `ACTION_SEND` image/\* (`image/png`, `image/jpeg`, `image/webp`)
  - `ACTION_SEND_MULTIPLE` image/\* （Phase 1 では先頭1枚のみ処理、UIで通知）
  - `ACTION_PROCESS_TEXT`（テキスト選択メニュー「翻訳(Gem Read)」、`processable=true`）
- 受信したペイロードを `Intent extras` で `ResultActivity` に転送
- 画像URIは `FileProvider`不要だが、`takePersistableUriPermission` で読み取り権限確保

### Step 7. 履歴機能 (_Step 4 後_)

- Roomエンティティ `HistoryEntry(id, type=TEXT|IMAGE, inputText, imagePath, modelName, templateId, output, createdAt, pinned)`
- 画像は内部ストレージ（`filesDir/history/{id}.jpg`）にダウンスケール後保存
- `HistoryActivity`: 一覧（日付グルーピング、検索バー、ピン留めフィルタ）、削除、再実行（結果画面で再生成）
- 設定: 自動削除（30日 / 90日 / 無期限）と最大件数

### Step 8. ランチャー画面とナビゲーション (_Step 2,3,7 後_)

- `MainActivity`: 起動時のハブ（手動入力でテキスト送信、履歴を開く、テンプレ管理、設定）
- 通常起動時とインテント起動時で挙動を分岐

### Step 9. テストとCI (_他Step と並行可_)

- ユニットテスト: ViewModel（MockK + Turbine）, Repository, ImageDownscaler, SecurePreferences
- GitHub Actions:
  - `pull_request` / `push` で `./gradlew lintDebug testDebug assembleDebug`
  - `tag v*` で `assembleRelease` を走らせ、未署名 APK を Releases に自動添付（署名はローカル鍵で別途、CIでは未署名でOK）
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
2. `./gradlew lintDebug` 警告ゼロ（または許容リスト）
3. Chrome で記事のテキストを共有 → 結果画面に翻訳がストリーミング表示されること
4. ギャラリーから画像を共有 → リサイズ後に画像認識結果が表示されること
5. テキスト選択 → メニューに「翻訳(Gem Read)」が現れること
6. APIキー未設定で送信 → 設定画面に誘導されること
7. APIキー誤入力 → 401相当のエラーが UI 上に明示されること
8. 履歴一覧から再生成→新規エントリが追加されること
9. APIキーが暗号化ストレージに保存されていること（adb で平文露出しない）
10. Phase 2: 録画許可ダイアログが連続キャプチャで毎回出ないこと
11. GitHub Actions: PR で test/lint が走り、tag push で APK が Releases に添付されること

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

## さらなる検討事項

1. **コード署名**: Releases に添付する APK は未署名でよいか、それとも GitHub Secrets に keystore を入れて署名済み APK を配るか。推奨: 当面は debug-keystore で署名した APK を配布（OSS で再配布する以上 sideload 利便性を優先）。
2. **Markdown レンダラ選定**: `dev.jeziellago:compose-markdown` / `com.mikepenz:multiplatform-markdown-renderer` / Compose 純正自前パーサ。推奨: `multiplatform-markdown-renderer`（活発・コードブロック対応）。
3. **クラッシュ収集**: 当面は無し / Firebase Crashlytics / Sentry。OSS 公開を考えると、推奨は当面なしまたはオプトインの Sentry。
