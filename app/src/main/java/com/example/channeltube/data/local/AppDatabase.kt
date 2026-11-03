package com.example.channeltube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, VideoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
