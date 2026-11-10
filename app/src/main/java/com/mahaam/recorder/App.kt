package com.mahaam.recorder

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        Prefs.ensureBaseline(this)
        MonthlyWorker.schedule(this)
    }
}
