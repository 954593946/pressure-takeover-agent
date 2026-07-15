package com.pressureagent.mobile.ui.wearable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.HeartRateSource
import com.pressureagent.mobile.domain.model.Vibration
import com.pressureagent.mobile.domain.model.WearableState
import com.pressureagent.mobile.ui.common.WearableBar

@Composable
fun WearableScreen(viewModel: WearableViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("腕上设备", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.wearable == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("等待数据…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        state.wearable?.let { WearableBar(wearable = it) }
    }
}
