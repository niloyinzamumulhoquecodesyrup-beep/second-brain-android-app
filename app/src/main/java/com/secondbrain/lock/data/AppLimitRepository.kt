package com.secondbrain.lock.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AppLimitRepository(context: Context) {
    private val dao = AppDatabase.get(context).appLimitDao()
    private val appContext = context.applicationContext

    fun observeAll(): Flow<List<AppLimit>> = dao.observeAll()

    suspend fun getEnabled(): List<AppLimit> = dao.getEnabled()

    suspend fun getLimit(packageName: String): AppLimit? = dao.getByPackage(packageName)

    suspend fun add(packageName: String, appName: String, dailyLimitMinutes: Int, openCountLimit: Int? = null) {
        dao.upsert(AppLimit(packageName, appName, dailyLimitMinutes, openCountLimit = openCountLimit))
    }

    suspend fun setEnabled(limit: AppLimit, enabled: Boolean) {
        dao.update(limit.copy(enabled = enabled))
    }

    suspend fun updateLimit(limit: AppLimit, dailyLimitMinutes: Int, openCountLimit: Int?) {
        dao.update(
            limit.copy(
                dailyLimitMinutes = dailyLimitMinutes,
                openCountLimit = openCountLimit,
                lastWarnedEpochDay = null
            )
        )
    }

    suspend fun setBlockDuringSchedule(limit: AppLimit, enabled: Boolean) {
        dao.update(limit.copy(blockDuringSchedule = enabled))
    }

    suspend fun setBlockDuringFocus(limit: AppLimit, enabled: Boolean) {
        dao.update(limit.copy(blockDuringFocus = enabled))
    }

    suspend fun remove(limit: AppLimit) = dao.delete(limit)

    suspend fun markWarned(limit: AppLimit) {
        dao.update(limit.copy(lastWarnedEpochDay = UsageStatsHelper.todayEpochDay()))
    }

    fun todaysUsageMillis(packageName: String): Long =
        UsageStatsHelper.todaysUsageMillis(appContext, packageName)

    fun todaysOpenCount(packageName: String): Int =
        UsageStatsHelper.todaysOpenCount(appContext, packageName)
}
