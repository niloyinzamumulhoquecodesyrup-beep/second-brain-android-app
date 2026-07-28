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
    val lastWarnedEpochDay: Long? = null,
    /** Optional cap on launches/day, independent of the minute budget. Null = no open-count limit. */
    val openCountLimit: Int? = null,
    /** Hard-locked during any active schedule auto-block window (see RoutineCache), regardless of budget. */
    val blockDuringSchedule: Boolean = false,
    /** Hard-locked while a cross-device focus/Pomodoro session is active. */
    val blockDuringFocus: Boolean = false
)
