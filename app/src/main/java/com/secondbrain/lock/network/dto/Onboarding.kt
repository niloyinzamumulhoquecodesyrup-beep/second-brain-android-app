package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/onboarding/status */
@Serializable
data class OnboardingStatus(
    val onboarded: Boolean = false,
    @SerialName("display_name") val displayName: String? = null,
    val age: Int? = null,
    val persona: String? = null
)

/** POST /api/onboarding/complete — `imports` (bulk paste-in from other tools) isn't supported natively yet. */
@Serializable
data class CompleteOnboardingRequest(
    @SerialName("display_name") val displayName: String,
    val age: Int? = null,
    val persona: String
)

val PERSONA_OPTIONS = listOf(
    "Tech-savvy",
    "Creative / artistic",
    "Student",
    "Business / entrepreneur",
    "Researcher / academic",
    "Just getting organized",
    "Focus & follow-through support"
)
