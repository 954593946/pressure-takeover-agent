package com.pressureagent.mobile.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring-buffer logger + file persistence for in-app debugging.
 */
object AppLogger {

    enum class Level(val emoji: String, val label: String) {
        DEBUG("🔍", "DEBUG"),
        INFO("ℹ️", "INFO"),
        WARN("⚠️", "WARN"),
        ERROR("❌", "ERROR"),
    }

    data class Entry(
        val id: Long,
        val timestamp: String,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private const val MAX_ENTRIES = 500
    private const val LOG_FILE = "auri_app.log"
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var counter: Long = 0
    private var logFile: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _logs = MutableStateFlow<List<Entry>>(emptyList())
    val logs: StateFlow<List<Entry>> = _logs.asStateFlow()

    /** Initialise file logging. Call once from Application.onCreate(). */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE)
        // Keep last 256 KB of log file
        if ((logFile?.length() ?: 0) > 256_000) {
            logFile?.delete()
        }
        i("AppLogger", "日志系统已初始化，文件: ${logFile?.absolutePath}")
    }

    @Synchronized
    private fun log(level: Level, tag: String, message: String) {
        val entry = Entry(
            id = ++counter,
            timestamp = fmt.format(Date()),
            level = level,
            tag = tag,
            message = message,
        )
        // In-memory ring buffer
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_ENTRIES) {
            current.removeAt(0)
        }
        _logs.value = current

        // Logcat
        val line = "[${entry.timestamp}] ${level.label}/$tag: $message"
        when (level) {
            Level.DEBUG -> android.util.Log.d("AURI", line)
            Level.INFO -> android.util.Log.i("AURI", line)
            Level.WARN -> android.util.Log.w("AURI", line)
            Level.ERROR -> android.util.Log.e("AURI", line)
        }

        // File (async)
        scope.launch {
            try {
                logFile?.appendText(line + "\n")
            } catch (_: Exception) { /* file write is best-effort */ }
        }
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$message: ${throwable.javaClass.simpleName}: ${throwable.message}\n${sw}"
        } else message
        log(Level.ERROR, tag, msg)
    }

    fun clear() {
        _logs.value = emptyList()
        counter = 0
        logFile?.delete()
    }

    /** Read logcat lines (last N lines matching our tag). */
    fun readLogcatLines(maxLines: Int = 200): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", maxLines.toString(), "AURI:*", "*:S"))
            process.inputStream.bufferedReader().readLines()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Get the log file path. */
    fun logFilePath(): String = logFile?.absolutePath ?: "(未初始化)"
}
