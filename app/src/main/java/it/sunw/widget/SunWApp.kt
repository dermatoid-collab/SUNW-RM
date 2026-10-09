package it.sunw.widget

import android.app.Application

class SunWApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
