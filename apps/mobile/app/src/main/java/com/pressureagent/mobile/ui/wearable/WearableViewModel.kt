package com.pressureagent.mobile.ui.wearable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearableUiState(val wearable: Wearable? = null)

@HiltViewModel
class WearableViewModel @Inject constructor(repository: WorldStateRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(WearableUiState())
    val uiState: StateFlow<WearableUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                _uiState.value = WearableUiState(wearable = ws.wearable)
            }
        }
    }
}
