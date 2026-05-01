package io.github.ikinocore.gemread.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.ikinocore.gemread.android.data.image.ImageDownscaler
import io.github.ikinocore.gemread.android.domain.usecase.SeedTemplatesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hilt の DI エントリポイントとなる Application クラス。
@HiltAndroidApp
class GemReadApp : Application() {

    @Inject
    lateinit var seedTemplatesUseCase: SeedTemplatesUseCase

    @Inject
    lateinit var imageDownscaler: ImageDownscaler

    // アプリプロセスのライフサイクルと同期する CoroutineScope。
    // Application はプロセスと同じ寿命なためキャンセル不要。
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 初回起動時のテンプレート seed を Application 起点で実行する。
        // ShareReceiverActivity など MainActivity を経由しないエントリポイントでも
        // 確実に seed が走るよう、Application.onCreate() で呼び出す。
        applicationScope.launch {
            seedTemplatesUseCase()
        }
        // 起動時 sweep: 前回セッションで保存されなかった一時画像キャッシュを削除する。
        // 履歴に昇格済みの画像は filesDir/history/ に移動済みのため影響を受けない。
        applicationScope.launch {
            imageDownscaler.clearCache()
        }
    }
}
