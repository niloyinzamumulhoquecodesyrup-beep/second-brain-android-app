package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Other Brains: cross-account, anonymous community feed ----

@Serializable
data class OtherBrainsIdentity(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = "",
    @SerialName("user_id") val userId: String? = null
)

@Serializable
data class OtherBrainsIdentityResponse(val identity: OtherBrainsIdentity? = null)

@Serializable
data class SetDisplayNameRequest(@SerialName("display_name") val displayName: String)

@Serializable
data class CommunityMessage(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunityMessagesResponse(val messages: List<CommunityMessage> = emptyList())

@Serializable
data class CommunityMessageResponse(val message: CommunityMessage? = null)

@Serializable
data class PostBodyRequest(val body: String)

@Serializable
data class CommunitySuggestion(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunitySuggestionsResponse(val suggestions: List<CommunitySuggestion> = emptyList())

@Serializable
data class CommunitySuggestionResponse(val suggestion: CommunitySuggestion? = null)

@Serializable
data class CommunityBook(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = "",
    val title: String = "",
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CommunityBooksResponse(val books: List<CommunityBook> = emptyList())

@Serializable
data class CommunityBookResponse(val book: CommunityBook? = null)

@Serializable
data class PostBookRequest(val title: String, val note: String = "")

// ---- Mindcord: live, room-based chat ----

@Serializable
data class MindcordLiveParticipant(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = ""
)

@Serializable
data class MindcordLiveRoom(val count: Int = 0, val participants: List<MindcordLiveParticipant> = emptyList())

/** One row per domain that has either library entries or an open room. */
@Serializable
data class MindcordDomain(
    val domain: String = "",
    val brains: Int = 0,
    val live: MindcordLiveRoom? = null
)

@Serializable
data class MindcordDomainsResponse(val domains: List<MindcordDomain> = emptyList())

@Serializable
data class JoinRoomRequest(val domain: String)

@Serializable
data class JoinRoomResponse(@SerialName("room_id") val roomId: String = "", val domain: String = "")

@Serializable
data class LeaveRoomRequest(@SerialName("room_id") val roomId: String)

@Serializable
data class MindcordParticipant(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = ""
)

@Serializable
data class MindcordParticipantsResponse(val participants: List<MindcordParticipant> = emptyList())

/** file_* fields exist server-side for uploads — not surfaced yet in the native client. */
@Serializable
data class MindcordMessage(
    val id: String = "",
    @SerialName("room_id") val roomId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_key") val avatarKey: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MindcordMessagesResponse(val messages: List<MindcordMessage> = emptyList())

@Serializable
data class MindcordMessageResponse(val message: MindcordMessage? = null)

@Serializable
data class SendRoomMessageRequest(@SerialName("room_id") val roomId: String, val body: String)

@Serializable
data class HeartbeatRequest(@SerialName("room_id") val roomId: String)

/** GET /api/other-brains/clusters — domain + distinct-account headcount, aggregate-only. */
@Serializable
data class OtherBrainsCluster(val domain: String, val brains: Int = 0)

@Serializable
data class OtherBrainsClustersResponse(val clusters: List<OtherBrainsCluster> = emptyList())
