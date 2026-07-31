package com.secondbrain.lock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Generic local-first cache of a backend response, keyed by a fixed per-field id (e.g.
 * "mind_insights"). Lets repositories show the last-known-good data immediately on app launch,
 * even if stale, instead of a blank/loading screen — then a normal refresh() overwrites it.
 */
@Entity(tableName = "cache_entry")
data class CacheEntry(
    @PrimaryKey val id: String,
    val json: String
)
