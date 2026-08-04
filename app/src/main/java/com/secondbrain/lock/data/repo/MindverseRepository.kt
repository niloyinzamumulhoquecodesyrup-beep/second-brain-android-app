package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.MindcordRealtime
import com.secondbrain.lock.network.dto.CommunityBook
import com.secondbrain.lock.network.dto.CommunityMessage
import com.secondbrain.lock.network.dto.CommunitySuggestion
import com.secondbrain.lock.network.dto.JoinRoomResponse
import com.secondbrain.lock.network.dto.MindcordDomain
import com.secondbrain.lock.network.dto.MindcordMessage
import com.secondbrain.lock.network.dto.MindcordParticipant
import com.secondbrain.lock.network.dto.OtherBrainsCluster
import com.secondbrain.lock.network.dto.OtherBrainsIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

/** Backs the MINDVERSE tab: Other Brains (anonymous community feed) + Mindcord (live room chat). */
object MindverseRepository {
    // Fire-and-forget target for work triggered from a non-suspend callback (Realtime's
    // participant-change notifications) — everything else in this object is called directly from
    // a caller-owned coroutine, this is the one exception.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _identity = MutableStateFlow<OtherBrainsIdentity?>(null)
    val identity: StateFlow<OtherBrainsIdentity?> = _identity.asStateFlow()

    private val _identityChecked = MutableStateFlow(false)
    val identityChecked: StateFlow<Boolean> = _identityChecked.asStateFlow()

    private val _messages = MutableStateFlow<List<CommunityMessage>>(emptyList())
    val messages: StateFlow<List<CommunityMessage>> = _messages.asStateFlow()

    private val _suggestions = MutableStateFlow<List<CommunitySuggestion>>(emptyList())
    val suggestions: StateFlow<List<CommunitySuggestion>> = _suggestions.asStateFlow()

    private val _books = MutableStateFlow<List<CommunityBook>>(emptyList())
    val books: StateFlow<List<CommunityBook>> = _books.asStateFlow()

    private val _domains = MutableStateFlow<List<MindcordDomain>>(emptyList())
    val domains: StateFlow<List<MindcordDomain>> = _domains.asStateFlow()

    private val _clusters = MutableStateFlow<List<OtherBrainsCluster>>(emptyList())
    val clusters: StateFlow<List<OtherBrainsCluster>> = _clusters.asStateFlow()

    private val _currentRoom = MutableStateFlow<JoinRoomResponse?>(null)
    val currentRoom: StateFlow<JoinRoomResponse?> = _currentRoom.asStateFlow()

    private val _roomMessages = MutableStateFlow<List<MindcordMessage>>(emptyList())
    val roomMessages: StateFlow<List<MindcordMessage>> = _roomMessages.asStateFlow()

    private val _roomParticipants = MutableStateFlow<List<MindcordParticipant>>(emptyList())
    val roomParticipants: StateFlow<List<MindcordParticipant>> = _roomParticipants.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    /** Loads last-saved community content into its StateFlows. Live-session state (current room,
     * its messages/participants, and the identity-checked flag) is intentionally not persisted —
     * it isn't meaningful across a restart. */
    suspend fun restore() {
        LocalCache.load<OtherBrainsIdentity>("mv_identity")?.let { _identity.value = it }
        LocalCache.load<List<CommunityMessage>>("mv_messages")?.let { _messages.value = it }
        LocalCache.load<List<CommunitySuggestion>>("mv_suggestions")?.let { _suggestions.value = it }
        LocalCache.load<List<CommunityBook>>("mv_books")?.let { _books.value = it }
        LocalCache.load<List<MindcordDomain>>("mv_domains")?.let { _domains.value = it }
        LocalCache.load<List<OtherBrainsCluster>>("mv_clusters")?.let { _clusters.value = it }
    }

    suspend fun refreshIdentity() {
        ApiClient.getOtherBrainsIdentity()
            .onSuccess {
                _identity.value = it.identity
                _identityChecked.value = true
                LocalCache.save("mv_identity", it.identity)
            }
    }

    suspend fun setDisplayName(name: String): Result<Unit> =
        ApiClient.setOtherBrainsIdentity(name)
            .onSuccess { _identity.value = it.identity; LocalCache.save("mv_identity", it.identity) }
            .onFailure { _error.value = it.message ?: "Couldn't set display name" }
            .map {}

    suspend fun refreshMessages() {
        ApiClient.getCommunityMessages().onSuccess { _messages.value = it.messages; LocalCache.save("mv_messages", it.messages) }
    }

    suspend fun sendMessage(body: String): Result<Unit> =
        ApiClient.postCommunityMessage(body)
            .onSuccess { refreshMessages() }
            .onFailure { _error.value = it.message ?: "Couldn't send" }
            .map {}

    suspend fun refreshSuggestions() {
        ApiClient.getCommunitySuggestions()
            .onSuccess { _suggestions.value = it.suggestions; LocalCache.save("mv_suggestions", it.suggestions) }
    }

    suspend fun sendSuggestion(body: String): Result<Unit> =
        ApiClient.postCommunitySuggestion(body)
            .onSuccess { refreshSuggestions() }
            .onFailure { _error.value = it.message ?: "Couldn't send" }
            .map {}

    suspend fun refreshBooks() {
        ApiClient.getCommunityBooks().onSuccess { _books.value = it.books; LocalCache.save("mv_books", it.books) }
    }

    suspend fun saveBook(title: String, note: String): Result<Unit> =
        ApiClient.postCommunityBook(title, note)
            .onSuccess { refreshBooks() }
            .onFailure { _error.value = it.message ?: "Couldn't save" }
            .map {}

    suspend fun refreshDomains() {
        ApiClient.getMindcordDomains().onSuccess { _domains.value = it.domains; LocalCache.save("mv_domains", it.domains) }
    }

    suspend fun refreshClusters() {
        ApiClient.getOtherBrainsClusters().onSuccess { _clusters.value = it.clusters; LocalCache.save("mv_clusters", it.clusters) }
    }

    suspend fun joinRoom(domain: String): Result<Unit> =
        ApiClient.joinMindcordRoom(domain)
            .onSuccess { _currentRoom.value = it }
            .onFailure { _error.value = it.message ?: "Couldn't join room" }
            .map {}

    suspend fun leaveRoom() {
        val room = _currentRoom.value ?: return
        // Clear local state before the network round-trip, not after: the room screen pops
        // itself the instant currentRoom goes null, so leaving this until after the await
        // would leave the UI hanging on a "Leave" tap for a full request round-trip.
        _currentRoom.value = null
        _roomMessages.value = emptyList()
        _roomParticipants.value = emptyList()
        ApiClient.leaveMindcordRoom(room.roomId)
    }

    suspend fun refreshRoomMessages() {
        val room = _currentRoom.value ?: return
        ApiClient.getMindcordMessages(room.roomId).onSuccess { _roomMessages.value = it.messages }
    }

    suspend fun refreshRoomParticipants() {
        val room = _currentRoom.value ?: return
        ApiClient.getMindcordParticipants(room.roomId).onSuccess { _roomParticipants.value = it.participants }
    }

    suspend fun sendRoomMessage(body: String): Result<Unit> {
        val room = _currentRoom.value ?: return Result.failure(IllegalStateException("Not in a room"))
        return ApiClient.sendMindcordMessage(room.roomId, body)
            .onSuccess { refreshRoomMessages() }
            .onFailure { _error.value = it.message ?: "Couldn't send" }
            .map {}
    }

    suspend fun sendHeartbeat() {
        val room = _currentRoom.value ?: return
        ApiClient.postMindcordHeartbeat(room.roomId)
    }

    private var realtime: MindcordRealtime? = null

    /** Subscribes to Supabase Realtime for this room so new messages/participant changes push in
     * live instead of waiting for the room screen's fallback poll. Safe to call repeatedly — each
     * call tears down any previous subscription first (matters if the caller's roomId key
     * changes without an intervening [disconnectRealtime], e.g. leave+rejoin in one recomposition). */
    fun connectRealtime(roomId: String) {
        realtime = MindcordRealtime { table, eventType, newRow ->
            when (table) {
                "mindcord_messages" -> {
                    if (eventType == "INSERT" && newRow != null) {
                        runCatching { ApiClient.json.decodeFromJsonElement(MindcordMessage.serializer(), newRow) }
                            .onSuccess { message ->
                                if (_roomMessages.value.none { it.id == message.id }) {
                                    _roomMessages.value = _roomMessages.value + message
                                }
                            }
                    }
                }
                "mindcord_participants" -> scope.launch { refreshRoomParticipants() }
            }
        }.also { it.connect(roomId) }
    }

    fun disconnectRealtime() {
        realtime?.disconnect()
        realtime = null
    }
}
