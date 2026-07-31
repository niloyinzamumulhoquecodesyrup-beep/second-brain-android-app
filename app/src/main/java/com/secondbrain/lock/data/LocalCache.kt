package com.secondbrain.lock.data

import android.content.Context
import com.secondbrain.lock.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Local-first disk cache for repository singletons (MindRepository, TasksRepository, etc.): each
 * repository's restore() reads its last-saved fields from here into its StateFlows at app launch,
 * so screens render the last-known-good data immediately (even if stale) instead of a blank state
 * while the network refresh() that follows is still in flight.
 */
object LocalCache {
    @PublishedApi internal lateinit var dao: CacheEntryDao

    fun init(context: Context) {
        dao = AppDatabase.get(context).cacheEntryDao()
    }

    suspend inline fun <reified T> load(id: String): T? = withContext(Dispatchers.IO) {
        dao.get(id)?.let { entry -> runCatching { ApiClient.json.decodeFromString<T>(entry.json) }.getOrNull() }
    }

    suspend inline fun <reified T> save(id: String, value: T) = withContext(Dispatchers.IO) {
        dao.put(CacheEntry(id, ApiClient.json.encodeToString(value)))
    }

    /** Wipes every cached field — called on logout so a different account never sees a flash of
     * the previous one's data on next launch. */
    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.clear() }
}
