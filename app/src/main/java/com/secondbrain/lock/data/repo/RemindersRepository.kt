package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.OkResponse
import com.secondbrain.lock.network.dto.Reminder
import com.secondbrain.lock.network.dto.ReminderActionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backs the top bar's NotificationBell + any in-screen due-reminder nudges. Polled every 60s. */
object RemindersRepository {
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    suspend fun restore() {
        LocalCache.load<List<Reminder>>("reminders")?.let { _reminders.value = it }
    }

    suspend fun refresh() {
        ApiClient.getTyped<List<Reminder>>("/api/reminders").onSuccess { _reminders.value = it; LocalCache.save("reminders", it) }
    }

    suspend fun act(id: String, action: String, minutes: Int? = null): Result<Unit> {
        val result = ApiClient.patchTyped<ReminderActionRequest, OkResponse>(
            "/api/reminders/$id",
            ReminderActionRequest(action = action, minutes = minutes)
        )
        refresh()
        return result.map {}
    }
}
