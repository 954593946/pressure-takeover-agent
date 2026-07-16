package com.pressureagent.mobile.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.Priority
import com.pressureagent.mobile.domain.model.Task
import com.pressureagent.mobile.domain.model.TaskStatus
import com.pressureagent.mobile.domain.model.TaskType
import com.pressureagent.mobile.ui.theme.*

@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel = hiltViewModel(),
    onNavigateToCreate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = AuriIvory,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = AuriNavy,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "创建任务")
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(16.dp),
        ) {
            Text("今日任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
            Spacer(Modifier.height(12.dp))

            if (state.tasks.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("暂无任务", color = Color.Gray)
                }
                return@Scaffold
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.tasks, key = { it.taskId }) { task ->
                    TaskCard(task = task)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == TaskStatus.COMPLETED) Color(0xFFF0F0F0) else Color.White,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (task.taskType == TaskType.RIGID) AuriCritical.copy(alpha = 0.1f) else AuriSuccess.copy(alpha = 0.1f),
                ) {
                    Text(
                        if (task.taskType == TaskType.RIGID) "刚性" else "弹性",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.taskType == TaskType.RIGID) AuriCritical else AuriSuccess,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (task.location != null) Text("📍 ${task.location}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (task.scheduledAt != null) Text("🕐 ${formatTime(task.scheduledAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (task.waitingParty.isNotEmpty()) {
                Text("等待: ${task.waitingParty.joinToString("、")}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF5F5F5)) {
                    Text(
                        when (task.status) {
                            TaskStatus.PENDING -> "待处理"
                            TaskStatus.RESCHEDULED -> "已顺延"
                            TaskStatus.COMPLETED -> "已完成"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    when (task.priority) { Priority.HIGH -> "高优先"; Priority.MEDIUM -> "中优先"; Priority.LOW -> "低优先" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

private fun formatTime(iso: String): String = try {
    iso.split("T").getOrNull(1)?.substring(0, 5) ?: iso
} catch (_: Exception) { iso }
