package com.secondbrain.lock.data.repo

import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the logged-in user's name/avatar-presence, backing the top bar's account button. */
object ProfileRepository {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    suspend fun refresh() {
        ApiClient.getTyped<Profile>("/api/auth/me").onSuccess { _profile.value = it }
    }

    fun clear() {
        _profile.value = null
    }
}
