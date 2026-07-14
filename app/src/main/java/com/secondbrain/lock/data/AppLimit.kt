package com.secondbrain.lock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean = true,
    /** Epoch day (LocalDate.toEpochDay) on which the 90%-used warning was last shown, to avoid repeats. */
    val lastWarnedEpochDay: Long? = null
)
