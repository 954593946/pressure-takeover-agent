package com.pressureagent.mobile.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Action
import com.pressureagent.mobile.domain.model.ActionStatus
import com.pressureagent.mobile.domain.model.ServiceOrder
import com.pressureagent.mobile.domain.model.ServiceOrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val ledger: List<String> = emptyList(),
    val completedActions: List<Action> = emptyList(),
    val completedOrders: List<ServiceOrder> = emptyList(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(repository: WorldStateRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                _uiState.value = ReviewUiState(
                    ledger = ws.actionLedger,
                    completedActions = ws.actions.filter { it.status == ActionStatus.COMPLETED },
                    completedOrders = ws.serviceOrders.filter { it.status == ServiceOrderStatus.SUBMITTED },
                )
            }
        }
    }
}
