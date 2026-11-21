package com.ranhroi.touchremote.ui.screens.remote

import android.content.Context
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToastLong

// ===== Main Screen =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FujitsuRemoteScreen(
    navController: NavHostController = rememberNavController(),
    remoteViewModel: RemoteViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current

    val remotesList = remember { remoteViewModel.availableRemotes }
    val currentRemote by remoteViewModel.currentRemote.collectAsState()

    if (remotesList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.remote_loading_data), color = Color.White)
        }
        return
    }

    LaunchedEffect(Unit) {
        if (currentRemote == null && remotesList.isNotEmpty()) {
            remoteViewModel.selectRemote(remotesList.first().id)
        }
    }

    val selectedRemote = currentRemote ?: remotesList[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_title), fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            }
                            view.playSoundClick()
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            DynamicFujitsuRemote(view, context, selectedRemote, remotesList, remoteViewModel)
        }
    }
}

// ==========================================
// REMOTE VẬT LÝ - CÂN ĐỐI CHUYÊN NGHIỆP
// ==========================================
@Composable
fun DynamicFujitsuRemote(
    view: View,
    context: Context,
    selectedRemote: RemoteModel,
    remotesList: List<RemoteModel>,
    remoteViewModel: RemoteViewModel
) {
    // ===== COLLECT STATE =====
    val isOn by remoteViewModel.isOn.collectAsState()
    val temp by remoteViewModel.temp.collectAsState()
    val mode by remoteViewModel.mode.collectAsState()
    val fanSpeed by remoteViewModel.fanSpeed.collectAsState()
    val isSwing by remoteViewModel.isSwing.collectAsState()
    val isCoilDry by remoteViewModel.isCoilDry.collectAsState()
    val isAirClean by remoteViewModel.isAirClean.collectAsState()
    val isSleep by remoteViewModel.isSleep.collectAsState()
    val isPowerful by remoteViewModel.isPowerful.collectAsState()
    val isQuiet by remoteViewModel.isQuiet.collectAsState()
    val timerMode by remoteViewModel.timerMode.collectAsState()
    val timerHour by remoteViewModel.timerHour.collectAsState()
    var isVisibleDrop by remember { mutableStateOf(false) }

    LaunchedEffect(remoteViewModel.irManager) {
        if (remoteViewModel.irManager != null && remoteViewModel.irManager.hasIrEmitter()) {
            context.showToastLong("The infrared port is ready to operate.")
        } else {
            context.showToastLong("This device does not support physical infrared vision.")
        }
    }

    // Remote với tỷ lệ cân đối
    Column(
        modifier = Modifier
            .width(350.dp)
            .heightIn(min = 700.dp, max = 920.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFAFAFA), Color(0xFFF1F5F9))
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .border(2.dp, Color(0xFFCBD5E1), shape = RoundedCornerShape(32.dp))
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: Logo + Model
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.remote_inverter),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E293B),
                letterSpacing = 2.sp
            )
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                FeatureButton(
                    view = view,
                    text = selectedRemote.name,
                    enabled = true,
                    active = isVisibleDrop,
                    onClick = { isVisibleDrop = true },
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .padding(horizontal = 8.dp)
                )
                DropdownMenu(
                    expanded = isVisibleDrop,
                    onDismissRequest = { isVisibleDrop = false },
                    modifier = Modifier
                        .heightIn(max = 500.dp)
                        .widthIn(min = 200.dp)
                ) {
                    remotesList.forEachIndexed { index, remote ->
                        val isSelected = remote.id == selectedRemote.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Remote ${index + 1}",
                                            color = if (isSelected) Color(0xFFF97316) else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = remote.id,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(R.drawable.baseline_check),
                                            contentDescription = "Selected",
                                            tint = Color(0xFFF97316),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                view.playSoundClick()
                                remoteViewModel.selectRemote(remote.id)
                                isVisibleDrop = false
                            },
                            modifier = Modifier.background(
                                if (isSelected) Color(0xFFF97316).copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                        )

                        if (index < remotesList.size - 1) {
                            Divider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ===== MÀN HÌNH LCD CHUYÊN NGHIỆP =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 195.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF64748B).copy(alpha = 0.3f),
                            Color(0xFF94A3B8).copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(1.5.dp, Color(0xFF475569), shape = RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            if (isOn) {
                // ===== HÀNG 1: MODE + TEMPERATURE =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modeIcon = when (mode) {
                            "COOL" -> "❄️"
                            "HEAT" -> "🔥"
                            "DRY" -> "💧"
                            "FAN" -> "🌀"
                            "AUTO" -> "⚙️"
                            else -> "❄️"
                        }
                        Text(modeIcon, fontSize = 20.sp)
                        Column {
                            Text(
                                stringResource(R.string.remote_mode),
                                fontSize = 9.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                getModeText(mode),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        // Hiển thị biểu tượng Powerful/Quiet
                        if (isPowerful) {
                            Text("☀️", fontSize = 14.sp)
                        }
                        if (isQuiet) {
                            Text("🔇", fontSize = 14.sp)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "$temp",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            "°C",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = Color(0xFF94A3B8).copy(alpha = 0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // ===== HÀNG 2: FAN + SWING/VANE + SLEEP =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // CỘT 1: FAN
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.remote_fan),
                            fontSize = 9.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val fanIcon = when (fanSpeed) {
                                "AUTO" -> "🔄"
                                "HIGH" -> "⬆️"
                                "MED" -> "➡️"
                                "LOW" -> "⬇️"
                                "QUIET" -> "🔇"
                                else -> "🔄"
                            }
                            Text(fanIcon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                getFanText(fanSpeed),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPowerful) Color(0xFFDC2626) else Color.Black
                            )
                        }
                    }

                    // CỘT 2: SWING + VANE
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isSwing) stringResource(R.string.remote_swing) else stringResource(R.string.remote_set_vane),
                            fontSize = 9.sp,
                            color = if (isSwing) Color(0xFF2563EB) else Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSwing) {
                                SwingAnimation()
                                Text(
                                    stringResource(R.string.remote_on_short),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB)
                                )
                            } else {
                                Text(
                                    stringResource(R.string.remote_vane_indicator),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }
                    }

                    // CỘT 3: SLEEP
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.remote_sleep),
                            fontSize = 9.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSleep) {
                                Text(
                                    "⭐" + stringResource(R.string.remote_on_short),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF46ED3A)
                                )
                            } else {
                                Text(
                                    stringResource(R.string.remote_off_short),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = Color(0xFF94A3B8).copy(alpha = 0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // ===== HÀNG 3: TIMER + FEATURES =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CỘT 1: Timer
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.remote_timer),
                            fontSize = 9.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        if (timerMode != "NONE") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏱️", fontSize = 14.sp)
                                Text(
                                    "$timerMode ${timerHour}${stringResource(R.string.remote_hour)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.remote_off),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // CỘT 2: Features
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.remote_features),
                            fontSize = 9.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCoilDry) {
                                Text("💧", fontSize = 14.sp)
                            }
                            if (isAirClean) {
                                Text("🌀", fontSize = 14.sp)
                            }
                            if (isPowerful) {
                                Text("☀️", fontSize = 14.sp)
                            }
                            if (isQuiet) {
                                Text("🔇", fontSize = 14.sp)
                            }
                            if (!isCoilDry && !isAirClean && !isPowerful && !isQuiet) {
                                Text(
                                    "—",
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            } else {
                // Màn hình OFF
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painterResource(R.drawable.baseline_power_setting_new),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.remote_screen_off),
                            fontSize = 14.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            stringResource(R.string.remote_power_saving),
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ===== HÀNG PHÍM LỚN =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteButton(
                view = view,
                title = stringResource(R.string.remote_mode),
                subtitle = stringResource(R.string.remote_mode_change),
                enabled = isOn,
                onClick = { if (isOn) remoteViewModel.changeMode() },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFE2E8F0)
            )

            Button(
                onClick = {
                    view.playSoundClick()
                    remoteViewModel.togglePower()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOn) Color(0xFFDC2626) else Color(0xFF22C55E)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(64.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isOn) stringResource(R.string.remote_stop) else stringResource(R.string.remote_start),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        if (isOn) stringResource(R.string.remote_turn_off) else stringResource(R.string.remote_turn_on),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            RemoteButton(
                view = view,
                title = stringResource(R.string.remote_fan),
                subtitle = stringResource(R.string.remote_fan_speed),
                enabled = isOn,
                onClick = { if (isOn) remoteViewModel.changeFanSpeed() },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFE2E8F0)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== CỤM PHÍM GIỮA (3 CỘT) =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== CỘT TRÁI: FEATURES =====
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // AIR CLEAN - Cho phép ở hầu hết mode
                val airCleanEnabled = isOn && selectedRemote.hasAirClean && mode != "HEAT"

                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_clean),
                    enabled = airCleanEnabled,
                    active = isAirClean,
                    onClick = {
                        if (airCleanEnabled) {
                            remoteViewModel.toggleAirClean()
                        }
                    }
                )

                // COIL DRY - Cho phép ở COOL, DRY, AUTO
                val coilDryEnabled = isOn && selectedRemote.hasCoilDry &&
                        (mode == "COOL" || mode == "DRY" || mode == "AUTO")

                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_coil_dry),
                    enabled = coilDryEnabled,
                    active = isCoilDry,
                    onClick = {
                        if (coilDryEnabled) {
                            remoteViewModel.toggleCoilDry()
                        }
                    }
                )

                val sleepEnabled = isOn && selectedRemote.hasSleep
                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_sleep),
                    enabled = sleepEnabled,
                    active = isSleep,
                    onClick = { if (sleepEnabled) remoteViewModel.toggleSleep() },
                )

                val powerfulEnabled = isOn && selectedRemote.hasPowerful
                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_powerful),
                    enabled = powerfulEnabled,
                    active = isPowerful,
                    onClick = {
                        if (powerfulEnabled) {
                            remoteViewModel.togglePowerful()
                        }
                    }
                )
            }

            // ===== CỘT GIỮA: TEMPERATURE =====
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .padding(horizontal = 4.dp)
                    .background(Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFCBD5E1), shape = RoundedCornerShape(8.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(modifier = Modifier.weight(1f), onClick = {
                    if (isOn && temp < selectedRemote.maxTemp) {
                        view.playSoundClick()
                        remoteViewModel.updateTemp(temp + 1)
                    }
                }) { Text("▲", color = Color.Black, fontWeight = FontWeight.Bold) }
                Text(
                    stringResource(R.string.remote_set_temp),
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                IconButton(modifier = Modifier.weight(1f), onClick = {
                    if (isOn && temp > selectedRemote.minTemp) {
                        view.playSoundClick()
                        remoteViewModel.updateTemp(temp - 1)
                    }
                }) { Text("▼", color = Color.Black, fontWeight = FontWeight.Bold) }
            }

            // ===== CỘT PHẢI: VANE + SWING =====
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ▲ UP - Chỉ active khi Swing OFF
                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_vane_up),
                    enabled = isOn && !isSwing,
                    active = false,
                    onClick = {
                        if (isOn && !isSwing) {
                            remoteViewModel.triggerSwingStepVertical()
                        }
                    }
                )

                // ▼ DOWN - Chỉ active khi Swing OFF
                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_vane_down),
                    enabled = isOn && !isSwing,
                    active = false,
                    onClick = {
                        if (isOn && !isSwing) {
                            remoteViewModel.triggerSwingStepHorizontal()
                        }
                    },
                )

                // ⤡ SWING - Luôn enabled khi bật máy
                FeatureButton(
                    view = view,
                    text = stringResource(R.string.remote_swing_button),
                    enabled = isOn,
                    active = isSwing,
                    onClick = {
                        if (isOn) {
                            remoteViewModel.toggleSwing()
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ===== TIMER BLOCK =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⏱️ " + stringResource(R.string.remote_timer),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF47569B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallButton(
                    view = view,
                    title = stringResource(R.string.remote_timer_mode),
                    subtitle = timerMode,
                    onClick = { if (isOn) remoteViewModel.changeTimerMode() },
                    modifier = Modifier.weight(0.8f)
                )

                SmallButton(
                    view = view,
                    title = "−",
                    subtitle = "${timerHour}${stringResource(R.string.remote_hour)}",
                    onClick = { if (isOn && timerHour > 0 && timerMode == "ON") remoteViewModel.decreaseTimerHour() },
                    modifier = Modifier.weight(0.6f)
                )

                SmallButton(
                    view = view,
                    title = "+",
                    subtitle = "${timerHour}${stringResource(R.string.remote_hour)}",
                    onClick = { if (isOn && timerHour < 12 && timerMode == "ON") remoteViewModel.increaseTimerHour() },
                    modifier = Modifier.weight(0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ===== NÚT CHÌM DƯỚI ĐÁY =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TinyPinHoleButton(label = stringResource(R.string.remote_clock), isActive = timerMode != "NONE")
            TinyPinHoleButton(label = stringResource(R.string.remote_test), isActive = false)
            TinyPinHoleButton(label = stringResource(R.string.remote_reset), isActive = false)
        }
    }
}

// ===== COMPONENT NÚT REMOTE =====
@Composable
fun RemoteButton(
    view: View,
    title: String,
    subtitle: String,
    enabled: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFE2E8F0)
) {
    Button(
        enabled = enabled,
        onClick = { onClick(); view.playSoundClick() },
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        contentPadding = PaddingValues(4.dp),
        modifier = modifier.height(64.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ===== COMPONENT NÚT TÍNH NĂNG =====
@Composable
fun FeatureButton(
    view: View,
    text: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                color = when {
                    !enabled -> Color(0xFFE2E8F0).copy(alpha = 0.4f)
                    active -> Color(0xFFBBF7D0)
                    else -> Color(0xFFF1F5F9)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                1.5.dp,
                when {
                    active -> Color(0xFF22C55E)
                    !enabled -> Color(0xFFE2E8F0)
                    else -> Color(0xFFCBD5E1)
                },
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick(); view.playSoundClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = if (enabled) Color.Black else Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ===== COMPONENT NÚT NHỎ =====
@Composable
fun SmallButton(
    view: View,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onClick(); view.playSoundClick() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = modifier.height(38.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = Color(0xFF64748B),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ===== NÚT CHÌM DƯỚI ĐÁY =====
@Composable
fun TinyPinHoleButton(label: String, isActive: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (isActive) Color(0xFF22C55E) else Color(0xFFCBD5E1),
                    shape = CircleShape
                )
                .border(
                    1.5.dp,
                    if (isActive) Color(0xFF16A34A) else Color(0xFF94A3B8),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFF16A34A) else Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
    }
}

// ===== ANIMATION SWING =====
@Composable
fun SwingAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Text(
        "⤡",
        fontSize = 18.sp,
        modifier = Modifier.graphicsLayer(rotationZ = rotation),
        color = Color(0xFF2563EB)
    )
}


// ===== Helper functions for dynamic text =====
@Composable
private fun getModeText(mode: String): String {
    return when (mode) {
        "COOL" -> stringResource(R.string.remote_mode_cool)
        "HEAT" -> stringResource(R.string.remote_mode_heat)
        "DRY" -> stringResource(R.string.remote_mode_dry)
        "FAN" -> stringResource(R.string.remote_mode_fan)
        "AUTO" -> stringResource(R.string.remote_mode_auto)
        else -> mode
    }
}

@Composable
private fun getFanText(fan: String): String {
    return when (fan) {
        "AUTO" -> stringResource(R.string.remote_fan_auto)
        "HIGH" -> stringResource(R.string.remote_fan_high)
        "MED" -> stringResource(R.string.remote_fan_med)
        "LOW" -> stringResource(R.string.remote_fan_low)
        "QUIET" -> stringResource(R.string.remote_fan_quiet)
        else -> fan
    }
}
