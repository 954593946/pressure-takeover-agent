package com.pressureagent.mobile.data.local

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Global uncaught exception handler that logs the crash to AppLogger
 * before delegating to the system default handler (which kills the app).
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        if (Thread.getDefaultUncaughtExceptionHandler() is CrashHandler) return
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Log the crash to AppLogger file before dying
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            AppLogger.e(
                "CRASH",
                "线程: ${thread.name}\n类型: ${throwable.javaClass.name}\n消息: ${throwable.message}\n堆栈:\n$sw"
            )
            // Force flush by writing again synchronously
            AppLogger.e("CRASH", "应用即将退出")
        } catch (_: Exception) {
            // Best effort — if logging itself fails, still pass to default handler
        }

        // Delegate to default handler (usually kills the process)
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
