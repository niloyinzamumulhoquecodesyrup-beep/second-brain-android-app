package com.secondbrain.lock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheEntryDao {
    @Query("SELECT * FROM cache_entry WHERE id = :id")
    suspend fun get(id: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("DELETE FROM cache_entry")
    suspend fun clear()
}
