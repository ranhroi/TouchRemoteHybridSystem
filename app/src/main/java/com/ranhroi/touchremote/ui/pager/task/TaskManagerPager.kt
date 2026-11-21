package com.ranhroi.touchremote.ui.pager.task

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.HeaderMenuModel
import com.ranhroi.touchremote.ui.components.HeaderHorizontal
import com.ranhroi.touchremote.ui.dialogs.ConfirmDialog
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max

@Composable
fun TaskManagerPager(
    view: View,
    config: CombineSettingModel,
    connection: ConnectionViewModel,
    hybridState: HybridConnectionModel,
    viewModel: TaskManagerViewModel = viewModel()
) {
    // Collect data
    val ramUsages by viewModel.ramUsages.collectAsState()
    val cpuUsages by viewModel.cpuUsages.collectAsState()
    val totalRamKBs by viewModel.totalRamKBs.collectAsState()
    val processes by viewModel.processes.collectAsState()
    val sortProcessesBy by viewModel.sortProcessesBy.collectAsState()
    val lastUpdateTime by viewModel.lastUpdateTime.collectAsState()

    // UI State
    var showKillDialog by remember { mutableStateOf(false) }
    var selectedProcess by remember { mutableStateOf<ProcessModel?>(null) }
    var isAutoTask by remember { mutableStateOf(config.isAutoTask) }
    var currentDelayInterval by remember { mutableLongStateOf(900L) }  // Mặc định 1 phút
    var showDelayMenu by remember { mutableStateOf(false) }


    // Setup socket
    LaunchedEffect(hybridState) {
        if (hybridState.isConnected && hybridState.output != null) {
            viewModel.init(hybridState)

            withContext(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(hybridState.input))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isNotEmpty()) {
                            // Chỉ xử lý dòng bắt đầu bằng "task_manager"
                            if (line.startsWith("task_manager")) {
                                withContext(Dispatchers.Main) {
                                    viewModel.processTaskManagerData(line)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Error reading socket: ${e.message}")
                }
            }

            // Start auto-refresh
            if (isAutoTask) {
                viewModel.startAutoRefresh(currentDelayInterval)
            }
        } else {
            viewModel.stopAutoRefresh()
            viewModel.cleanup()
        }
    }

    // Toggle auto-refresh
    LaunchedEffect(isAutoTask, currentDelayInterval) {
        if (isAutoTask) {
            viewModel.startAutoRefresh(currentDelayInterval)
        } else {
            viewModel.stopAutoRefresh()
        }
    }


    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAutoRefresh()
            viewModel.cleanup()
        }
    }


    val leftMenuItems = listOf(
        HeaderMenuModel(
            isStatusDot = true,
            isConnected = hybridState.isConnected,
            isVisible = { true }
        ),
        HeaderMenuModel(
            text = if (hybridState.isConnected)
                stringResource(R.string.app_connected)
            else
                stringResource(R.string.app_disconnected),
            textColor = if (hybridState.isConnected) Color(0xFF4CAF50) else Color(0xFFE57373),
            isVisible = { true }
        )
    )

    val rightMenuItems = listOf(
        HeaderMenuModel(
            icon = if (isAutoTask) "baseline_pause" else "baseline_play_arrow",
            onClick = { isAutoTask = !isAutoTask },
            enabled = hybridState.isConnected
        ),
        HeaderMenuModel(
            icon = "baseline_import_export",
            onClick = { viewModel.requestData() },
            enabled = hybridState.isConnected
        ),
        HeaderMenuModel(
            icon = "baseline_refresh",
            onClick = { connection.pingCommand() }
        )
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        HeaderHorizontal(
            view = view,
            modifier = Modifier.fillMaxWidth(),
            leftItems = leftMenuItems,
            centerContent = {
                Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showDelayMenu = true }) {
                        val ms = currentDelayInterval.toInt()
                        val displayText = when {
                            ms == 0 -> stringResource(R.string.task_no_delay)
                            ms < 1000 -> stringResource(R.string.task_ms, ms)
                            else -> stringResource(
                                R.string.task_seconds,
                                ms / 1000,
                                (ms % 1000) / 100
                            )
                        }
                        Text(displayText, fontSize = 11.sp)
                    }
                    DropdownMenu(
                        expanded = showDelayMenu,
                        onDismissRequest = { showDelayMenu = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        (0..2000 step 100).forEach { interval ->
                            val displayText = when {
                                interval == 0 -> stringResource(R.string.task_no_delay)
                                interval < 1000 -> stringResource(R.string.task_ms, interval)
                                else -> stringResource(
                                    R.string.task_seconds,
                                    interval / 1000,
                                    (interval % 1000) / 100
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(displayText, fontSize = 12.sp) },
                                onClick = {
                                    currentDelayInterval = interval.toLong()
                                    showDelayMenu = false
                                }
                            )
                        }
                    }
                }

                // Last update time / connection status
                Text(
                    text = if (hybridState.isConnected) {
                        lastUpdateTime.ifEmpty { "--:--:--" }
                    } else {
                        "--:--:--"
                    },
                    fontSize = 12.sp,
                    color = if (hybridState.isConnected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.Red
                    }
                )
            },
            rightItems = rightMenuItems
        )

        // Show connection waiting state if disconnected
        if (!hybridState.isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.task_waiting_connection),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            // CPU and RAM graphs section (25% of height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // CPU Graph
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 5.dp, top = 5.dp, bottom = 5.dp, end = 2.5.dp)
                ) {
                    CpuGraphView(
                        cpuUsages = cpuUsages,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(1.dp)
                    )
                    Text(
                        text = stringResource(R.string.task_cpu),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    CpuPercentageText(
                        cpuUsages = cpuUsages,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(5.dp)
                    )
                }

                // RAM Graph
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 2.5.dp, top = 5.dp, bottom = 5.dp, end = 5.dp)
                ) {
                    RamGraphView(
                        ramUsages = ramUsages,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(1.dp)
                    )
                    Text(
                        text = stringResource(R.string.task_ram),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    RamUsageText(
                        ramUsages = ramUsages,
                        totalRamKBs = totalRamKBs,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(5.dp)
                    )
                    RamPercentageText(
                        ramUsages = ramUsages,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                    )
                }
            }

            // Process list section (75% of height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.75f)
            ) {
                // Header row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp)
                    ) {
                        HeaderText(
                            text = stringResource(R.string.task_process_name),
                            isSelected = sortProcessesBy == 0,
                            onClick = { viewModel.setSortOrder(0) },
                            modifier = Modifier.weight(1f)
                        )
                        HeaderText(
                            text = stringResource(R.string.task_process_cpu),
                            isSelected = sortProcessesBy == 1,
                            onClick = { viewModel.setSortOrder(1) },
                            modifier = Modifier.weight(0.3f)
                        )
                        HeaderText(
                            text = stringResource(R.string.task_process_ram),
                            isSelected = sortProcessesBy == 2,
                            onClick = { viewModel.setSortOrder(2) },
                            modifier = Modifier.weight(0.3f)
                        )
                    }
                }

                // Process list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (processes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.task_no_processes),
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        ProcessListView(
                            processes = processes,
                            onProcessLongClick = { process ->
                                selectedProcess = process
                                showKillDialog = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.LightGray.copy(alpha = 0.1f))
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    // Kill process dialogs
    ConfirmDialog(
        show = showKillDialog && selectedProcess != null,
        title = stringResource(R.string.task_kill_title, selectedProcess?.name ?: ""),
        message = stringResource(R.string.task_kill_message),
        onConfirm = {
            selectedProcess?.let {
                viewModel.killProcess(it.pid)
            }
            showKillDialog = false
            selectedProcess = null
        },
        onDismiss = {
            showKillDialog = false
            selectedProcess = null
        }
    )
}

// ==================== CPU PERCENTAGE TEXT ====================
@Composable
fun CpuPercentageText(
    cpuUsages: List<MutableList<Float>>,
    modifier: Modifier = Modifier
) {
    val cpuText = remember(cpuUsages) {
        buildString {
            cpuUsages.forEachIndexed { index, core ->
                if (core.isNotEmpty()) {
                    val percent = (100.0f * core.last()).toInt()
                    if (index > 0) append(" ")
                    append("$percent%")
                }
            }
            if (isEmpty()) append("0%")
        }
    }

    Text(
        text = cpuText,
        modifier = modifier,
        textAlign = TextAlign.Start,
        fontSize = 12.sp
    )
}

// ==================== RAM COMPONENTS ====================
@SuppressLint("DefaultLocale")
@Composable
fun RamUsageText(
    ramUsages: List<Float>,
    totalRamKBs: Int,
    modifier: Modifier = Modifier
) {
    val ramText = remember(ramUsages, totalRamKBs) {
        if (ramUsages.isNotEmpty() && totalRamKBs > 0) {
            val pctUsed = ramUsages.last()
            val totalGB = totalRamKBs.toFloat() / (1024 * 1024)
            val usedGB = pctUsed * totalGB
            val formattedUsed = String.format("%.02f", usedGB)
            val formattedTotal = String.format("%.02f", totalGB)
            "$formattedUsed GB / $formattedTotal GB"
        } else "0.00 GB / 0.00 GB"
    }

    Text(
        text = ramText,
        modifier = modifier,
        textAlign = TextAlign.Start,
        fontSize = 12.sp
    )
}

@Composable
fun RamPercentageText(
    ramUsages: List<Float>,
    modifier: Modifier = Modifier
) {
    val percentageText = remember(ramUsages) {
        if (ramUsages.isNotEmpty()) {
            val pctUsed = ramUsages.last()
            "${(pctUsed * 100).toInt()}%"
        } else "0%"
    }

    Text(
        text = percentageText,
        modifier = modifier,
        textAlign = TextAlign.End,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFBB5555)
    )
}

// ==================== PROCESS LIST ====================
@Composable
fun ProcessListView(
    processes: List<ProcessModel> = emptyList(),
    onProcessLongClick: (ProcessModel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(processes, key = { it.pid }) { process ->
            ProcessListItem(
                process = process,
                onLongClick = { onProcessLongClick(process) }
            )
        }
    }
}

// ==================== PROCESS LIST ITEM ====================
@Composable
fun ProcessListItem(
    process: ProcessModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 2.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = process.name,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ✅ CPU: hiển thị số thập phân (ví dụ: 12.5%)
            Text(
                text = formatPercentage(process.cpu),
                modifier = Modifier.width(70.dp),
                fontSize = 14.sp,
                textAlign = TextAlign.End,
                color = if (process.cpu > 50) Color.Red else Color.Unspecified
            )

            // ✅ RAM: hiển thị MB (ví dụ: 370.5 MB)
            Text(
                text = formatMemoryMB(process.ram),
                modifier = Modifier.width(90.dp),
                fontSize = 14.sp,
                textAlign = TextAlign.End,
                color = if (process.ram > 500) Color.Red else Color.Unspecified
            )
        }
    }
}

// ==================== RAM GRAPH ====================
@Composable
fun RamGraphView(
    ramUsages: List<Float>,
    modifier: Modifier = Modifier
) {
    val ramColor = Color(0xFFBB5555)

    Canvas(modifier = modifier) {
        if (ramUsages.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height

        // Draw graph background
        drawRect(color = Color.Gray.copy(alpha = 0.1f))

        // Draw grid lines
        for (i in 1..3) {
            val y = height * i / 4
            drawLine(
                color = Color.LightGray.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Create path for RAM graph
        val path = Path()
        val pointCount = ramUsages.size

        for (i in ramUsages.indices) {
            val x = (i.toFloat() / max(pointCount - 1, 1)) * width
            val usage = ramUsages[i]
            val y = height - (usage * height)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw the path
        drawPath(
            path = path,
            color = ramColor,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ==================== CPU GRAPH (MULTI-CORE) ====================
@Composable
fun CpuGraphView(
    cpuUsages: List<MutableList<Float>>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (cpuUsages.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height

        // Draw graph background
        drawRect(color = Color.Gray.copy(alpha = 0.1f))

        // Draw grid lines
        for (i in 1..3) {
            val y = height * i / 4
            drawLine(
                color = Color.LightGray.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw each CPU core
        cpuUsages.forEachIndexed { coreIndex, coreUsages ->
            if (coreUsages.isEmpty()) return@forEachIndexed

            val path = Path()
            val pointCount = coreUsages.size

            for (i in coreUsages.indices) {
                val x = (i.toFloat() / max(pointCount - 1, 1)) * width
                val usage = coreUsages[i]
                val y = height - (usage * height)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            // Draw the core path
            drawPath(
                path = path,
                color = getCpuColor(coreIndex),
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

// ==================== HEADER TEXT ====================
@Composable
fun HeaderText(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clickable { onClick() }
            .padding(15.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 16.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

// ==================== FORMAT UTILITIES ====================
private fun formatPercentage(value: Double): String {
    return when {
        value == 0.0 -> "0%"
        value == value.toInt().toDouble() -> "${value.toInt()}%"
        else -> String.format("%.1f%%", value)
    }
}

private fun formatMemoryMB(value: Double): String {
    return when {
        value == 0.0 -> "0 MB"
        value == value.toInt().toDouble() -> "${value.toInt()} MB"
        else -> String.format("%.1f MB", value)
    }
}

// ==================== UTILITY FUNCTIONS ====================
private fun getCpuColor(coreNum: Int): Color {
    return when (coreNum % 8) {
        0 -> Color(0xFFFF6E00)  // Orange
        1 -> Color(0xFFCB0C29)  // Red
        2 -> Color(0xFF49A835)  // Green
        3 -> Color(0xFF2D7DB3)  // Blue
        4 -> Color(0xFFF97DB3)  // Pink
        5 -> Color(0xFF805A9F)  // Purple
        6 -> Color(0xFFFFCD00)  // Yellow
        else -> Color(0xFFB3E5DC) // Mint
    }
}