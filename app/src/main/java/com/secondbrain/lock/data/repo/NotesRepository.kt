package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.network.dto.MoveParaRequest
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.network.dto.NoteGraphResponse
import com.secondbrain.lock.network.dto.NoteLinksResponse
import com.secondbrain.lock.network.dto.Packet
import com.secondbrain.lock.network.dto.RelatedNote
import com.secondbrain.lock.network.dto.UpdateNoteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backs PARACube (active, non-graduated notes) + GraduatedSection. */
object NotesRepository {
    private val _paraNotes = MutableStateFlow<List<Note>>(emptyList())
    val paraNotes: StateFlow<List<Note>> = _paraNotes.asStateFlow()

    private val _graduatedNotes = MutableStateFlow<List<Note>>(emptyList())
    val graduatedNotes: StateFlow<List<Note>> = _graduatedNotes.asStateFlow()

    private val _packets = MutableStateFlow<List<Packet>>(emptyList())
    val packets: StateFlow<List<Packet>> = _packets.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Only restores the untagged (all-notes) view — a tag-filtered result is a transient UI
     * selection, not the base dataset, so it's never cached. */
    suspend fun restore() {
        LocalCache.load<List<Note>>("notes_para")?.let { _paraNotes.value = it }
        LocalCache.load<List<Note>>("notes_graduated")?.let { _graduatedNotes.value = it }
        LocalCache.load<List<Packet>>("notes_packets")?.let { _packets.value = it }
    }

    suspend fun refreshPara(tag: String? = null) {
        val query = if (tag.isNullOrBlank()) "" else "?tag=${tag}"
        ApiClient.getTyped<List<Note>>("/api/notes$query")
            .onSuccess { notes ->
                val filtered = notes.filter { !it.graduated }
                _paraNotes.value = filtered
                _error.value = null
                if (tag.isNullOrBlank()) LocalCache.save("notes_para", filtered)
            }
            .onFailure { _error.value = it.message ?: "Couldn't load notes" }
    }

    suspend fun refreshGraduated() {
        ApiClient.getTyped<List<Note>>("/api/notes?graduated=true")
            .onSuccess { _graduatedNotes.value = it; LocalCache.save("notes_graduated", it) }
            .onFailure { _error.value = it.message ?: "Couldn't load graduated notes" }
    }

    suspend fun refreshPackets() {
        ApiClient.getPackets()
            .onSuccess { _packets.value = it; LocalCache.save("notes_packets", it) }
            .onFailure { _error.value = it.message ?: "Couldn't load packets" }
    }

    suspend fun create(request: CreateNoteRequest): Result<Note> =
        ApiClient.postTyped<CreateNoteRequest, Note>("/api/notes", request)
            .onSuccess { note -> if (!note.graduated) _paraNotes.value = _paraNotes.value + note }

    suspend fun moveToPara(id: String, para: String): Result<Note> =
        ApiClient.postTyped<MoveParaRequest, Note>("/api/para", MoveParaRequest(id, para))
            .onSuccess { updated ->
                _paraNotes.value = _paraNotes.value.map { if (it.id == id) updated else it }
            }

    suspend fun graduate(id: String): Result<Note> =
        ApiClient.putTyped<UpdateNoteRequest, Note>("/api/notes/$id", UpdateNoteRequest(graduated = true))
            .onSuccess { updated ->
                _paraNotes.value = _paraNotes.value.filterNot { it.id == id }
                _graduatedNotes.value = listOf(updated) + _graduatedNotes.value
            }

    suspend fun getNote(id: String): Result<Note> = ApiClient.getTyped("/api/notes/$id")

    suspend fun updateNote(id: String, request: UpdateNoteRequest): Result<Note> =
        ApiClient.putTyped<UpdateNoteRequest, Note>("/api/notes/$id", request)

    suspend fun deleteNote(id: String): Result<Unit> = ApiClient.deleteRaw("/api/notes/$id")

    suspend fun getLinks(id: String): Result<NoteLinksResponse> = ApiClient.getTyped("/api/notes/$id/links")

    suspend fun getRelated(id: String): Result<List<RelatedNote>> = ApiClient.getTyped("/api/notes/$id/related")

    suspend fun getGraph(): Result<NoteGraphResponse> = ApiClient.getTyped("/api/notes/graph")
}
