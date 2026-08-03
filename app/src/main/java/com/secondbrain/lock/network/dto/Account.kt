package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(val name: String)

/** GET/PUT /api/notification-prefs — the "quiet hours" window during which reminder nudges wait
 * rather than firing. Minutes-since-midnight, matching every other start/duration field in the
 * app (see RoutinePlanner/TasksPanel's startMin convention). */
@Serializable
data class NotificationPrefs(
    @SerialName("quiet_start_min") val quietStartMin: Int? = null,
    @SerialName("quiet_end_min") val quietEndMin: Int? = null
)
