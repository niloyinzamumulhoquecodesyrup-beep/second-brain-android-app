package com.secondbrain.lock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReminderStateDao {
    @Query("SELECT * FROM reminder_state WHERE reminderId = :reminderId LIMIT 1")
    suspend fun get(reminderId: String): ReminderState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReminderState)

    @Query("DELETE FROM reminder_state WHERE reminderId = :reminderId")
    suspend fun clear(reminderId: String)
}
