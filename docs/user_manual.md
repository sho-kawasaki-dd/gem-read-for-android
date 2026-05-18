# Gem Read for Android User Manual

Gem Read for Android は、Android の共有メニューやテキスト選択メニューからテキストや画像を Gemini に渡し、翻訳・要約・解説などの回答を受け取るためのアプリです。

This manual describes the current implemented behavior of Phase 1. Planned features are separated from the main usage guide.

## 1. Overview / 概要

- App name: Gem Read
- Supported platform: Android
- Main purpose: Send shared text or shared images to Gemini and read the response in a bottom sheet result view.
- Required setup: Gemini API key
- No sign-in account is required inside the app.

## 2. What You Can Do / できること

現時点のアプリでは、次の使い方に対応しています。

1. ホーム画面でテキストを手動入力して Gemini に送る
2. 他のアプリからテキストを共有して Gemini に送る
3. 他のアプリから画像を共有して Gemini に送る
4. Android のテキスト選択メニューから Gem Read を呼び出す
5. 回答をコピーする
6. 回答を履歴に残し、あとで再実行する
7. プロンプトテンプレートを追加・編集・並べ替え・既定化する
8. モデル、画像リサイズ、ストリーミング、履歴保持条件を設定する

## 3. First-Time Setup / 初回設定

Gem Read を使い始める前に、Gemini API キーを設定してください。API キーが未設定の状態で共有機能を使うと、ホーム画面へ戻され、設定を促すメッセージが表示されます。

### 3.1 Open Settings / 設定を開く

1. Gem Read を起動します。
2. ホーム画面で `設定` を開きます。
3. `Gemini API キー` に API キーを入力します。

### 3.2 Test Connection / 接続テスト

1. API キー入力後、`接続テスト` を押します。
2. `接続に成功しました。` と表示されれば利用準備は完了です。

接続に失敗した場合は、次を確認してください。

- API キーが正しいか
- ネットワーク接続が有効か
- レート制限に達していないか

### 3.3 Default Settings / 初期設定値

- Default model: `gemini-2.5-flash`
- Available models:
    - `gemini-2.5-flash`
    - `gemini-2.5-pro`
    - `gemini-2.5-flash-lite`
- Streaming default: ON
- Image resize default: ON
- History retention default: 200 entries / 90 days

## 4. Home Screen / ホーム画面

ホーム画面は、手動入力と各管理画面への入口です。

### Main actions / 主な操作

- `履歴` を開く
- `テンプレート管理` を開く
- `設定` を開く
- `手動入力プロンプト` に文字を入れて `生成` を押す

### Manual input / 手動入力

1. `手動入力プロンプト` にテキストを入力します。
2. `生成` を押します。
3. 結果画面が開き、Gemini の回答が表示されます。

この入力方法は、共有メニューを使わずに短いメモや質問を直接送るときに向いています。

### Screenshot placeholder / スクリーンショット差し込み位置

`[Screenshot: Home screen / ホーム画面]`

## 5. How to Use by Entry Point / 起点別の使い方

### 5.1 Share Text / テキスト共有

他のアプリでテキストを共有し、Gem Read に送る方法です。

1. 共有したいテキストを含むアプリを開きます。
2. Android の共有メニューを開きます。
3. `Gem Read` を選びます。
4. 結果画面が開き、Gemini の回答が表示されます。

### 5.2 Share Image / 画像共有

スクリーンショットや画像を共有して Gemini に送る方法です。

1. 画像を表示しているアプリで共有メニューを開きます。
2. `Gem Read` を選びます。
3. 画像プレビュー付きの結果画面が開きます。
4. 回答が生成されるまで待ちます。

対応画像形式は次のとおりです。

- PNG
- JPEG
- WebP

### 5.3 Share Multiple Images / 複数画像共有

複数画像を同時に共有した場合、現在の実装では先頭 1 枚のみ処理されます。結果画面の上部に通知バナーが表示されます。

この制約は現在仕様です。複数枚同時解析は未実装です。

### 5.4 Process Text / テキスト選択メニュー

他のアプリでテキストを選択し、Android のテキスト選択メニューから Gem Read を呼び出せます。

1. 対象アプリでテキストを選択します。
2. テキスト選択メニューから Gem Read を選びます。
3. 結果画面で回答を確認します。

Note: Current behavior is read-only. The processed text is not written back into the original app.

### Screenshot placeholders / スクリーンショット差し込み位置

`[Screenshot: Share text flow / テキスト共有]`

`[Screenshot: Share image flow / 画像共有]`

`[Screenshot: Process text flow / テキスト選択メニュー]`

## 6. Result Screen / 結果画面

結果画面は半透明背景のボトムシートで開きます。入力テキストまたは入力画像のプレビュー、テンプレート選択、Gemini の回答、各種アクションがまとまっています。

### Available actions / 使える操作

- テンプレートを切り替える
- 回答を `コピー` する
- 回答を `ピン留め` する
- `設定` を開く
- `再生成` する
- 画面を閉じる

### Template switching / テンプレート切り替え

結果画面のテンプレートチップを切り替えると、選んだテンプレートで再生成されます。

### Copy / コピー

`コピー` を押すと、表示中の回答がクリップボードへコピーされます。

### Pin / ピン留め

回答が履歴として保存されたあと、`ピン留め` を押すと保持対象として扱いやすくなります。ピン留めした履歴は自動削除対象外です。

### Regenerate / 再生成

自動再試行はありません。回答をもう一度取りたい場合は `再生成` を使います。

### Streaming / ストリーミング表示

ストリーミングが ON の場合、回答は少しずつ追記表示されます。OFF の場合は完成した回答がまとめて表示されます。

### Process death / 処理中断

アプリのプロセスが終了した場合、生成は自動再開されません。中断メッセージが表示されたら、必要に応じて `再生成` を実行してください。

### Screenshot placeholder / スクリーンショット差し込み位置

`[Screenshot: Result screen / 結果画面]`

## 7. History / 履歴

履歴画面では、過去の入出力を検索、再実行、ピン留め、削除できます。

### Available actions / 使える操作

- 履歴を検索する
- `ピン留めのみ` で絞り込む
- 項目を再実行する
- 項目をピン留めする
- 項目を削除する

### Re-run / 再実行

履歴項目をタップするか、再実行ボタンを押すと、その入力内容で結果画面を再度開きます。

### Search / 検索

`履歴を検索…` にキーワードを入れると、対象の履歴を絞り込めます。

### Screenshot placeholder / スクリーンショット差し込み位置

`[Screenshot: History screen / 履歴画面]`

## 8. Prompt Templates / テンプレート管理

テンプレート管理画面では、システムプロンプトのひな型を管理できます。

### Initial templates / 初期テンプレート

初回状態では、次のテンプレートが投入されます。

1. 要約
2. 翻訳（日本語へ）
3. 解説
4. 校正・添削

### Available actions / 使える操作

- テンプレートを追加する
- テンプレートを編集する
- デフォルトに設定する
- 上下に並べ替える
- 削除する

### Important note / 注意事項

最後の 1 件は削除できません。

### Screenshot placeholder / スクリーンショット差し込み位置

`[Screenshot: Template management screen / テンプレート管理画面]`

## 9. Settings / 設定

設定画面では、Gemini 接続と動作条件を調整できます。

### Items you can configure / 設定できる項目

- Gemini API キー
- モデル
- ベースシステムプロンプト
- 画像リサイズ
- ストリーミング
- 履歴保持件数
- 履歴保持日数
- 接続テスト

### Setting details / 各項目の意味

`Gemini API キー`  
Gemini 利用に必要なキーです。未入力では共有機能を使えません。

`モデル`  
利用する Gemini モデルを選びます。

`ベースシステムプロンプト`  
すべての生成に共通して付与したい指示を入力します。

`画像リサイズ`  
ON の場合は長辺 1568px、JPEG quality 85 を目安に縮小して送信します。

`ストリーミング`  
ON の場合は回答を逐次受信して結果画面に追記表示します。

`履歴保持件数` / `履歴保持日数`  
履歴の自動整理条件です。ピン留めされた履歴は対象外です。

### Screenshot placeholder / スクリーンショット差し込み位置

`[Screenshot: Settings screen / 設定画面]`

## 10. Data and Privacy / データとプライバシー

アプリ内で扱う主なデータは次のとおりです。

- API キー
- 入力テキスト
- 入力画像の履歴参照情報
- Gemini の回答
- テンプレート設定
- 履歴保持設定などのアプリ設定

### Storage / 保存

- API キーは暗号化ストレージに保存されます。
- 履歴は端末内に保存されます。
- 画像履歴はアプリ内部の保存領域を使います。

### Permissions / 権限

現在の実装で必要なのはインターネット接続です。カメラや連絡先などの権限は要求しません。

## 11. Troubleshooting / トラブルシューティング

### I cannot use share features / 共有機能が使えない

`共有機能を使う前に API キーを設定してください。` または同様の案内が出る場合は、設定画面で API キーを登録してください。

### Authentication failed / 認証に失敗した

`認証に失敗しました。APIキーを確認してください。` と表示される場合は、API キーの再確認と接続テストを行ってください。

### Network error / ネットワークエラー

`ネットワークエラーが発生しました。` と表示された場合は、通信状態を確認して再生成してください。

### Rate limited / レート制限

`レート制限に達しました。しばらく待ってから再試行してください。` と表示された場合は、少し時間を空けて再生成してください。

### Shared data too large / 共有データが大きすぎる

`共有されたデータが大きすぎるため、処理できませんでした。` と表示される場合は、共有する内容を短くするか、画像サイズを見直してください。

### Unsupported share input / 非対応の共有データ

`この共有データは Phase 1 では処理できません。` と表示される場合は、テキスト、PNG、JPEG、WebP のいずれかで共有してください。

## 12. Known Limitations / 既知の制限

- `ACTION_SEND_MULTIPLE` で複数画像を共有しても、先頭 1 枚のみ処理します。
- HEIC と GIF は未対応です。
- テキスト選択メニュー経由の処理は読み取り専用です。
- 自動再試行はありません。
- プロセス終了後の自動再開はありません。
- API キー未設定時は共有処理を継続せず、ホーム画面へ誘導します。

## 13. Future Scope Appendix / 将来機能の補足

以下は Phase 1 の本文には含めない将来構想です。

- 複数画像の同時解析
- MediaProjection を使った画面取得
- Overlay や常駐型の操作導線

将来機能は未実装です。現行バージョンの使い方としては扱わないでください。

## 14. Screenshot Checklist / スクリーンショット撮影チェックリスト

後から画像を差し込む場合は、少なくとも次を撮影してください。

1. Home screen
2. Settings screen
3. Share text flow
4. Share image flow
5. Result screen
6. History screen
7. Template management screen

画像ファイルの配置ルールは [docs/images/user-manual/README.md](images/user-manual/README.md) を参照してください。
