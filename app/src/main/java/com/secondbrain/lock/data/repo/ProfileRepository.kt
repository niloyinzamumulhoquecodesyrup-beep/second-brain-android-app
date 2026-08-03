package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the logged-in user's name/avatar-presence, backing the top bar's account button. */
object ProfileRepository {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    // GET /api/auth/avatar is a static path with no server-side version/etag, so an image loader
    // keyed purely on that URL (Coil, or any HTTP cache) keeps serving the bytes it fetched the
    // first time even after a new photo is uploaded. [bumpAvatarVersion] lets a caller append it
    // as a `?v=` query param to force a real refetch — but it must NOT happen inside [refresh]
    // itself: TopBar mounts (and calls refresh) fresh on every tab switch, so bumping here made
    // the avatar's URL churn on every navigation, not just on an actual upload, which showed up
    // as the photo flickering back to initials every time you switched tabs.
    private val _avatarVersion = MutableStateFlow(0L)
    val avatarVersion: StateFlow<Long> = _avatarVersion.asStateFlow()

    suspend fun restore() {
        LocalCache.load<Profile>("profile")?.let { _profile.value = it }
    }

    suspend fun refresh() {
        ApiClient.getTyped<Profile>("/api/auth/me").onSuccess {
            _profile.value = it
            LocalCache.save("profile", it)
        }
    }

    /** Call after a successful avatar upload — see [_avatarVersion] for why this is separate
     * from [refresh]. */
    fun bumpAvatarVersion() {
        _avatarVersion.value = System.currentTimeMillis()
    }

    fun clear() {
        _profile.value = null
    }
}
