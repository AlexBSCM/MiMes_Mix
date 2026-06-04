package com.mimes.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MiMesApp : Application() {
    companion object {
        lateinit var instance: MiMesApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
