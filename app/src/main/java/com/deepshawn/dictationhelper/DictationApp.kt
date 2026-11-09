package com.deepshawn.dictationhelper

import android.app.Application
import com.deepshawn.dictationhelper.data.SettingsStore
import com.deepshawn.dictationhelper.data.WordRepository

class DictationApp : Application() {
    lateinit var repository: WordRepository
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = WordRepository(this)
        settings = SettingsStore(this)
    }
}
