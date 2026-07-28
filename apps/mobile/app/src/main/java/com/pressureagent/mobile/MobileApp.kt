package com.pressureagent.mobile

import android.app.Application
import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.data.local.CrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install()
        AppLogger.init(this)
        AppLogger.i("MobileApp", "App 启动, USE_MOCK=${BuildConfig.USE_MOCK_AGENT}, BASE_URL=${BuildConfig.AGENT_API_BASE_URL}")
    }
}
