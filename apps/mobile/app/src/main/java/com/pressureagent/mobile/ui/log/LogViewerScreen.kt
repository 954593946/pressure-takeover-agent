package com.pressureagent.mobile.ui.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onNavigateBack: () -> Unit = {}) {
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var filterLevel by remember { mutableStateOf<AppLogger.Level?>(null) }
    var showLogcat by remember { mutableStateOf(false) }
    var logcatLines by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs
        else logs.filter { it.level == filterLevel }
    }

    val displayItems: List<Any> = if (showLogcat) logcatLines else filteredLogs

    // Auto-scroll to bottom
    LaunchedEffect(displayItems.size) {
        if (autoScroll && displayItems.isNotEmpty()) {
            listState.animateScrollToItem(displayItems.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF1E1E1E),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showLogcat) "Logcat (${logcatLines.size})" else "应用日志 (${filteredLogs.size})",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    // Toggle app logs vs logcat
                    TextButton(onClick = {
                        showLogcat = !showLogcat
                        if (showLogcat) {
                            scope.launch {
                                logcatLines = withContext(Dispatchers.IO) {
                                    AppLogger.readLogcatLines(300)
                                }
                            }
                        }
                    }) {
                        Text(if (showLogcat) "应用日志" else "Logcat", color = AuriGold, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            logcatLines = withContext(Dispatchers.IO) {
                                AppLogger.readLogcatLines(300)
                            }
                            showLogcat = true
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (autoScroll) "暂停滚动" else "自动滚动",
                            tint = if (autoScroll) AuriSuccess else Color.Gray,
                        )
                    }
                    IconButton(onClick = { AppLogger.clear(); logcatLines = emptyList() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2D2D2D)),
            )
        },
        bottomBar = {
            if (!showLogcat) {
                Surface(color = Color(0xFF2D2D2D)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filterLevel == null,
                            onClick = { filterLevel = null },
                            label = { Text("全部", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuriNavy.copy(alpha = 0.5f),
                                selectedLabelColor = Color.White,
                            ),
                        )
                        AppLogger.Level.entries.forEach { level ->
                            FilterChip(
                                selected = filterLevel == level,
                                onClick = { filterLevel = if (filterLevel == level) null else level },
                                label = { Text("${level.emoji} ${level.label}", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (level) {
                                        AppLogger.Level.ERROR -> AuriCritical.copy(alpha = 0.5f)
                                        AppLogger.Level.WARN -> AuriWarning.copy(alpha = 0.5f)
                                        AppLogger.Level.INFO -> AuriNavy.copy(alpha = 0.5f)
                                        AppLogger.Level.DEBUG -> Color.Gray.copy(alpha = 0.5f)
                                    },
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (displayItems.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("暂无日志", color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "文件: ${AppLogger.logFilePath()}",
                    color = Color.Gray.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "点击右上角刷新按钮读取 Logcat\n或切换标签查看不同来源",
                    color = Color.Gray.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        } else if (showLogcat) {
            // Logcat display
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(logcatLines.size) { index ->
                    val line = logcatLines[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = Color(0xFF2D2D2D),
                    ) {
                        Text(
                            line,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            // AppLogger entries
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(filteredLogs, key = { (it as AppLogger.Entry).id }) { entry ->
                    LogEntry(entry as AppLogger.Entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntry(entry: AppLogger.Entry) {
    val bgColor = when (entry.level) {
        AppLogger.Level.ERROR -> Color(0xFF3D1F1F)
        AppLogger.Level.WARN -> Color(0xFF3D3A1F)
        AppLogger.Level.INFO -> Color(0xFF1F2D3D)
        AppLogger.Level.DEBUG -> Color(0xFF2D2D2D)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${entry.level.emoji} ${entry.tag}",
                        color = when (entry.level) {
                            AppLogger.Level.ERROR -> AuriCritical
                            AppLogger.Level.WARN -> AuriWarning
                            else -> Color.White.copy(alpha = 0.7f)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    entry.timestamp,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                entry.message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
