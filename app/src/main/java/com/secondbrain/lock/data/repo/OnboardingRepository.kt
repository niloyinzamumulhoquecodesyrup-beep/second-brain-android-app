package com.secondbrain.lock.data.repo

import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CompleteOnboardingRequest
import com.secondbrain.lock.network.dto.OkResponse
import com.secondbrain.lock.network.dto.OnboardingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OnboardingRepository {
    private val _status = MutableStateFlow<OnboardingStatus?>(null)
    val status: StateFlow<OnboardingStatus?> = _status.asStateFlow()

    suspend fun refresh() {
        ApiClient.getTyped<OnboardingStatus>("/api/onboarding/status").onSuccess { _status.value = it }
    }

    suspend fun complete(displayName: String, age: Int?, persona: String): Result<Unit> =
        ApiClient.postTyped<CompleteOnboardingRequest, OkResponse>(
            "/api/onboarding/complete",
            CompleteOnboardingRequest(displayName = displayName, age = age, persona = persona)
        ).onSuccess { refresh() }.map {}
}
