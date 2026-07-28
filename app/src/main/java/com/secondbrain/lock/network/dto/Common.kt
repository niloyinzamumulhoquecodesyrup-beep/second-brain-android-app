package com.secondbrain.lock.network.dto

import kotlinx.serialization.Serializable

/** Generic `{ ok: true, ... }` acknowledgement shape used by several mutation endpoints. */
@Serializable
data class OkResponse(val ok: Boolean = true)
