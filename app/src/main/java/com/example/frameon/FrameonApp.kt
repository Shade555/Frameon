
package com.example.frameon

import android.app.Application
import com.example.frameon.worker.SensitiveDataWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FrameonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start the background monitor for sensitive information
        SensitiveDataWorker.scheduleNext(this)
    }
}
