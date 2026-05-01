# Phase 1 仕様確定ドキュメント

本ドキュメントは、Gem Read for Android Phase 1 の実装における確定事項を記録する。Step 1 に基づき、これらの仕様は以後再議論せず、実装の指針とする。

## 1. スコープ
- **対象:** Phase 1（OS標準インテント連携 MVP）
- **除外:** Phase 2（MediaProjection / Overlay / ForegroundService）

## 2. 技術選定
- **言語:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **SDK:** `google-genai` Android SDK
- **Markdown レンダラ:** `com.mikepenz:multiplatform-markdown-renderer-m3`
- **画像ライブラリ:** Coil（入力プレビュー・履歴サムネイル用）
- **保存:** Room (DB), DataStore (Preferences), EncryptedSharedPreferences (API Key)
- **DI:** Hilt
- **Lint/Format:** Spotless (ktlint)

## 3. Gemini API 連携
- **デフォルトモデル:** `gemini-2.5-flash`
- **選択可能モデル:** `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash-lite`
- **ストリーミング:** ON/OFF 可能（デフォルト ON）
- **再試行:** 自動再試行なし。ユーザー操作による「再生成」のみ。
- **接続テスト:** 現在のモデルに対し "ping" を送信し 200 OK を確認。

## 4. インテント受信と入力処理
- **対応インテント:**
    - `ACTION_SEND` (text/plain)
    - `ACTION_SEND` (image/*)
    - `ACTION_SEND_MULTIPLE` (image/*) -> **先頭1枚のみ処理**。上部バナーで通知。
    - `ACTION_PROCESS_TEXT` -> **読み取り専用**（`setResult()` を返さない）。
- **画像 MIME:** `image/png`, `image/jpeg`, `image/webp` (HEIC/GIF は除外)。
- **画像キャッシュ・履歴保存:**
    - キャッシュ命名: `filesDir/cache/{uuid}.{ext}`
    - 履歴命名: `filesDir/history/{id}.{ext}`
    - 再エンコード時は `.jpg`、パススルー時は元形式に応じた拡張子を保持する。
- **画像リサイズ (ON):** 長辺 1568px, JPEG quality 85。
- **画像リサイズ (OFF):**
    - WebP/JPEG/PNG かつ フェイルセーフ（4096px/4MB）以下の場合はパススルー。
    - それ以外は再縮小・JPEG化。
- **URI 処理:** 受信後即座に内部キャッシュへコピー。`takePersistableUriPermission` は使用しない。
- **長文テキスト:** 50,000文字（または200KB）超はキャッシュファイル経由で Activity 間受け渡し。

## 5. UI/UX
- **結果表示:** `Theme.Material3.Translucent` + Compose `ModalBottomSheet`。
- **画面回転:** Activity再作成。ViewModel で通信と状態を維持。`configChanges` は原則使用しない。
- **Process Death:** 自動再実行なし。`Error(ProcessDeath)` 状態を表示し、手動再生成を促す。
- **APIキー未設定:** 共有経由でも MainActivity の onboarding へ誘導。

## 6. テンプレート管理
- **初期シード:** `assets/prompt_templates_ja.json` から 4 件投入。
- **削除制限:** 最後の 1 件は削除不可。デフォルト削除時は他を自動昇格。

## 7. 履歴管理
- **デフォルト保持:** 最大 200 件 / 90 日。設定で変更可能。
- **対象外:** ピン留め（pinned）された履歴は自動削除対象外。

## 8. ビルド・セキュリティ・CI/CD
- **ライセンス:** Apache-2.0
- **難読化:** R8 (minifyEnabled/shrinkResources) 有効。
- **ネットワーク:** `networkSecurityConfig` で cleartext 拒否。
- **APIキー:** `EncryptedSharedPreferences` (AES256-GCM) で暗号化保存。
- **CI:** GitHub Actions。tag `v*` で署名済み APK/AAB を Releases に自動添付。
- **署名:** GitHub Secrets を利用したリモート署名。`--no-daemon` + マスキング。

---
**確定日:** 2026-05-01
**ステータス:** 確定 (Fixed)
