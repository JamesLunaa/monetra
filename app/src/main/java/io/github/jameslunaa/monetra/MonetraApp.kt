package io.github.jameslunaa.monetra

import android.app.Application
import io.github.jameslunaa.monetra.di.AppContainer

class MonetraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
