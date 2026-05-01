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
