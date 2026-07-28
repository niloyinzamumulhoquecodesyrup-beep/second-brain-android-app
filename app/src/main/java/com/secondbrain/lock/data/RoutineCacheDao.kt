package com.secondbrain.lock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineCacheDao {
    @Query("SELECT * FROM routine_cache ORDER BY startMin ASC")
    fun observeAll(): Flow<List<RoutineCache>>

    @Query("SELECT * FROM routine_cache ORDER BY startMin ASC")
    suspend fun getAll(): List<RoutineCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<RoutineCache>)

    @Query("DELETE FROM routine_cache")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(routines: List<RoutineCache>) {
        clear()
        insertAll(routines)
    }
}
