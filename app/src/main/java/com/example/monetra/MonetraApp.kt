package com.example.monetra

import android.app.Application
import com.example.monetra.di.AppContainer

class MonetraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
