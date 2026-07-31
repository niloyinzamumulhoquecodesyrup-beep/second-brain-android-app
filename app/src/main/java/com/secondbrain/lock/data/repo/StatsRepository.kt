package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.DayCount
import com.secondbrain.lock.network.dto.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Holds the latest /api/stats snapshot in memory so every screen that reads it sees the same
 * numbers without refetching. Task/focus completions patch the cached snapshot locally first
 * (mirroring the web's optimistic local bump in pages/work.js) rather than round-tripping.
 */
object StatsRepository {
    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun restore() {
        LocalCache.load<Stats>("stats")?.let { _stats.value = it }
    }

    suspend fun refresh() {
        ApiClient.getTyped<Stats>("/api/stats")
            .onSuccess { _stats.value = it; _error.value = null; LocalCache.save("stats", it) }
            .onFailure { _error.value = it.message ?: "Couldn't load stats" }
    }

    fun bumpTaskDone() {
        val current = _stats.value ?: return
        _stats.value = current.copy(
            tasksDone = current.tasksDone + 1,
            tasksOpen = (current.tasksOpen - 1).coerceAtLeast(0)
        )
    }

    fun bumpFocusSession() {
        val current = _stats.value ?: return
        _stats.value = current.copy(focusSessionsTotal = current.focusSessionsTotal + 1)
    }

    /** Adds a completed focus session's duration to today's bucket, ahead of the next full /api/stats refresh. */
    fun bumpFocusMinutes(minutes: Int) {
        val current = _stats.value ?: return
        val today = LocalDate.now().toString()
        val byDay = current.focusMinutesByDay.toMutableList()
        val idx = byDay.indexOfFirst { it.day == today }
        if (idx >= 0) {
            byDay[idx] = byDay[idx].copy(count = byDay[idx].count + minutes)
        } else {
            byDay.add(DayCount(today, minutes))
        }
        _stats.value = current.copy(
            focusMinutesByDay = byDay,
            focusMinutesTotal = current.focusMinutesTotal + minutes
        )
    }
}
