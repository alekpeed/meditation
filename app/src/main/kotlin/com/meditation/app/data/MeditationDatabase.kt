package com.meditation.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PresetEntity::class,
        ActiveSessionEntity::class,
        HistoryEntity::class,
        SoundEntity::class,
        AttributionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MeditationDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun historyDao(): HistoryDao
    abstract fun soundDao(): SoundDao
    abstract fun attributionDao(): AttributionDao

    companion object {
        @Volatile private var instance: MeditationDatabase? = null

        fun get(context: Context): MeditationDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MeditationDatabase::class.java,
                "meditation.db",
            )
                // New-release migrations are added here as versions increment; a destructive
                // fallback is intentionally NOT used so user history is never silently dropped.
                .build()
                .also { instance = it }
        }
    }
}
