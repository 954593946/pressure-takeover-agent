package com.pressureagent.mobile.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onNavigateToCreate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    // 每次进入日程 Tab 自动回到今天
    LaunchedEffect(Unit) { viewModel.goToToday() }

    // Calendar expand/collapse — must be at @Composable scope, not inside LazyColumn
    var isExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AuriIvory,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = AuriNavy,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "创建任务") }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Month header ──────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月", tint = AuriNavy)
                    }
                    Text(
                        "${state.currentMonth.year}年 ${state.currentMonth.monthValue}月",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AuriNavy,
                    )
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月", tint = AuriNavy)
                    }
                }
            }

            // ─── Day-of-week headers ───────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth()) {
                    val daysOfWeek = listOf("日", "一", "二", "三", "四", "五", "六")
                    daysOfWeek.forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (day == "日" || day == "六") AuriCritical.copy(alpha = 0.6f) else Color.Gray,
                        )
                    }
                }
            }

            // ─── Month grid (collapsed = selected week only) ────────────────
            val daysInMonth = generateMonthGrid(state.currentMonth, state.tasks)
            val weeks = daysInMonth.chunked(7)

            // Find the week containing selectedDate
            val selectedWeekIndex = weeks.indexOfFirst { week ->
                week.any { it.date == state.selectedDate }
            }.coerceAtLeast(0)

            val visibleWeeks = if (isExpanded) weeks else listOf(weeks[selectedWeekIndex])

            item {
                Column {
                    visibleWeeks.forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { dayCell ->
                                DayCell(
                                    dayCell = dayCell,
                                    isSelected = dayCell.date == state.selectedDate,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (dayCell.date != null) viewModel.selectDate(dayCell.date)
                                    },
                                )
                            }
                        }
                    }
                    // Expand / collapse button
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { isExpanded = !isExpanded }) {
                            Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isExpanded) "收起" else "展开",
                                tint = Color.Gray,
                            )
                        }
                    }
                }
            }

            // ─── Selected date tasks ────────────────────────────────────────
            item {
                HorizontalDivider(color = Color(0xFFE8E8E8))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日${if (state.selectedDate == LocalDate.now()) " · 今天" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AuriNavy,
                    )
                    if (state.selectedDate != LocalDate.now()) {
                        TextButton(onClick = viewModel::goToToday) { Text("回到今天", color = AuriProcessing) }
                    }
                }
            }

            if (state.tasksOnSelectedDate.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text("当天无任务", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(state.tasksOnSelectedDate.distinctBy { it.taskId }, key = { it.taskId }) { task ->
                    TaskCard(task = task, onDelete = { viewModel.removeTask(task.taskId) })
                }
            }

            // ─── Unscheduled tasks ─────────────────────────────────────────
            if (state.unscheduledTasks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color(0xFFE8E8E8))
                    Text("未安排日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriWarning)
                }
                items(state.unscheduledTasks.distinctBy { it.taskId }, key = { it.taskId }) { task ->
                    TaskCard(task = task, onDelete = { viewModel.removeTask(task.taskId) })
                }
            }

            item { Spacer(Modifier.height(72.dp)) } // FAB clearance
        }
    }
}

// ─── Day Cell ─────────────────────────────────────────────────────────────

data class DayCell(
    val date: LocalDate?,
    val dayNumber: Int = 0,
    val isToday: Boolean = false,
    val taskCount: Int = 0,
    val hasRigidTask: Boolean = false,
)

@Composable
private fun DayCell(dayCell: DayCell, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bgColor = when {
        dayCell.date == null -> Color.Transparent
        isSelected -> AuriNavy.copy(alpha = 0.08f)
        dayCell.isToday -> AuriGold.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = dayCell.date != null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (dayCell.date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Day number
                if (dayCell.isToday) {
                    Surface(
                        shape = CircleShape,
                        color = AuriNavy,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${dayCell.dayNumber}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                } else {
                    Text(
                        "${dayCell.dayNumber}",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (dayCell.date.dayOfWeek == DayOfWeek.SUNDAY) AuriCritical.copy(alpha = 0.7f) else AuriNavy,
                        fontSize = 13.sp,
                    )
                }

                // Task dots
                if (dayCell.taskCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (dayCell.hasRigidTask) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(AuriCritical))
                        }
                        if (dayCell.taskCount > (if (dayCell.hasRigidTask) 1 else 0)) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(AuriSuccess))
                        }
                        if (dayCell.taskCount > 2) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.Gray))
                        }
                    }
                }
            }
        }
    }
}

// ─── Task Card ────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(task: Task, onDelete: (() -> Unit)? = null) {
    val timeText = task.scheduledAt?.let {
        try { it.split("T").getOrNull(1)?.substring(0, 5) } catch (_: Exception) { null }
    }
    val isLocal = task.taskId.startsWith("local_")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == TaskStatus.COMPLETED) Color(0xFFF0F0F0) else Color.White,
        ),
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // ── Time (left) ─────────────────────────────────────────
            if (timeText != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeText, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AuriNavy)
                    Text(
                        try { task.scheduledAt!!.split("T")[0].takeLast(5).replace("-", "/") } catch (_: Exception) { "" },
                        style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            // Type indicator bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (task.taskType == TaskType.RIGID) AuriCritical else AuriSuccess)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, fontWeight = FontWeight.SemiBold, color = if (task.status == TaskStatus.COMPLETED) Color.Gray else AuriNavy)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = if (task.taskType == TaskType.RIGID) AuriCritical.copy(alpha = 0.1f) else AuriSuccess.copy(alpha = 0.1f)) {
                        Text(
                            if (task.taskType == TaskType.RIGID) "刚性" else "弹性",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.taskType == TaskType.RIGID) AuriCritical else AuriSuccess,
                        )
                    }
                }
                if (task.location != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("📍 ${task.location}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF5F5F5)) {
                Text(
                    when (task.priority) { Priority.HIGH -> "高"; Priority.MEDIUM -> "中"; Priority.LOW -> "低" },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (isLocal && onDelete != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Text("✕", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ─── Month Grid Generator ─────────────────────────────────────────────────

private fun generateMonthGrid(month: YearMonth, tasks: List<Task>): List<DayCell> {
    val today = LocalDate.now()
    val firstDay = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()

    // Day-of-week: MONDAY=1 ... SUNDAY=7 -> we need SUNDAY=0
    val leadingBlanks = (firstDay.dayOfWeek.value % 7) // 0=Sun, 1=Mon, ... 6=Sat

    val cells = mutableListOf<DayCell>()

    // Leading blanks
    repeat(leadingBlanks) { cells.add(DayCell(date = null)) }

    // Days of month
    val taskCountByDate = tasks
        .mapNotNull { it.scheduledAt?.substring(0, 10) } // "2026-07-16"
        .groupingBy { it }.eachCount()

    val rigidByDate = tasks
        .filter { it.taskType == TaskType.RIGID }
        .mapNotNull { it.scheduledAt?.substring(0, 10) }
        .toSet()

    for (day in 1..daysInMonth) {
        val date = month.atDay(day)
        val key = date.toString()
        cells.add(
            DayCell(
                date = date,
                dayNumber = day,
                isToday = date == today,
                taskCount = taskCountByDate[key] ?: 0,
                hasRigidTask = key in rigidByDate,
            )
        )
    }

    return cells
}
