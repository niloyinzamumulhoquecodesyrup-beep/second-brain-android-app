package com.secondbrain.lock.data

/** Mirrors the response of GET /api/focus/state on the second-brain backend. */
data class FocusState(
    val active: Boolean,
    val sessionId: String?,
    val mode: String?,
    val startedAtMillis: Long?,
    val endsAtMillis: Long?,
    val taskId: String?
) {
    companion object {
        val INACTIVE = FocusState(
            active = false, sessionId = null, mode = null,
            startedAtMillis = null, endsAtMillis = null, taskId = null
        )
    }
}
