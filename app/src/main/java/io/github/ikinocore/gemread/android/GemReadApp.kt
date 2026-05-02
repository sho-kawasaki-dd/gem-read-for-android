package io.github.ikinocore.gemread.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.ikinocore.gemread.android.data.image.ImageDownscaler
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.domain.usecase.SeedTemplatesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hilt の DI エントリポイントとなる Application クラス。
@HiltAndroidApp
class GemReadApp : Application() {

    @Inject
    lateinit var seedTemplatesUseCase: SeedTemplatesUseCase

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var imageDownscaler: ImageDownscaler

    // アプリプロセスのライフサイクルと同期する CoroutineScope。
    // Application はプロセスと同じ寿命なためキャンセル不要。
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 起動時のメンテナンス処理
        applicationScope.launch {
            // 1. テンプレートのシード投入（初回起動時のみ）
            seedTemplatesUseCase()

            // 2. 履歴の pruning (最大件数・保持日数)
            // pinned=1 のエントリーは SQL 側で除外されている。
            val maxCount = appPreferences.historyRetentionCount.first()
            val maxDays = appPreferences.historyRetentionDays.first()
            historyRepository.pruneHistory(maxCount, maxDays)

            // 3. 起動時 sweep: 前回セッションで保存されなかった一時画像キャッシュを削除する。
            // 履歴に昇格済みの画像は filesDir/history/ に移動済みのため影響を受けない。
            imageDownscaler.clearCache()
        }
    }
}
