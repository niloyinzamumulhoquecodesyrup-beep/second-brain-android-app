package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.MindQueueAnswerRequest
import com.secondbrain.lock.network.dto.MindQueueItem
import com.secondbrain.lock.network.dto.MindQueueTaskValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs "Your brain suggests" on the Work screen — the create_task subset of the shared
 * para_fun_queue, filtered client-side exactly like the web app's TasksPanel.js does.
 */
object MindQueueRepository {
    private val _items = MutableStateFlow<List<MindQueueItem>>(emptyList())
    /** Raw pending queue; callers filter with [taskSuggestionTitle] for the create_task subset. */
    val items: StateFlow<List<MindQueueItem>> = _items.asStateFlow()

    suspend fun restore() {
        LocalCache.load<List<MindQueueItem>>("mind_queue_items")?.let { _items.value = it }
    }

    suspend fun refresh() {
        ApiClient.getMindQueue().onSuccess { _items.value = it; LocalCache.save("mind_queue_items", it) }
    }

    suspend fun accept(item: MindQueueItem, title: String): Result<Unit> =
        ApiClient.answerMindQueue(item.id, MindQueueAnswerRequest(action = "create_task", value = MindQueueTaskValue(title)))
            .onSuccess {
                _items.value = _items.value.filterNot { it.id == item.id }
                LocalCache.save("mind_queue_items", _items.value)
            }
            .map {}

    suspend fun dismiss(item: MindQueueItem): Result<Unit> =
        ApiClient.answerMindQueue(item.id, MindQueueAnswerRequest(action = "skip"))
            .onSuccess {
                _items.value = _items.value.filterNot { it.id == item.id }
                LocalCache.save("mind_queue_items", _items.value)
            }
            .map {}
}
