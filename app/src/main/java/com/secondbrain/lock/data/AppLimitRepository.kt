package com.secondbrain.lock.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AppLimitRepository(context: Context) {
    private val dao = AppDatabase.get(context).appLimitDao()
    private val appContext = context.applicationContext

    fun observeAll(): Flow<List<AppLimit>> = dao.observeAll()

    suspend fun getEnabled(): List<AppLimit> = dao.getEnabled()

    suspend fun getLimit(packageName: String): AppLimit? = dao.getByPackage(packageName)

    suspend fun add(packageName: String, appName: String, dailyLimitMinutes: Int) {
        dao.upsert(AppLimit(packageName, appName, dailyLimitMinutes))
    }

    suspend fun setEnabled(limit: AppLimit, enabled: Boolean) {
        dao.update(limit.copy(enabled = enabled))
    }

    suspend fun updateLimit(limit: AppLimit, dailyLimitMinutes: Int) {
        dao.update(limit.copy(dailyLimitMinutes = dailyLimitMinutes, lastWarnedEpochDay = null))
    }

    suspend fun remove(limit: AppLimit) = dao.delete(limit)

    suspend fun markWarned(limit: AppLimit) {
        dao.update(limit.copy(lastWarnedEpochDay = UsageStatsHelper.todayEpochDay()))
    }

    fun todaysUsageMillis(packageName: String): Long =
        UsageStatsHelper.todaysUsageMillis(appContext, packageName)
}
