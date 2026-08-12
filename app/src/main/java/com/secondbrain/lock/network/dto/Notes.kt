package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** para: inbox|project|area|resource|archive. */
@Serializable
data class Note(
    val id: String = "",
    val title: String = "",
    val content: String? = null,
    val para: String = "inbox",
    val status: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("executive_summary") val executiveSummary: String? = null,
    val distilled: Boolean = false,
    val pinned: Boolean = false,
    val graduated: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Local-only: true while this note (or an edit to it) is queued in [com.secondbrain.lock.data.SyncQueue]
     * waiting for connectivity. Never sent to or set by the server. */
    val pendingSync: Boolean = false
)

@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String? = null,
    val tags: List<String>? = null,
    val para: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null
)

/** PATCH-style partial update — only non-null fields are meant to change. */
@Serializable
data class UpdateNoteRequest(
    val title: String? = null,
    val content: String? = null,
    val para: String? = null,
    @SerialName("executive_summary") val executiveSummary: String? = null,
    val distilled: Boolean? = null,
    val status: String? = null,
    val tags: List<String>? = null,
    val pinned: Boolean? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    val graduated: Boolean? = null
)

@Serializable
data class NoteRef(val id: String = "", val title: String = "", val para: String = "inbox")

/** GET /api/notes/:id/links */
@Serializable
data class NoteLinksResponse(
    val outgoing: List<NoteRef> = emptyList(),
    val incoming: List<NoteRef> = emptyList()
)

/** GET /api/notes/:id/related — one entry per row, similarity in [0,1]. */
@Serializable
data class RelatedNote(
    val id: String = "",
    val title: String = "",
    val para: String = "inbox",
    val similarity: Double = 0.0
)

@Serializable
data class MoveParaRequest(val id: String, val para: String)

/** GET /api/notes/graph */
@Serializable
data class NoteGraphNode(
    val id: String = "",
    val title: String = "",
    val para: String = "inbox",
    val tags: List<String> = emptyList(),
    val distilled: Boolean = false
)

@Serializable
data class NoteGraphEdge(
    val from: String = "",
    val to: String = "",
    val type: String = "link",
    val similarity: Double? = null
)

@Serializable
data class NoteGraphResponse(
    val nodes: List<NoteGraphNode> = emptyList(),
    val edges: List<NoteGraphEdge> = emptyList()
)

/** Packet: a distilled excerpt clipped from a note. */
@Serializable
data class Packet(
    val id: String = "",
    @SerialName("note_id") val noteId: String = "",
    val title: String = "",
    val content: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreatePacketRequest(
    @SerialName("note_id") val noteId: String,
    val title: String? = null,
    val content: String? = null
)
