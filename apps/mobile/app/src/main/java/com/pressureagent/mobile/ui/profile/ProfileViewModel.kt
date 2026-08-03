package com.pressureagent.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.data.remote.AgentApiService
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.data.wearablegateway.WearableGateway
import com.pressureagent.mobile.data.wearablegateway.WearableGatewaySnapshot
import com.pressureagent.mobile.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: Profile? = null,
    val wearable: Wearable? = null,
    val wearableGateway: WearableGatewaySnapshot = WearableGatewaySnapshot(),
    val hasReviewData: Boolean = false,
    val reviewSummary: String = "",
    val completedActions: List<Action> = emptyList(),
    val completedOrders: List<ServiceOrder> = emptyList(),
    // Sync
    val isSyncing: Boolean = false,
    val syncError: String? = null,
)

/**
 * Stable presets for the two profile types.
 *
 * Profile affects tone, budget, delivery, substitution, and explanation depth.
 * It does NOT affect safety permissions (L0-L3 thresholds, primary surface, confirmation owner).
 */
enum class ProfilePreset(val label: String, val subtitle: String) {
    EFFICIENCY("效率优先", "最快配送 · 同规格替代 · 简洁解释 · 预算 ¥300"),
    QUALITY("品质优先", "品质优先 · 同品牌 · 详细解释 · 预算 ¥500"),
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: WorldStateRepository,
    private val api: AgentApiService,
    private val wearableGateway: WearableGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** Currently selected preset (derived from live Profile). */
    val activePreset: ProfilePreset?
        get() = when (_uiState.value.profile?.profileType) {
            ProfileType.EFFICIENCY -> ProfilePreset.EFFICIENCY
            ProfileType.QUALITY -> ProfilePreset.QUALITY
            null -> null
        }

    init {
        wearableGateway.start()
        viewModelScope.launch {
            try {
                repository.worldState.combine(wearableGateway.state) { ws, gateway ->
                    ws to gateway
                }.collect { (ws, gateway) ->
                    val completed = ws.actions.filter { it.status == ActionStatus.COMPLETED }
                    val completedOrders = ws.serviceOrders.filter { it.status == ServiceOrderStatus.SUBMITTED }
                    _uiState.update {
                        it.copy(
                            profile = ws.profile,
                            wearable = ws.wearable,
                            wearableGateway = gateway,
                            hasReviewData = ws.actionLedger.isNotEmpty() || completed.isNotEmpty() || completedOrders.isNotEmpty(),
                            reviewSummary = buildReviewSummary(completed, completedOrders),
                            completedActions = completed,
                            completedOrders = completedOrders,
                            // Clear sync state when profile arrives from backend
                            isSyncing = false,
                            syncError = null,
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ProfileVM", "WorldState collection error", e)
            }
        }
    }

    /** Build an EFFICIENCY profile with the given profileId. */
    private fun buildEfficiencyProfile(profileId: String) = Profile(
        profileId = profileId,
        profileType = ProfileType.EFFICIENCY,
        tone = "简洁",
        proactiveVoiceThreshold = VoiceThreshold.L2,
        hapticMode = HapticMode.CLEAR,
        budgetLimit = 300.0,
        deliveryPriority = DeliveryPriority.FASTEST,
        substitutionPolicy = SubstitutionPolicy.SAME_SPEC_WITHIN_BUDGET,
        explanationDepth = ExplanationDepth.BRIEF,
    )

    /** Build a QUALITY profile with the given profileId. */
    private fun buildQualityProfile(profileId: String) = Profile(
        profileId = profileId,
        profileType = ProfileType.QUALITY,
        tone = "温和",
        proactiveVoiceThreshold = VoiceThreshold.L1,
        hapticMode = HapticMode.GENTLE,
        budgetLimit = 500.0,
        deliveryPriority = DeliveryPriority.QUALITY_FIRST,
        substitutionPolicy = SubstitutionPolicy.SAME_BRAND_ONLY,
        explanationDepth = ExplanationDepth.DETAILED,
    )

    /** Switch to the given preset and push to backend. */
    fun switchToPreset(preset: ProfilePreset) {
        val currentProfile = _uiState.value.profile
        val profileId = currentProfile?.profileId ?: "default"

        val newProfile = when (preset) {
            ProfilePreset.EFFICIENCY -> buildEfficiencyProfile(profileId)
            ProfilePreset.QUALITY -> buildQualityProfile(profileId)
        }

        // Optimistic UI update
        _uiState.update { it.copy(profile = newProfile, isSyncing = true, syncError = null) }

        viewModelScope.launch {
            try {
                val updated = api.updateProfile(newProfile)
                // Backend confirmed — apply server response
                _uiState.update { it.copy(profile = updated.profile, isSyncing = false) }
                // Refresh world state so other screens pick up the change
                repository.refresh()
                AppLogger.i("ProfileVM", "Profile switched to ${preset.label}")
            } catch (e: Exception) {
                AppLogger.e("ProfileVM", "Profile update failed", e)
                _uiState.update {
                    it.copy(
                        // Revert to previous profile on failure
                        profile = currentProfile,
                        isSyncing = false,
                        syncError = "同步失败: ${e.message}",
                    )
                }
            }
        }
    }

    fun dismissError() { _uiState.update { it.copy(syncError = null) } }

    private fun buildReviewSummary(actions: List<Action>, orders: List<ServiceOrder>): String {
        val parts = mutableListOf<String>()
        if (actions.isNotEmpty()) parts.add("${actions.size} 项操作")
        if (orders.isNotEmpty()) parts.add("${orders.size} 笔订单")
        return if (parts.isEmpty()) "暂无复盘数据" else parts.joinToString(" · ")
    }
}
