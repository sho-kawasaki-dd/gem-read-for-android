# Gem Read for Android

ブラウザ上のテキストや画面上の画像（スクリーンショット）をシームレスに取得し、Gemini APIを用いて翻訳・解説を行うAndroid向けユーティリティアプリ。

## Phase 1: OS標準インテント連携 (MVP)
OS標準の共有インテント（ACTION_SEND）やテキスト選択メニュー（ACTION_PROCESS_TEXT）から Gemini API を呼び出し、結果をボトムシートで表示します。

## 開発環境
- Kotlin / Jetpack Compose / Material 3
- minSdk 29 / targetSdk 35 / compileSdk 35
- [google-genai](https://github.com/google/generative-ai-android) SDK

## ライセンス
Apache License 2.0

## 開発と署名
本プロジェクトは GitHub Actions を用いた CI/CD を構築しています。

### CI 設定 (GitHub Secrets)
リリースの自動ビルドと署名には以下の Secrets 設定が必要です：
- `KEYSTORE_BASE64`: リリース用キーストアファイルを Base64 エンコードした文字列
- `KEYSTORE_PASSWORD`: キーストアのパスワード
- `KEY_ALIAS`: 鍵のエイリアス
- `KEY_PASSWORD`: 鍵のパスワード

### ローカルでの署名ビルド
ローカル環境で署名付きビルドを行う場合は、`local.properties` または `gradle.properties` に以下の情報を追記してください：
```properties
RELEASE_KEYSTORE_PATH=/path/to/your/release.keystore
RELEASE_KEYSTORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_password
```
あるいは環境変数 `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` を設定することでもビルド可能です。
