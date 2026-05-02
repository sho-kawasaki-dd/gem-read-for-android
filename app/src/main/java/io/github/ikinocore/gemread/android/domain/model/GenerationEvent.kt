package io.github.ikinocore.gemread.android.domain.model

/**
 * [io.github.ikinocore.gemread.android.domain.repository.GenerationRepository.generate] が
 * Flow として emit するイベント型。
 * ViewModel はこれを受け取ってストリーミング状態と履歴 ID を管理する。
 */
sealed class GenerationEvent {
    /** ストリーミング中の部分テキストチャンク */
    data class Chunk(val text: String) : GenerationEvent()

    /** 生成および履歴保存が完了したことを示す。historyId は保存されたレコードの ID */
    data class Completed(val historyId: Long) : GenerationEvent()
}
