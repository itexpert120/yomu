package com.itexpert120.yomu

import android.app.Application
import com.itexpert120.yomu.app.YomuGraph

class YomuApplication : Application() {
    lateinit var graph: YomuGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = YomuGraph(this)
    }
}
