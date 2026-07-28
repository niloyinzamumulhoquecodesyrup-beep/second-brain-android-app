package com.secondbrain.lock.data

import android.content.Context
import com.secondbrain.lock.network.ApiClient
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** Local-first access to planner routines — always reads the cache; [refresh] pulls the latest. */
class RoutineRepository(context: Context) {
    private val dao = AppDatabase.get(context).routineCacheDao()

    fun observeAll(): Flow<List<RoutineCache>> = dao.observeAll()

    suspend fun getCached(): List<RoutineCache> = dao.getAll()

    suspend fun refresh(): Result<Unit> =
        ApiClient.getPlannerRoutines().map { routines -> dao.replaceAll(routines) }

    companion object {
        /** 0=Mon..6=Sun, matching the server's `days int[]` convention. */
        fun currentDayOfWeekIndex(): Int =
            LocalDate.now(ZoneId.systemDefault()).dayOfWeek.value - 1

        fun currentMinuteOfDay(): Int {
            val now = LocalTime.now(ZoneId.systemDefault())
            return now.hour * 60 + now.minute
        }
    }
}
