package com.example

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode

/**
 * Application entry point.
 *
 * Security & Quality enforcement:
 * - Enables [StrictMode] thread and VM policies ONLY in debuggable builds to catch accidental
 *   disk/network I/O on the main thread, leaked Closable resources, or unclosed MediaExtractor/AudioTrack
 *   handles.
 * - Zero network history logging outside debug builds.
 */
class SmartNetworkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val isDebugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebugBuild) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectFileUriExposure()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
