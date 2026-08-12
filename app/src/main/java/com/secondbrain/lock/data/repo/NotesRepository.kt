package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.PendingOp
import com.secondbrain.lock.data.SyncQueue
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
import kotlinx.coroutines.flow.update
import java.util.UUID

private const val LOCAL_ID_PREFIX = "local-"

/** Backs PARACube (active, non-graduated notes) + GraduatedSection.
 *
 * Offline queueing here covers note CREATE only (see [create]). Still missing, each left for a
 * follow-up because it has its own ordering interaction with a queued create: [moveToPara],
 * [graduate], and [updateNote] for notes; and separately, routines, planner blocks, reminders,
 * and packets have no offline path anywhere in the app yet. */
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

    /** For the untagged (base) result, merges the server's list into local state rather than
     * replacing it wholesale — a plain `_paraNotes.value = filtered` wipes any offline-created
     * placeholder the moment this refresh beats [SyncQueue.flush]'s WorkManager job back from a
     * cold start, exactly the bug [TasksRepository.refresh] had to fix for tasks. Since the whole
     * point of the capture sheet is that nothing typed is ever lost, this matters even more here.
     * A tag-filtered result stays a plain replace — it's a transient search view over the same
     * base dataset, not the dataset itself (see [restore]'s note on why it's never cached either).
     *
     * Uses [MutableStateFlow.update] rather than a plain read-then-write for the untagged branch,
     * because this merge and [resolveLocalNote]'s merge are independent writers that can genuinely
     * run concurrently right after reconnect (the queued create's POST and this GET can land
     * back-to-back). A plain `_paraNotes.value = _paraNotes.value.x` on both sides can interleave
     * into one writer's result clobbering the other's — in practice this showed up as the same
     * synced note appearing twice. `update`'s CAS retry means each transform always sees the
     * other's result before committing. */
    suspend fun refreshPara(tag: String? = null) {
        val query = if (tag.isNullOrBlank()) "" else "?tag=${tag}"
        ApiClient.getTyped<List<Note>>("/api/notes$query")
            .onSuccess { notes ->
                val filtered = notes.filter { !it.graduated }
                if (tag.isNullOrBlank()) {
                    _paraNotes.update { local ->
                        val unsynced = local.filter { it.id.startsWith(LOCAL_ID_PREFIX) }
                        val locallyEdited = local.filter { it.pendingSync && !it.id.startsWith(LOCAL_ID_PREFIX) }
                            .associateBy { it.id }
                        filtered.map { locallyEdited[it.id] ?: it } + unsynced
                    }
                    LocalCache.save("notes_para", _paraNotes.value)
                } else {
                    _paraNotes.value = filtered
                }
                _error.value = null
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

    /** On a genuine (non-connectivity) failure this surfaces the error like every other mutator
     * here. When offline, it instead inserts a local placeholder (id "local-<uuid>",
     * [Note.pendingSync] = true) and queues the real create via [SyncQueue] for when connectivity
     * returns — mirrors [TasksRepository.create]; see [SyncQueue.flush]'s "create_note" handling
     * for how the placeholder gets swapped for the server's real note.
     *
     * CREATE only. moveToPara/graduate/note updates aren't offline-queued yet — each has its own
     * ordering interaction with a queued create that's easier to reason about once this is solid.
     */
    suspend fun create(request: CreateNoteRequest): Result<Note> {
        val result = ApiClient.postTyped<CreateNoteRequest, Note>("/api/notes", request)
        result.onSuccess { note -> if (!note.graduated) _paraNotes.update { it + note }; _error.value = null }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                val placeholder = Note(
                    id = "$LOCAL_ID_PREFIX${UUID.randomUUID()}",
                    title = request.title,
                    content = request.content,
                    para = request.para ?: "inbox",
                    tags = request.tags ?: emptyList(),
                    sourceUrl = request.sourceUrl,
                    // A server-created note always has created_at set to "now" — without it here
                    // this placeholder reads as created on no particular day, same reasoning as
                    // TasksRepository.create's placeholder.
                    createdAt = java.time.Instant.now().toString(),
                    pendingSync = true
                )
                _paraNotes.update { it + placeholder }
                LocalCache.save("notes_para", _paraNotes.value)
                SyncQueue.enqueue(
                    PendingOp(
                        id = SyncQueue.newOpId(),
                        type = PendingOp.TYPE_CREATE_NOTE,
                        createdAt = System.currentTimeMillis(),
                        localId = placeholder.id,
                        createNote = request
                    )
                )
                return Result.success(placeholder)
            }
            _error.value = result.exceptionOrNull()?.message ?: "Couldn't save note"
        }
        return result
    }

    suspend fun moveToPara(id: String, para: String): Result<Note> =
        ApiClient.postTyped<MoveParaRequest, Note>("/api/para", MoveParaRequest(id, para))
            .onSuccess { updated ->
                _paraNotes.update { list -> list.map { if (it.id == id) updated else it } }
            }

    suspend fun graduate(id: String): Result<Note> =
        ApiClient.putTyped<UpdateNoteRequest, Note>("/api/notes/$id", UpdateNoteRequest(graduated = true))
            .onSuccess { updated ->
                _paraNotes.update { list -> list.filterNot { it.id == id } }
                _graduatedNotes.update { listOf(updated) + it }
            }

    suspend fun getNote(id: String): Result<Note> = ApiClient.getTyped("/api/notes/$id")

    suspend fun updateNote(id: String, request: UpdateNoteRequest): Result<Note> =
        ApiClient.putTyped<UpdateNoteRequest, Note>("/api/notes/$id", request)

    /** A note that never synced (still has its local placeholder id) is simply dropped — and its
     * queued create cancelled via [SyncQueue.cancelPendingCreate] — rather than deleted
     * server-side, since the server never saw it (mirrors [TasksRepository.delete]). */
    suspend fun deleteNote(id: String): Result<Unit> {
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            _paraNotes.update { it.filterNot { n -> n.id == id } }
            LocalCache.save("notes_para", _paraNotes.value)
            SyncQueue.cancelPendingCreate(id)
            return Result.success(Unit)
        }
        return ApiClient.deleteRaw("/api/notes/$id")
    }

    /** Called by [SyncQueue.flush] once a queued create_note op succeeds: swaps the optimistic
     * local entry for the server's authoritative copy — or appends it if a [refreshPara] already
     * wiped the placeholder before this ran (mirrors [TasksRepository.resolveLocalTask]'s fix for
     * the same reconnect race). Uses [MutableStateFlow.update] for the same reason [refreshPara]
     * does — this and a concurrent [refreshPara] are independent writers on reconnect. */
    suspend fun resolveLocalNote(placeholderId: String, real: Note) {
        _paraNotes.update { current ->
            when {
                current.any { it.id == placeholderId } -> current.map { if (it.id == placeholderId) real else it }
                current.none { it.id == real.id } -> current + real
                else -> current
            }
        }
        LocalCache.save("notes_para", _paraNotes.value)
    }

    suspend fun getLinks(id: String): Result<NoteLinksResponse> = ApiClient.getTyped("/api/notes/$id/links")

    suspend fun getRelated(id: String): Result<List<RelatedNote>> = ApiClient.getTyped("/api/notes/$id/related")

    suspend fun getGraph(): Result<NoteGraphResponse> = ApiClient.getTyped("/api/notes/graph")
}
