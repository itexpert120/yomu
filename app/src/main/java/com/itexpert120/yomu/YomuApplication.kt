package com.itexpert120.yomu

import android.app.Application
import com.itexpert120.yomu.data.sync.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YomuApplication : Application() {
    @Inject lateinit var sync: SyncRepository

    override fun onCreate() {
        super.onCreate()
        // Resumes sync if this device has a phrase; does nothing otherwise.
        sync.start()
    }
}
