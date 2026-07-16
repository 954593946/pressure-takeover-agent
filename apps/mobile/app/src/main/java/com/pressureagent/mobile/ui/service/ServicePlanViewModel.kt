package com.pressureagent.mobile.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.ServiceOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicePlanUiState(val orders: List<ServiceOrder> = emptyList())

@HiltViewModel
class ServicePlanViewModel @Inject constructor(repository: WorldStateRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ServicePlanUiState())
    val uiState: StateFlow<ServicePlanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                _uiState.value = ServicePlanUiState(orders = ws.serviceOrders)
            }
        }
    }
}
