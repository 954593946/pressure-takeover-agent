package com.pressureagent.mobile.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskListUiState(val tasks: List<Task> = emptyList())

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                _uiState.value = TaskListUiState(tasks = ws.tasks)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }
}
