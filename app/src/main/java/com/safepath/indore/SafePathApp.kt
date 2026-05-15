package com.safepath.indore

import android.app.Application
import android.content.Context

class SafePathApp : Application() {
    companion object {
        private lateinit var instance: SafePathApp
        val context: Context get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
