package com.pressureagent.mobile.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.local.LocalTaskStore
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val tasks: List<Task> = emptyList(),
    val tasksOnSelectedDate: List<Task> = emptyList(),
    val unscheduledTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: WorldStateRepository,
    private val localTasks: LocalTaskStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CalendarUiState(selectedDate = LocalDate.now())
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        observeTasks()
        refresh()
    }

    private fun observeTasks() {
        viewModelScope.launch {
            try {
                combine(
                    repository.worldState,
                    localTasks.tasks,
                ) { ws, locals ->
                    // Merge backend + local tasks, dedup by ID
                    val merged = (ws.tasks + locals).distinctBy { it.taskId }
                    merged
                }.collect { merged ->
                    _uiState.update {
                        it.copy(
                            tasks = merged,
                            tasksOnSelectedDate = filterTasksForDate(merged, it.selectedDate),
                            unscheduledTasks = merged.filter { t -> t.scheduledAt == null },
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "状态同步异常: ${e.message}") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try { repository.refresh() }
            catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun previousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                tasksOnSelectedDate = filterTasksForDate(it.tasks, date),
            )
        }
    }

    fun removeTask(taskId: String) { localTasks.removeTask(taskId) }

    fun goToToday() {
        val today = LocalDate.now()
        _uiState.update {
            it.copy(
                currentMonth = YearMonth.from(today),
                selectedDate = today,
                tasksOnSelectedDate = filterTasksForDate(it.tasks, today),
            )
        }
    }

    private fun filterTasksForDate(tasks: List<Task>, date: LocalDate): List<Task> {
        val dateStr = date.toString() // "2026-07-16"
        return tasks.filter { it.scheduledAt?.startsWith(dateStr) == true }
    }
}
