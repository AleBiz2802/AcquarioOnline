package com.acquarioonline

import android.app.Application
import com.acquarioonline.di.AppContainer

class AcquarioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
