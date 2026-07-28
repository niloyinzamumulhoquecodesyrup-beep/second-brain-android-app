package com.secondbrain.lock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppLimit::class, RoutineCache::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao
    abstract fun routineCacheDao(): RoutineCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "second_brain_lock.db"
                )
                    // Pre-release local config (app limits) — safe to drop and recreate rather
                    // than hand-write column migrations for a schema that's still moving.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
