package com.midhun.a3dmodelviewer

import android.app.Application
import com.midhun.a3dmodelviewer.di.AppContainer

class ModelViewerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
