package com.ziro.anindo

import android.app.Application
import com.ziro.anindo.core.data.local.AppDatabase
import com.ziro.anindo.core.data.repository.AnimeRepository

class AnindoApp : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var repository: AnimeRepository
        private set

    lateinit var downloadManager: com.ziro.anindo.core.download.AppDownloadManager
        private set

    lateinit var settingsManager: com.ziro.anindo.core.data.settings.SettingsManager
        private set

    companion object {
        lateinit var instance: AnindoApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        repository = AnimeRepository(database)
        downloadManager = com.ziro.anindo.core.download.AppDownloadManager(this)
        settingsManager = com.ziro.anindo.core.data.settings.SettingsManager(this)
    }
}


