package com.pressureagent.mobile.ui.profile

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

data class ProfileUiState(
    val profile: Profile? = null,
    val wearable: Wearable? = null,
    val hasReviewData: Boolean = false,
    val reviewSummary: String = "",
    val completedActions: List<Action> = emptyList(),
    val completedOrders: List<ServiceOrder> = emptyList(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                val completed = ws.actions.filter { it.status == ActionStatus.COMPLETED }
                val completedOrders = ws.serviceOrders.filter { it.status == ServiceOrderStatus.SUBMITTED }
                _uiState.update {
                    it.copy(
                        profile = ws.profile,
                        wearable = ws.wearable,
                        hasReviewData = ws.actionLedger.isNotEmpty() || completed.isNotEmpty() || completedOrders.isNotEmpty(),
                        reviewSummary = buildReviewSummary(completed, completedOrders),
                        completedActions = completed,
                        completedOrders = completedOrders,
                    )
                }
            }
        }
    }

    private fun buildReviewSummary(actions: List<Action>, orders: List<ServiceOrder>): String {
        val parts = mutableListOf<String>()
        if (actions.isNotEmpty()) parts.add("${actions.size} 项操作")
        if (orders.isNotEmpty()) parts.add("${orders.size} 笔订单")
        return if (parts.isEmpty()) "暂无复盘数据" else parts.joinToString(" · ")
    }
}
