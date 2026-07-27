package com.pressureagent.mobile.data.local

import android.content.Context
import com.pressureagent.mobile.domain.model.Priority
import com.pressureagent.mobile.domain.model.Task
import com.pressureagent.mobile.domain.model.TaskStatus
import com.pressureagent.mobile.domain.model.TaskType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTaskStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "local_tasks.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        // Tiny file — sync load is fine, prevents race with addTask()
        try {
            if (file.exists()) {
                val content = file.readText()
                _tasks.value = json.decodeFromString(content)
            }
        } catch (_: Exception) {
            // Corrupted file — start fresh
        }
    }

    fun addTask(title: String, scheduledAtIso: String?) {
        val task = Task(
            taskId = "local_${UUID.randomUUID().toString().take(8)}",
            title = title,
            scheduledAt = scheduledAtIso,
            taskType = TaskType.FLEXIBLE,
            priority = Priority.MEDIUM,
            adjustable = true,
            status = TaskStatus.PENDING,
        )
        _tasks.update { it + task }
        saveToDisk()
    }

    fun removeTask(taskId: String) {
        _tasks.update { list -> list.filter { it.taskId != taskId } }
        saveToDisk()
    }

    private fun saveToDisk() {
        val current = _tasks.value
        scope.launch {
            try {
                file.writeText(json.encodeToString(current))
            } catch (_: Exception) {
                // Disk full or other I/O error — data stays in memory
            }
        }
    }
}
