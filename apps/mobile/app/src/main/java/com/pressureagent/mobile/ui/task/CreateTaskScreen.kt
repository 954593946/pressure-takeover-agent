package com.pressureagent.mobile.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.ui.theme.AuriIvory
import com.pressureagent.mobile.ui.theme.AuriNavy
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

    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) {
            viewModel.onNavigatedAfterSuccess()
            onNavigateBack()
        }
    }

    Scaffold(
        containerColor = AuriIvory,
        topBar = {
            TopAppBar(
                title = { Text("创建任务") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuriIvory),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "要创建复杂任务？去对话 Tab 直接说就行",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
            Spacer(Modifier.height(16.dp))

            // ─── Title ───────────────────────────────────────────────
            OutlinedTextField(
                value = state.quickTitle,
                onValueChange = viewModel::onQuickTitleChange,
                label = { Text("任务标题") },
                placeholder = { Text("接孩子", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuriNavy,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                ),
            )

            Spacer(Modifier.height(12.dp))

            // ─── Date + Time picker ──────────────────────────────────
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }
            var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.quickTimeDisplay.isNotEmpty()) state.quickTimeDisplay
                    else "选择日期和时间（可选）",
                    color = if (state.quickTimeDisplay.isNotEmpty()) AuriNavy else Color.Gray,
                )
            }

            if (showDatePicker) {
                val dateState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickedDateMillis = dateState.selectedDateMillis
                            showDatePicker = false
                            showTimePicker = true
                        }) { Text("下一步") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
                ) { DatePicker(state = dateState) }
            }

            if (showTimePicker) {
                val timeState = rememberTimePickerState()
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val dateMs = pickedDateMillis ?: System.currentTimeMillis()
                            val date = Instant.ofEpochMilli(dateMs).atZone(ZoneId.systemDefault()).toLocalDate()
                            val dt = LocalDateTime.of(date.year, date.month, date.dayOfMonth, timeState.hour, timeState.minute)
                            val zoned = ZonedDateTime.of(dt, ZoneId.systemDefault())
                            viewModel.onQuickTimeSelected(
                                iso = zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                display = zoned.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")),
                            )
                            showTimePicker = false
                        }) { Text("确定") }
                    },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
                    title = { Text("选择时间") },
                    text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Create button ───────────────────────────────────────
            Button(
                onClick = viewModel::onQuickCreate,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = state.quickTitle.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuriNavy),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("创建任务")
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}
