package com.pressureagent.mobile.ui.debug

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

@Composable
fun DebugScreen(viewModel: DebugViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("调试控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (!viewModel.isAvailable) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("调试控制在 mock 模式下可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mock 故事脚本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("当前步骤: ${state.currentStep} / ${state.totalSteps - 1}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f)) { Text("重置") }
                    Button(onClick = viewModel::advance, modifier = Modifier.weight(2f)) { Text("下一步 →") }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "空闲", 1 to "任务", 4 to "驾驶", 5 to "拥堵", 7 to "确认", 8 to "完成").forEach { (step, label) ->
                        val isCurrent = step == state.currentStep
                        SuggestionChip(
                            onClick = { viewModel.jumpTo(step) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                            colors = if (isCurrent) SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ) else SuggestionChipDefaults.suggestionChipColors(),
                        )
                    }
                }
            }
        }
    }
}
