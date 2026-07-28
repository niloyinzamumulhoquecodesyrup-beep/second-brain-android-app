package com.secondbrain.lock.network.dto

import kotlinx.serialization.Serializable

/**
 * GET /api/auth/me. Note there is no avatar URL field — `hasAvatar` only says whether one
 * exists; the actual image bytes are streamed from GET /api/auth/avatar (an authenticated,
 * non-hotlinkable endpoint backed by a bytea column, not object storage).
 */
@Serializable
data class Profile(
    val email: String = "",
    val name: String? = null,
    val hasAvatar: Boolean = false
)
