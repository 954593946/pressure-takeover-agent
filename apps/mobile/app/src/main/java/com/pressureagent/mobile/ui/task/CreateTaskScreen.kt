package com.pressureagent.mobile.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.Priority
import com.pressureagent.mobile.domain.model.TaskType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Auto-navigate on success
    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) {
            viewModel.onNavigatedAfterSuccess()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建任务") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Title ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("任务标题") },
                isError = state.titleError != null,
                supportingText = state.titleError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ─── Location ───────────────────────────────────────────────────
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("地点（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ─── Scheduled time ─────────────────────────────────────────────
            var showDatePicker by remember { mutableStateOf(false) }
            var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
            var showTimePicker by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.scheduledAtDisplay.isNotEmpty()) state.scheduledAtDisplay
                    else "选择日期和时间（可选）"
                )
            }

            // Date picker
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedDateMillis = datePickerState.selectedDateMillis
                            showDatePicker = false
                            showTimePicker = true
                        }) { Text("下一步") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Time picker
            if (showTimePicker) {
                val timePickerState = rememberTimePickerState()
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val dateMillis = selectedDateMillis ?: System.currentTimeMillis()
                            val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            val time = LocalDateTime.of(date.year, date.month, date.dayOfMonth, timePickerState.hour, timePickerState.minute)
                            val zoned = ZonedDateTime.of(time, ZoneId.systemDefault())
                            val iso = zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            val display = zoned.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
                            viewModel.onScheduledAtSelected(iso, display)
                            showTimePicker = false
                        }) { Text("确定") }
                    },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
                    title = { Text("选择时间") },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TimePicker(state = timePickerState)
                        }
                    },
                )
            }

            // ─── Task type ──────────────────────────────────────────────────
            Text("任务类型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.taskType == TaskType.RIGID,
                    onClick = { viewModel.onTaskTypeSelected(TaskType.RIGID) },
                    label = { Text("刚性") },
                )
                FilterChip(
                    selected = state.taskType == TaskType.FLEXIBLE,
                    onClick = { viewModel.onTaskTypeSelected(TaskType.FLEXIBLE) },
                    label = { Text("弹性") },
                )
            }

            // ─── Priority ────────────────────────────────────────────────────
            Text("优先级", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(
                        selected = state.priority == p,
                        onClick = { viewModel.onPrioritySelected(p) },
                        label = {
                            Text(
                                when (p) {
                                    Priority.HIGH -> "高"
                                    Priority.MEDIUM -> "中"
                                    Priority.LOW -> "低"
                                }
                            )
                        },
                    )
                }
            }

            // ─── Waiting parties ─────────────────────────────────────────────
            Text("等待方（可选）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("老师", "家人", "同事").forEach { party ->
                    FilterChip(
                        selected = state.waitingParties.contains(party),
                        onClick = { viewModel.onWaitingPartyToggle(party) },
                        label = { Text(party) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Submit ──────────────────────────────────────────────────────
            Button(
                onClick = viewModel::onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isSubmitting) "创建中…" else "创建任务")
            }

            // ─── Error ───────────────────────────────────────────────────────
            state.error?.let {
                Snackbar(modifier = Modifier.fillMaxWidth()) {
                    Text(it)
                }
            }
        }
    }
}
