package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.MindCyclesResponse
import com.secondbrain.lock.network.dto.MindInsightsResponse
import com.secondbrain.lock.network.dto.MindLibraryEntry
import com.secondbrain.lock.network.dto.MindLibraryResponse
import com.secondbrain.lock.network.dto.MindSection
import com.secondbrain.lock.network.dto.MindSectionsResponse
import com.secondbrain.lock.network.dto.MindTopic
import com.secondbrain.lock.network.dto.MindTopicsResponse
import com.secondbrain.lock.network.dto.OkResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backs Organize's InsightCards (insights only) and the full Mind tab. */
object MindRepository {
    private val _insights = MutableStateFlow<MindInsightsResponse?>(null)
    val insights: StateFlow<MindInsightsResponse?> = _insights.asStateFlow()

    private val _sections = MutableStateFlow<List<MindSection>>(emptyList())
    val sections: StateFlow<List<MindSection>> = _sections.asStateFlow()

    private val _cycles = MutableStateFlow<MindCyclesResponse?>(null)
    val cycles: StateFlow<MindCyclesResponse?> = _cycles.asStateFlow()

    private val _topics = MutableStateFlow<List<MindTopic>>(emptyList())
    val topics: StateFlow<List<MindTopic>> = _topics.asStateFlow()

    private val _library = MutableStateFlow<List<MindLibraryEntry>>(emptyList())
    val library: StateFlow<List<MindLibraryEntry>> = _library.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Loads the last-saved data into every StateFlow above so the Mind tab has something to
     * show the instant the app opens, before the network refreshes below land. */
    suspend fun restore() {
        LocalCache.load<MindInsightsResponse>("mind_insights")?.let { _insights.value = it }
        LocalCache.load<List<MindSection>>("mind_sections")?.let { _sections.value = it }
        LocalCache.load<MindCyclesResponse>("mind_cycles")?.let { _cycles.value = it }
        LocalCache.load<List<MindTopic>>("mind_topics")?.let { _topics.value = it }
        LocalCache.load<List<MindLibraryEntry>>("mind_library")?.let { _library.value = it }
    }

    suspend fun refreshInsights() {
        ApiClient.getTyped<MindInsightsResponse>("/api/mind/insights")
            .onSuccess { _insights.value = it; _error.value = null; LocalCache.save("mind_insights", it) }
            .onFailure { _error.value = it.message ?: "Couldn't load insights" }
    }

    suspend fun refreshSections() {
        ApiClient.getTyped<MindSectionsResponse>("/api/mind/sections")
            .onSuccess { _sections.value = it.sections; LocalCache.save("mind_sections", it.sections) }
    }

    suspend fun refreshCycles() {
        ApiClient.getTyped<MindCyclesResponse>("/api/mind/cycles")
            .onSuccess { _cycles.value = it; LocalCache.save("mind_cycles", it) }
    }

    suspend fun refreshTopics() {
        ApiClient.getTyped<MindTopicsResponse>("/api/mind/topics")
            .onSuccess { _topics.value = it.topics; LocalCache.save("mind_topics", it.topics) }
    }

    suspend fun refreshLibrary() {
        ApiClient.getTyped<MindLibraryResponse>("/api/mind/library")
            .onSuccess { _library.value = it.entries; LocalCache.save("mind_library", it.entries) }
    }

    suspend fun synthesize(): Result<Unit> = ApiClient.postTyped<OkResponse>("/api/mind/synthesize").map {}
}
