package com.pressureagent.mobile.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleUiState(
    // Vehicle info (mock → API /v1/vehicle/status)
    val vehicleInfo: VehicleInfo = VehicleInfo(
        vehicleId = "AURI-DEMO-001",
        modelName = "AURI ONE",
        licensePlate = "京A·00001",
        connected = true,
    ),
    val vehicleStatus: VehicleStatus = VehicleStatus(
        batteryPercent = 72,
        rangeKm = 420,
        totalOdometerKm = 12380,
        isLocked = true,
        cabinTempCelsius = 24.0,
        location = "公司地下车库 B2",
        securityStatus = "normal",
    ),
    // Trips (mock)
    val recentTrips: List<TripSummary> = listOf(
        TripSummary("t1", "2026-07-16", "家", "公司", 28, 12.5),
        TripSummary("t2", "2026-07-15", "公司", "位智小学", 22, 8.3),
        TripSummary("t3", "2026-07-15", "位智小学", "家", 18, 7.1),
        TripSummary("t4", "2026-07-14", "家", "公司", 32, 13.2, hadTakeover = true),
        TripSummary("t5", "2026-07-14", "公司", "家", 26, 11.8),
    ),
    // HMI link (from WorldState)
    val hmiStage: Stage = Stage.OFF_VEHICLE_IDLE,
    val hmiScene: Scene = Scene.OFF_VEHICLE,
    val hmiPrimarySurface: PrimarySurface = PrimarySurface.MOBILE,
    val hmiRisk: Risk = Risk(pressureLevel = PressureLevel.L0),
    val hmiEta: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    init {
        observeWorldState()
        refresh()
    }

    private fun observeWorldState() {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                _uiState.update {
                    it.copy(
                        hmiStage = ws.stage,
                        hmiScene = ws.scene,
                        hmiPrimarySurface = ws.primarySurface,
                        hmiRisk = ws.risk,
                        hmiEta = ws.eta,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.refresh()
                // In future: also fetch from VehicleApiService
                // val vehicleState = vehicleApi.getVehicleState()
                // _uiState.update { it.copy(vehicleInfo = ..., vehicleStatus = ...) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
