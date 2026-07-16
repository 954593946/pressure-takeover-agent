package com.pressureagent.mobile.ui.debug

import androidx.lifecycle.ViewModel
import com.pressureagent.mobile.data.mock.MockAgent
import com.pressureagent.mobile.data.mock.StoryScript
import com.pressureagent.mobile.data.repository.WorldStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DebugUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = StoryScript.STEP_COUNT,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val mockAgent: MockAgent?,
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    val isAvailable: Boolean get() = mockAgent != null

    fun advance() {
        val agent = mockAgent ?: return
        agent.jumpTo((agent.currentStep + 1) % StoryScript.STEP_COUNT)
        _uiState.value = _uiState.value.copy(currentStep = agent.currentStep)
    }

    fun reset() {
        val agent = mockAgent ?: return
        agent.reset()
        _uiState.value = _uiState.value.copy(currentStep = 0)
    }

    fun jumpTo(step: Int) {
        val agent = mockAgent ?: return
        agent.jumpTo(step)
        _uiState.value = _uiState.value.copy(currentStep = agent.currentStep)
    }
}
