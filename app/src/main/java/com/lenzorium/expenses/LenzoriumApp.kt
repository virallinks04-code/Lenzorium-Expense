package com.lenzorium.expenses

import android.app.Application
import com.lenzorium.expenses.data.AppDatabase
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SettingsStore

class LenzoriumApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.create(this)
        repository = Repository(this, db, SettingsStore(this))
        com.lenzorium.expenses.notify.Notifier.createChannels(this)
        runCatching { com.lenzorium.expenses.notify.Notifier.schedule(this) }
    }
}
