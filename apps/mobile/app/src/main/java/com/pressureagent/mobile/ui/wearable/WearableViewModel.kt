package com.pressureagent.mobile.ui.wearable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.data.wearablegateway.WearableGateway
import com.pressureagent.mobile.data.wearablegateway.WearableGatewaySnapshot
import com.pressureagent.mobile.domain.model.HapticPattern
import com.pressureagent.mobile.domain.model.PrimarySurface
import com.pressureagent.mobile.domain.model.Stage
import com.pressureagent.mobile.domain.model.Wearable
import com.pressureagent.mobile.domain.model.WearableMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearableUiState(
    val wearable: Wearable? = null,
    val stage: Stage? = null,
    val revision: Int = 0,
    val primarySurface: PrimarySurface? = null,
    val gateway: WearableGatewaySnapshot = WearableGatewaySnapshot(),
)

@HiltViewModel
class WearableViewModel @Inject constructor(
    repository: WorldStateRepository,
    private val wearableGateway: WearableGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WearableUiState())
    val uiState: StateFlow<WearableUiState> = _uiState.asStateFlow()

    init {
        wearableGateway.start()
        viewModelScope.launch {
            repository.worldState.combine(wearableGateway.state) { ws, gateway ->
                WearableUiState(
                    wearable = ws.wearable,
                    stage = ws.stage,
                    revision = ws.revision,
                    primarySurface = ws.primarySurface,
                    gateway = gateway,
                )
            }.collect { next ->
                _uiState.value = next
            }
        }
    }

    fun requestHealthSnapshot() {
        wearableGateway.requestHealthSnapshot()
    }

    fun sendDebugState(mode: WearableMode) {
        wearableGateway.sendDebugState(mode)
    }

    fun sendDebugHaptic(haptic: HapticPattern) {
        val currentMode = _uiState.value.wearable?.mode ?: WearableMode.IDLE
        wearableGateway.sendDebugHaptic(haptic = haptic, mode = currentMode)
    }
}
