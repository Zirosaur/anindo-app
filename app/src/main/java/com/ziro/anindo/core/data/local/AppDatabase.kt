package com.ziro.anindo.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ziro.anindo.core.data.local.dao.AnimeDao
import com.ziro.anindo.core.data.local.dao.DownloadDao
import com.ziro.anindo.core.data.local.dao.EpisodeProgressDao
import com.ziro.anindo.core.data.local.entity.AnimeEntity
import com.ziro.anindo.core.data.local.entity.DownloadEntity
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity

@Database(
    entities = [
        AnimeEntity::class,
        EpisodeProgressEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun episodeProgressDao(): EpisodeProgressDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anindo.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
