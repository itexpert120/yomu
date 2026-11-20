package com.itexpert120.yomu

import android.app.Application
import com.itexpert120.yomu.data.diagnostics.CrashLog
import com.itexpert120.yomu.data.sync.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YomuApplication : Application() {
    @Inject lateinit var sync: SyncRepository

    @Inject lateinit var crashLog: CrashLog

    override fun onCreate() {
        super.onCreate()
        crashLog.install(this)
        // Resumes sync if this device has a phrase; does nothing otherwise.
        sync.start()
    }
}
