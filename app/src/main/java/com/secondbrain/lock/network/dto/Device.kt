package com.secondbrain.lock.network.dto

import kotlinx.serialization.Serializable

/** POST/DELETE /api/devices/register (P23, backend) — registers or unregisters this device's FCM
 * token so the server's reminder evaluator can push a DATA-only backstop message when the local
 * AlarmManager tier can't fire (process killed by an OEM battery manager, exact-alarm permission
 * denied and still inside the inexact window, etc). Not live on the backend as of this commit —
 * see docs/api-reference.md — so calls through this DTO 404 gracefully until P23 ships, the same
 * way RemindersRepository's snooze PATCH already tolerates a missing backend route. */
@Serializable
data class DeviceTokenRequest(
    val token: String,
    val platform: String = "android"
)
