---
mode: agent
description: Gem Read for Android Phase 1（OS 標準インテント → Gemini MVP）の実装計画。スコープ固定・並列ワークストリーム・受け入れ基準を含む。
---

# Plan: Gem Read Android Phase 1

Phase 1 は「OS標準インテントから Gemini へつなぐ MVP」を最短で成立させる。推奨の進め方は、まず Android プロジェクト基盤と共通インフラを作り、その後に 4 本の並行ワークストリーム（設定、テンプレート、Gemini 呼び出し、履歴）を進め、最後に結果表示・インテント受信・ランチャーで統合する。ユーザー決定として、初期テンプレートは JSON/asset seed、複数画像共有は先頭 1 枚のみ処理、回転時も処理継続、自動再試行なし、CI Release 署名を Phase 1 に含める方針で固定する（詳細は末尾の Decisions に集約）。

## Steps

1. Phase 1 仕様を固定する。現行ドキュメントの Phase 1 から実装対象を限定し、Phase 2 の MediaProjection / Overlay / ForegroundService は除外する。下部 Decisions に列挙された各決定（テンプレ seed、複数画像、回転継続、再試行なし、CI 署名、リサイズ OFF 時挙動、テンプレ削除フォールバック、共有時 API キー未設定の導線、process death 復帰、R8、ライセンス、保持件数）を本ステップで一括確定し、以後 Steps 内で再議論しない。
2. Android プロジェクトを新規生成する。Kotlin + Compose + Material 3 + minSdk 29 / targetSdk 35 / compileSdk 35 で app モジュールを作成し、applicationId と namespace は `io.github.ikinocore.gemread.android` に揃える（PC 版との衝突回避）。ライセンス（Apache-2.0、`LICENSE` と `NOTICE` を配置）、README、.editorconfig、Android 向け .gitignore、Version Catalog、基本リソース構成（ja を一次、en は空スケルトンで `values-en/strings.xml` を用意して将来差分を入れやすくする、themes、icons）をこの段階で作る。
3. ビルド基盤を整える。AGP、Kotlin、Compose BOM、Hilt、Room、DataStore、Security Crypto、google-genai、Coil（入力プレビュー / 履歴サムネイル用、Markdown 内画像レンダリングには兼用しない）、Markdown renderer は `com.mikepenz:multiplatform-markdown-renderer-m3` を採用、Coroutines、test ライブラリを Version Catalog に集約し、KSP、Hilt plugin、Room schema 出力を有効化する。format/lint は ktlint または spotless のいずれかを導入し（どちらかに固定）、`app/build.gradle.kts` と `settings.gradle.kts` に反映する。これは以後すべての手順の前提になる。
4. アプリ全体の層構造と DI を用意する。`ui`、`domain`、`data`、`di` を作り、Application クラス、Hilt エントリ、共通 Dispatcher 提供、Repository / DataSource の interface 配置、画面共通の UiState / Event の置き場を決める。ここで「どこが UI 状態を所有するか」を明文化して以後の ViewModel 実装の軸にする。_depends on 2,3_
5. 設定・セキュリティ基盤を実装する。EncryptedSharedPreferences + MasterKey（AES256-GCM）で API キーを保存する SecurePreferences、DataStore Preferences でモデル名、ベースシステムプロンプト、画像リサイズ、ストリーミング有無、自動削除設定などを保存する AppPreferences を作る。モデル名の初期値は `gemini-2.5-flash` とし、設定画面のドロップダウン候補は `gemini-2.5-flash` / `gemini-2.5-pro` / `gemini-2.5-flash-lite` の 3 種に固定する。設定未完了かどうかを判定する use case を用意し、起動時と共有受信時の両方で使えるようにする。_depends on 4_
6. Prompt Template 基盤を実装する。Room に PromptTemplate entity / DAO / repository を追加し、初回起動時に assets 配下 JSON から 4 件を seed する。JSON は title、systemPrompt、sortOrder、default フラグを持つ形にして、将来の多言語差し替えにも耐える構造にする。デフォルト変更時の一意性制約、並び替え永続化、削除時のフォールバックは「件数が 1 件のときは削除不可（UI で削除アクション無効化）。デフォルト指定中のテンプレを削除する場合は、削除前に sortOrder 最小の他テンプレを default に昇格させる」を確定仕様として実装する。_parallel with 5,7,8 after 4_
7. Gemini API 層を実装する。google-genai SDK をラップする GeminiClient を作り、text / image の 2 経路を Flow ベースで統一する。SDK 例外を Auth / Network / RateLimited / Unknown に正規化し、自動再試行は入れず、UI の「再生成」でのみ再実行できるようにする。モデル選択は preferences から読み出し、接続テストもこの層を経由させる。接続テストの実体は「現在のモデルに対して短い固定テキスト（例: "ping"）を `generateContent` で 1 リクエスト投げ、200 を確認する」方式に固定し、課金・レート影響を最小に抑える。_parallel with 5,6,8 after 4_
8. 画像入力パイプラインを実装する。grant 付き Uri を受信したら即座に内部キャッシュ `filesDir/cache/{uuid}.{ext}` へコピーし、その後に downscale と必要に応じた再エンコードを行う ImageDownscaler を用意する。`ContentResolver` で開いて `Dispatchers.IO` 上でデコード・縮小・エンコードし、リサイズ ON のとき長辺 1568px・JPEG quality 85、リサイズ OFF のときは元画像が WebP/JPEG/PNG かつフェイルセーフ（長辺 4096px / 出力 4MB）以下なら再エンコードせず内部コピーをそのまま送信し、条件を超える場合または非対応形式の場合のみ再縮小や JPEG 化を行う（Gemini への過大送信防止）。履歴へ保存する場合のみ `filesDir/history/{id}.{ext}` へ昇格し、再エンコード時は `.jpg`、パススルー時は元形式に応じた拡張子を保持する。未保存の temp ファイルはアプリ起動時の sweep でクリーンアップする。共有元の grant Uri は `takePersistableUriPermission` が効かないケースがあるため、永続化は試みず内部コピー一択で運用する。_parallel with 5,6,7 after 4_
9. 履歴基盤を実装する。HistoryEntry entity / DAO / repository を追加し、TEXT / IMAGE、入力、出力、modelName、templateId、createdAt、pinned、imagePath を保持する。保持件数・保持日数の pruning ロジックは repository or maintenance use case に切り出し、起動時や履歴更新時に発火できるようにする。デフォルトは「最大 200 件 / 90 日」とし、設定画面から変更可能にする（pinned は対象外）。履歴保存は GenerationRepository の責務に寄せ、Gemini 応答完了後に 1 回だけ確定保存する。entity 層は Step 4 にのみ依存、generation との結線は Step 10 で行う。_depends on 4; integrates in 10_
10. Result 画面の状態モデルと ViewModel を実装する。Intent から受け取るのは Uri 文字列 / mimeType / text 参照（短文は extras、長文は cache file path）/ templateId / source の軽量データに限定し、ViewModel 側で PreparingImage、Loading、Streaming、Success、Error を管理する。Gemini ストリームは `viewModelScope` で保持し、回転後も同一リクエストが継続する設計にする。SavedStateHandle で入力情報（Uri / text path / templateId / source / 直近の partial output 長）を復元する。process death から復帰した場合は自動再実行せず、`Error(reason=ProcessDeath)` 状態で UI に「再生成」ボタンを促す（ストリームの途中状態は破棄）。再生成時は現在の template と model を使って同じ generation pipeline を再実行する。ModalBottomSheet が dismiss された際は確実に Activity.finish() を呼んで ViewModel を破棄させる。_depends on 5,6,7,8_
11. ResultActivity の UI を実装する。`Theme.Material3.Translucent` 系テーマ + Compose `ModalBottomSheet` で Bottom Sheet 型として実装し、背景タップとスワイプで閉じる、IME 出現時は内容をスクロール対応にする。入力プレビュー、テンプレート切替チップ、Markdown 出力、コピー、再生成、設定導線、ピン留めを配置する。ダークテーマ追従と動的カラーに対応し、結果テキストは TalkBack で 1 ブロックとして読み上げ可能にする。複数画像共有で入ってきた場合は「先頭 1 枚のみ処理しました。複数枚同時解析は今後対応予定です。」の通知を上部バナーか Snackbar で表示する。回転は Activity 再作成 + ViewModel 復元で成立させ、`configChanges` は付けない（必要性は Step 19 の検証結果で再評価し、明確な不具合がない限り付与しない）。_depends on 10_
12. 共有受信エントリを実装する。ShareReceiverActivity を透明 Activity として追加し、`ACTION_SEND text/plain`、`ACTION_SEND image/*`（受け入れ MIME は `image/png` / `image/jpeg` / `image/webp` を一次対象）、`ACTION_SEND_MULTIPLE image/*`、`ACTION_PROCESS_TEXT`（intent-filter には標準の action と `text/plain` data のみを設定し、Activity 側で `setResult()` を呼ばないことで元のテキストを置換しない読み取り専用として振る舞う）を受けて、入力正規化後に ResultActivity を起動する。ResultActivity 起動 Intent には Uri 文字列のみを extras で載せ、`Intent.FLAG_GRANT_READ_URI_PERMISSION` を再付与する（Bitmap や生バイトは絶対に載せない）。text は 50,000 文字（または 200KB）を閾値にそれ以下は extras、超過時はアプリ内 cache file へ書き出して path のみ受け渡す。ShareReceiverActivity の onCreate 内での Intent 展開時は、巨大なテキストによる TransactionTooLargeException を警戒し、try-catch でラップして安全にエラー画面（MainActivity の Snackbar 等）へフォールバックさせる。API キー未設定時は **MainActivity を経由させ、起動時の onboarding バナーから設定画面へ誘導する**（共有経由でいきなり設定画面に飛ばす経路は採らない）。_depends on 5,10,11_
13. 設定画面を実装する。API キー入力、表示トグル、接続テスト、モデル選択、ベースシステムプロンプト、画像リサイズ ON/OFF、ストリーミング ON/OFF、履歴保持件数、履歴保持日数を Compose で実装し、保存先は 5 の preferences に統一する。接続テストは 7 の GeminiClient を呼び、成功・認証失敗・通信失敗を UI に明確に返す。_depends on 5,7; parallel with 10-12 after 5,7_
14. テンプレート管理画面を実装する。一覧、追加、編集、削除、デフォルト設定、並び替えを Compose + Room で実装し、Result 画面からのテンプレ切替と同一 repository を使う。asset seed は初期投入専用とし、その後の編集内容は DB のみを真実とする。_depends on 6; parallel with 13_
15. 履歴画面を実装する。日付グルーピング一覧、検索、ピン留めフィルタ、削除、再実行を実装し、再実行時は元の入力情報を ResultActivity に渡して新規 generation を走らせる。画像履歴は保存済み内部パスのみ参照し、共有元 Uri に依存しない。_depends on 9; parallel with 13,14_
16. MainActivity とナビゲーションを実装する。通常起動時のハブとして、手動テキスト入力、履歴、テンプレ管理、設定への導線を置く。共有起動と通常起動で分岐し、共有経路は ShareReceiverActivity、通常起動は MainActivity を起点にする。初回起動時は API キー未設定なら設定を先に案内する。_depends on 13,14,15_
17. テストを実装する。ViewModel は Turbine + MockK、Repository と preference 層は JVM テスト、Room は Robolectric or instrumented のどちらかに統一して最小セットで検証する。重点は API キー未設定判定、template seed、Uri コピー、画像 downscale、Gemini エラー変換、履歴保存、回転後の state 維持に置く。_parallel with 10-16 after respective dependencies_
18. CI/CD を固める。`.github/workflows/android.yml` で lintDebug、testDebug、assembleDebug を PR/push で実行する。lintDebug は警告ゼロを既定とし、許容する項目のみ `lint-baseline.xml` または `lintOptions` で明示する。Gradle / AVD のキャッシュを有効化してビルド時間を抑える。tag v\* では assembleRelease と bundleRelease を実行し、**生成された署名済み APK と AAB を GitHub Releases に自動添付する**。Release ビルドは `minifyEnabled true` / `shrinkResources true` を有効化し、`proguard-rules.pro` に google-genai（リフレクション利用される DTO 群）、Hilt 生成クラス、Room の `@Entity` / `@Dao`、Markdown renderer の AST ノードに対する keep ルールを置く。`networkSecurityConfig` で cleartext を全面拒否する。Release 署名は GitHub Secrets の KEYSTORE_BASE64、KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD を使い、workflow で `${{ runner.temp }}/release.keystore` に Base64 デコード配置・ジョブ終了時に削除、Gradle ログに認証情報を出さないため `--no-daemon` 実行と Secrets マスキングを徹底する。Gradle 側は env → `local.properties` → `gradle.properties` のフォールバックでローカル開発にも対応させる。Secrets 設定手順と keystore 作成手順を README に追記する。_depends on 2,3; can start early and finalize after build stabilizes_
19. 統合受け入れを行う。Chrome 共有テキスト、ギャラリー共有画像、テキスト選択 ACTION_PROCESS_TEXT、API キー未設定、認証失敗、履歴再実行、複数画像共有、回転中ストリーミング継続、CI 署名付き Release 生成を実機またはエミュレータで検証し、Phase 1 完了条件を満たすことを確認する。_depends on 11-18_

## Relevant files

- [.github/prompts/plan-gemReadForAndroid.prompt.md](.github/prompts/plan-gemReadForAndroid.prompt.md) — 現行のフェーズ定義。Phase 1 のスコープ固定と決定事項の反映元。
- [企画書\_ Gem Read for Android.md](%E4%BC%81%E7%94%BB%E6%9B%B8_%20Gem%20Read%20for%20Android.md) — 企画意図、Phase 1/2 境界、Bottom Sheet 推奨理由、オープン論点の根拠。
- [README.md](README.md) — セットアップ、Secrets、keystore、ローカル開発手順を追記する前提。
- `settings.gradle.kts` — モジュール構成、pluginManagement、dependencyResolutionManagement。
- `build.gradle.kts` — ルート build 設定、共通 plugin 管理。
- `gradle/libs.versions.toml` — 依存とバージョンの一元管理。
- `app/build.gradle.kts` — Android 設定、Compose、Hilt、Room、signingConfig、test 依存。
- `app/src/main/AndroidManifest.xml` — Activity、intent-filter、権限、Application 設定。
- `app/src/main/java/io/github/ikinocore/gemread/android/GemReadApp.kt` — Hilt Application。
- `app/src/main/java/io/github/ikinocore/gemread/android/di/AppModule.kt` — Dispatcher、database、preferences、repository 提供。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/prefs/SecurePreferences.kt` — API キー暗号化保存。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/prefs/AppPreferences.kt` — DataStore 設定保存。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/db/AppDatabase.kt` — Room DB。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/db/template/PromptTemplateEntity.kt` — テンプレート定義。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/db/history/HistoryEntryEntity.kt` — 履歴定義。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/api/GeminiClient.kt` — Gemini SDK ラッパ。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/image/ImageDownscaler.kt` — Uri コピー、縮小、JPEG 変換。
- `app/src/main/java/io/github/ikinocore/gemread/android/data/repo/GenerationRepository.kt` — generation と履歴保存の橋渡し。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/result/ResultViewModel.kt` — generation pipeline と state 管理。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/result/ResultActivity.kt` — Bottom Sheet 結果画面。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/share/ShareReceiverActivity.kt` — 標準インテント受信。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/settings/SettingsActivity.kt` — 設定画面。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/template/TemplateActivity.kt` — テンプレ管理画面。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/history/HistoryActivity.kt` — 履歴画面。
- `app/src/main/java/io/github/ikinocore/gemread/android/ui/main/MainActivity.kt` — 通常起動時ハブ。
- `app/src/main/assets/prompt_templates_ja.json` — 初期テンプレート seed。
- `app/src/main/res/values-en/strings.xml` — Phase 1 では空スケルトン、将来の en 差分投入用。
- `app/src/main/res/xml/network_security_config.xml` — cleartext 拒否設定。
- `app/proguard-rules.pro` — Release minify 用の keep ルール（google-genai / Hilt / Room / Markdown renderer）。
- `gradle.properties` — 署名情報のローカルフォールバック先。
- `LICENSE` / `NOTICE` — Apache-2.0 ライセンス本文と告知。
- `.github/workflows/android.yml` — CI/CD ワークフロー。

## Verification

1. Android プロジェクト生成直後に `./gradlew tasks` と `./gradlew :app:assembleDebug` が通ることを確認する。
2. preferences 層で API キー暗号保存と設定保存のユニットテストを通す。
3. template seed 実行後、初回起動時のみ 4 件投入され、再起動で重複しないことを確認する。
4. text / image の generation pipeline がそれぞれ Flow で最後まで完了し、例外が GeminiError に正規化されることをテストする。
5. grant Uri のコピー後に元 Uri アクセスが失効しても内部コピーで処理継続できることを確認する。
6. Result 画面でストリーミング中に画面回転してもリクエストが継続し、UI state が復元されることを確認する。
7. ACTION_SEND text/plain、ACTION_SEND image/\*、ACTION_PROCESS_TEXT が各アプリから起動できることを手動確認する。
8. ACTION_SEND_MULTIPLE image/\* で先頭 1 枚のみ処理され、将来拡張予定の通知が表示されることを確認する。
9. 履歴保存、検索、削除、再実行、pin、画像履歴参照が成立することを確認する。
10. `./gradlew lintDebug testDebug assembleDebug` を CI とローカルで通し（lintDebug は警告ゼロまたは baseline 許容のみ）、tag ビルドで署名済み APK/AAB が生成され、GitHub Releases に自動添付されることを確認する。
11. API キー誤入力時に 401 相当のエラーが GeminiError(Auth) として正規化され、UI 上に「認証に失敗しました」相当のメッセージが明示されることを確認する。
12. APIキーが暗号化ストレージに保存され、`adb` で取り出した SharedPreferences ファイルに平文で露出しないことを確認する。

## Decisions

- Included scope は Phase 1 のみ。Phase 2 の Overlay、MediaProjection、Foreground Service は除外する。
- デフォルトモデルは `gemini-2.5-flash`。設定画面の選択肢は `gemini-2.5-flash` / `gemini-2.5-pro` / `gemini-2.5-flash-lite`。
- Markdown レンダラは `com.mikepenz:multiplatform-markdown-renderer-m3` を採用。Coil は入力プレビュー / 履歴サムネイル用途のみ。
- format/lint ツールは ktlint または spotless を 1 つ選定して導入する。lintDebug は警告ゼロを既定とし、例外は baseline で明示。
- 受け入れ画像 MIME は `image/png` / `image/jpeg` / `image/webp`。`image/heic` / `image/gif` は Phase 1 では intent-filter から除外する。
- 画像キャッシュ命名は `filesDir/cache/{uuid}.{ext}`、履歴は `filesDir/history/{id}.{ext}` で固定する。再エンコード時は `.jpg`、パススルー時は元形式に応じた拡張子を保持する。
- 共有元の grant Uri は `takePersistableUriPermission` を試みず、ViewModel 到達直後に内部コピーする一択で運用する。
- ResultActivity 起動 Intent には Uri 文字列のみを載せ、`Intent.FLAG_GRANT_READ_URI_PERMISSION` を必ず再付与する。
- `ACTION_PROCESS_TEXT` はマニフェストの特別な属性を使わず、Activity 側で `setResult()` を返さない実装とすることで、元のアプリのテキストを置換しない読み取り専用として扱う。
- CI は Gradle / AVD キャッシュを有効化し、tag v\* で署名済み APK / AAB を GitHub Releases に自動添付する。署名工程は `--no-daemon` + Secrets マスキングで認証情報のログ漏洩を防ぐ。
- 初期テンプレートは assets JSON seed を採用し、将来の差し替えと多言語化余地を確保する。
- ACTION_SEND_MULTIPLE は Phase 1 では先頭 1 枚のみ処理する。複数画像を Gemini にまとめて渡す拡張は将来対応に回す。
- 回転時も Gemini 通信は継続する前提で ViewModel 中心に state を保持する。process death 復帰時は自動再実行せず、`Error(ProcessDeath)` 状態から手動再生成させる。
- Gemini エラー時の自動再試行は入れず、ユーザー操作による再生成のみ提供する。
- リサイズ ON の場合は長辺 1568px・JPEG quality 85 に縮小。リサイズ OFF かつ元画像が WebP/JPEG/PNG でフェイルセーフ（4096px/4MB）以下の場合は、再エンコードせず無劣化でコピー・送信する（パススルー）。条件超過時または非対応形式では、再縮小や必要に応じた JPEG 化を行う。
- テンプレート削除は「件数 1 のときは不可」「default 削除時は sortOrder 最小の他テンプレを default に自動昇格」で固定。
- API キー未設定時の共有起動は MainActivity 経由の onboarding に統一し、ShareReceiverActivity から設定画面へ直接遷移させない。
- 履歴保持のデフォルトは 200 件 / 90 日（pinned は対象外）。設定画面から変更可能。
- 接続テストは現在のモデルに対する短い `generateContent`（固定文字列）1 リクエストで判定する。
- ResultActivity は Compose `ModalBottomSheet` + Translucent テーマ。`configChanges` は付けず、ViewModel 復元のみで成立させる。
- 長文 text の Intent 受け渡しは 50,000 文字（または 200KB）を閾値に cache file へ退避する。
- ライセンスは Apache-2.0。`LICENSE` と `NOTICE` をリポジトリ直下に置く。
- Release ビルドは R8（`minifyEnabled` / `shrinkResources` 有効）+ keep ルール、`networkSecurityConfig` で cleartext 拒否。
- CI は Secrets ベースの本番署名付き Release 生成まで含める。

## Definition of Done

Phase 1 完了は次の全項目を満たすことで判定する。Verification の各番号と対応付けて運用する。

- ビルドと CI: `./gradlew lintDebug testDebug assembleDebug` がローカルと CI で成功し、tag v\* で署名付き `assembleRelease` / `bundleRelease` が生成される（Verification 1, 10）。
- 設定とセキュリティ: API キーが EncryptedSharedPreferences に暗号化保存され、未設定判定が共有起動経路でも機能し、SharedPreferences ファイルに平文で露出しない（Verification 2, 12）。
- テンプレート: 初回起動 seed が 4 件のみ投入され、再起動で重複しない。削除フォールバックが仕様通りに動く（Verification 3）。
- Generation pipeline: text / image いずれの Flow も最後まで完了し、SDK 例外が GeminiError に正規化される（Verification 4）。
- エラー表現: 401 相当の認証失敗が GeminiError(Auth) に正規化され、UI に認証失敗が明示される（Verification 11）。
- 画像処理: grant Uri 失効後も内部コピーで処理が継続し、リサイズ OFF ではフェイルセーフ以下の WebP/JPEG/PNG がパススルーされ、条件超過時または非対応形式ではフェイルセーフ上限内に再縮小される（Verification 5）。
- 状態維持: ストリーミング中の回転で UI と通信が継続し、process death 後は ProcessDeath エラー状態に落ちて再生成導線が出る（Verification 6）。
- インテント受信: ACTION_SEND（text/plain, image/\*）、ACTION_PROCESS_TEXT、ACTION_SEND_MULTIPLE のすべてが期待通りに起動し、複数画像時は通知が表示される（Verification 7, 8）。
- 履歴: 保存・検索・削除・再実行・pin・画像履歴参照が全て成立し、デフォルト pruning が機能する（Verification 9）。

## Further Considerations

1. temp 画像キャッシュの掃除は Phase 1 では起動時 sweep に寄せる。件数上限併用は履歴拡張時に検討する。
2. Room migration は 1.0 では destructive migration でも成立するが、OSS 公開直後から継続運用を見込むなら schema export を有効化し、初回から migration テストの土台を置く。
3. クラッシュ収集（Crashlytics 等）と構造化ログ（Timber 等）は Phase 1 スコープ外。導入する場合は Phase 2 開始時に方針決定する。
4. アクセシビリティの追加検証（フォントサイズ拡大時のレイアウト崩れ、コントラスト比）は Phase 1 では最小確認に留め、Phase 2 で網羅検証する。
