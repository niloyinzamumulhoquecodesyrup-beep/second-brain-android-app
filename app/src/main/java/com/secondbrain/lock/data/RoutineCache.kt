package com.secondbrain.lock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of GET /api/planner/routines, refreshed opportunistically and used offline by
 * both the schedule auto-block feature and the sectograph widget. [days] is a comma-separated
 * list of 0=Mon..6=Sun to match the server's `days int[]` column.
 */
@Entity(tableName = "routine_cache")
data class RoutineCache(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val days: String,
    val startMin: Int,
    val durationMin: Int,
    val active: Boolean
) {
    val dayList: List<Int> get() = days.split(",").mapNotNull { it.trim().toIntOrNull() }

    /** True if [nowMinuteOfDay] on [dayOfWeek] (0=Mon..6=Sun) falls inside this routine's window. */
    fun coversNow(dayOfWeek: Int, nowMinuteOfDay: Int): Boolean {
        if (!active || dayOfWeek !in dayList) return false
        val end = startMin + durationMin
        return if (end <= 1440) {
            nowMinuteOfDay in startMin until end
        } else {
            // Window wraps past midnight (e.g. a sleep block starting at 23:00).
            nowMinuteOfDay >= startMin || nowMinuteOfDay < (end - 1440)
        }
    }
}
