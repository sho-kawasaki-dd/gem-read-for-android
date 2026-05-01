package io.github.ikinocore.gemread.android.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * テキスト・画像の生成パイプラインを抽象化するリポジトリ。
 * ViewModel はこのインターフェースを通じて生成を要求し、
 * 実装詳細（Gemini SDK / 画像処理 / テンプレート解決）に依存しない。
 */
interface GenerationRepository {
    /**
     * 指定したプロンプト・画像・テンプレートで生成を実行する。
     * ストリーミング有効時は各チャンクを逐次 emit し、無効時は完成テキストを一括 emit する。
     * エラー時は [io.github.ikinocore.gemread.android.data.api.GeminiError] をスローする。
     *
     * @param prompt ユーザー入力テキスト
     * @param imageUri 入力画像 Uri（テキストのみの場合は null）
     * @param templateId 使用するテンプレートの ID（null の場合はデフォルトテンプレートを使用）
     */
    fun generate(
        prompt: String,
        imageUri: Uri? = null,
        templateId: Long? = null,
    ): Flow<String>
}
