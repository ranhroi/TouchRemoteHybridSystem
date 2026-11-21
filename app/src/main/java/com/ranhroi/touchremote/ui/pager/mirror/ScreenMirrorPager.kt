package com.ranhroi.touchremote.ui.pager.mirror

import android.annotation.SuppressLint
import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.HeaderMenuModel
import com.ranhroi.touchremote.ui.components.HeaderVertical
import com.ranhroi.touchremote.ui.pager.touch.Touchpad
import com.ranhroi.touchremote.ui.screens.server.ConnectionManager
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.utils.getStringSafe
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToastLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMirrorPager(
    view: View,
    config: CombineSettingModel,
    connection: ConnectionViewModel,
    viewModel: ScreenMirrorViewModel = hiltViewModel()
) {
    val allConnections by connection.connections.collectAsStateWithLifecycle()
    val streamState = allConnections[ConnectionManager.ROLE_STREAM] ?: HybridConnectionModel()

    val inputManager by connection.inputState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    val isStreaming by viewModel.isStreaming.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val audioState by viewModel.audioState.collectAsState()
    val mouseData by viewModel.mouseData.collectAsState()

    // ===== UI STATE =====
    var showControls by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(90f) }

    var surfaceViewInstance: SurfaceView? by remember { mutableStateOf(null) }

    var selectedVideoConfigIndex by rememberSaveable { mutableIntStateOf(5) }
    var selectedAudioConfigIndex by rememberSaveable { mutableIntStateOf(2) }

    var udpPort by rememberSaveable { mutableStateOf("5050") }

    var rmsThreshold by rememberSaveable { mutableFloatStateOf(30f) }
    var forcedPlay by rememberSaveable { mutableStateOf(false) }
    var enabledAudio by rememberSaveable { mutableStateOf(false) }

    // ===== HEADER AUTO-HIDE STATE =====
    var isHeaderVisible by remember { mutableStateOf(true) }
    var hideHeaderJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ===== VIDEO CONTAINER =====
    var videoW by remember { mutableStateOf(VIDEO_PRESETS[selectedVideoConfigIndex].width.toFloat()) }
    var videoH by remember { mutableStateOf(VIDEO_PRESETS[selectedVideoConfigIndex].height.toFloat()) }

    val density = LocalDensity.current

    // --- ZOOM & PAN ---
    var zoomScale by remember { mutableStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var panSpeed by rememberSaveable { mutableFloatStateOf(20f) } // 🔥 TỐC ĐỘ PAN


    // Khởi tạo trạng thái cho 2 thanh trượt điều chỉnh bù trừ tọa độ Hotspot chuột
    // Bạn có thể chỉnh sửa khoảng giá trị -100f đến 100f tùy ý
    var customOffsetX by remember { mutableFloatStateOf(-12f) } // Giá trị mặc định trục X ban đầu bạn muốn là +20f
    var customOffsetY by remember { mutableFloatStateOf(14f) }  // Giá trị mặc định trục Y ban đầu bạn muốn là 0f
    var isSetHotspot by remember { mutableStateOf(false) }


    val errorColor = MaterialTheme.colorScheme.error
    val context = LocalContext.current

    // ===== HEADER AUTO-HIDE FUNCTIONS =====
    fun showHeaderTemporarily() {
        isHeaderVisible = true
        hideHeaderJob?.cancel()
        hideHeaderJob = coroutineScope.launch {
            delay(10000.milliseconds)
            if (!showControls ) {
                isHeaderVisible = false
            }
        }
    }

    fun toggleHeader() {
        if (isHeaderVisible) {
            isHeaderVisible = false
            hideHeaderJob?.cancel()
        } else {
            showHeaderTemporarily()
        }
    }

    fun forceShowHeader() {
        isHeaderVisible = true
        hideHeaderJob?.cancel()
    }

    fun toggleControls() {
        showControls = !showControls
        if (showControls) {
            forceShowHeader()
        } else {
            hideHeaderJob?.cancel()
            hideHeaderJob = coroutineScope.launch {
                delay(10000.milliseconds)
                if (!showControls) {
                    isHeaderVisible = false
                }
            }
        }
    }

    // ===== SURFACE CALLBACK =====
    LaunchedEffect(streamState.isConnected) {
        if (streamState.isConnected) {
            viewModel.initConnection(streamState, config)
        } else {
            inputManager.sendStringMessage("mirror stop")
            viewModel.stopStream()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isStreaming) {
                inputManager.sendStringMessage("mirror stop")
                viewModel.stopStream()
            }
            inputManager.sendStringMessage("mirror stop")
            viewModel.stopStream()
            connection.disconnectRole(ConnectionManager.ROLE_STREAM)
        }
    }

    DisposableEffect(surfaceViewInstance) {
        val activity = context as? Activity
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                viewModel.setSurface(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                viewModel.setSurface(null)
            }
        }
        surfaceViewInstance?.holder?.addCallback(callback)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            inputManager.sendStringMessage("mirror stop")
            surfaceViewInstance?.holder?.removeCallback(callback)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                inputManager.sendStringMessage("mirror stop")
                viewModel.stopStream()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ===== MAIN LAYOUT =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (isStreaming) {
                        showHeaderTemporarily()
                    }
                }
            }
    ) {
        // VÙNG STREAM VIDEO
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { coordinates ->
                    val containerW = coordinates.size.width.toFloat()
                    val containerH = coordinates.size.height.toFloat()

                    if (containerW > 0 && containerH > 0) {
                        val targetAspectRatio = 9f / 16f
                        var calculatedH = containerW / targetAspectRatio
                        var calculatedW = containerW

                        if (calculatedH > containerH) {
                            calculatedH = containerH
                            calculatedW = containerH * targetAspectRatio
                        }
                        videoW = calculatedW
                        videoH = calculatedH
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isConnected) {
                val imgW = mouseData.screenWidth.toFloat()
                val imgH = mouseData.screenHeight.toFloat()

                var drawWInDp = with(density) { videoW.toDp() }
                var drawHInDp = with(density) { videoH.toDp() }
                var drawWInPx = videoW
                var drawHInPx = videoH

                if ((imgW > 0f && imgH > 0f) || (videoH > 0f && videoW > 0f)) {
                    val isRotated = rotationAngle % 180 != 0f

                    val scale = if (isRotated) {
                        val scaleX = videoW / imgH
                        val scaleY = videoH / imgW
                        minOf(scaleX, scaleY)
                    } else {
                        val scaleX = videoW / imgW
                        val scaleY = videoH / imgH
                        minOf(scaleX, scaleY)
                    }

                    drawWInPx = (if (isRotated) imgH else imgW) * scale
                    drawHInPx = (if (isRotated) imgW else imgH) * scale

                    drawWInDp = with(density) { drawWInPx.toDp() }
                    drawHInDp = with(density) { drawHInPx.toDp() }
                }

                // KHUNG CHỨA VIDEO + CHUỘT + CẢM ỨNG
                Box(
                    modifier = Modifier
                        .size(width = drawWInDp, height = drawHInDp)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    // LỚP 1: AndroidView
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceView(ctx).also {
                                surfaceViewInstance = it
                            }
                        },
                        update = { view ->
                            val params = view.layoutParams
                            if (params != null && (params.width != videoW.toInt() || params.height != videoH.toInt())) {
                                params.width = videoW.toInt()
                                params.height = videoH.toInt()
                                view.layoutParams = params
                            }
                        }
                    )

                    // LỚP 2: Touchpad nhận tương tác cảm ứng
                    Touchpad(
                        modifier = Modifier.fillMaxSize(),
                        inputManager = inputManager,
                        isCursor = false,
                    )

                    // LỚP 3: CON TRỎ CHUỘT
                    if (mouseData.xRatio >= 0f && mouseData.yRatio >= 0f) {

                        // Sử dụng đúng hàm map gốc của bạn, thay thế các biến draw thô bằng videoW/H lọt lòng chuẩn
                        val mouseOffset = mapDecoderRotatedMouse(
                            xRatio = mouseData.xRatio,
                            yRatio = mouseData.yRatio,
                            videoDrawW = videoW,
                            videoDrawH = videoH,
                            rotationAngle = rotationAngle
                        )

                        val iconSize = 20.dp
                        val iconSizePx = with(density) { iconSize.toPx() }

                        // =================================================================================
                        // 1. GÁN HAI BIẾN ĐỘNG TỪ THANH KÉO (SLIDER) VÀO HOTSPOT
                        // =================================================================================
                        val (visualX, visualY) = when (mouseData.shape) {
                            // Tích hợp trực tiếp 2 biến từ thanh kéo do người dùng chỉnh
                            0, 2, 13 -> Pair(iconSizePx + customOffsetX, customOffsetY)
                            1 -> Pair(iconSizePx * 0.25f, iconSizePx * 0.10f)
                            4, 6, 7, 8 -> Pair(iconSizePx / 2f, iconSizePx / 2f)
                            else -> Pair(iconSizePx / 2f, iconSizePx / 2f)
                        }

                        val visualDx = visualX - (iconSizePx / 2f)
                        val visualDy = visualY - (iconSizePx / 2f)

                        val rawTopLeftX = mouseOffset.x - visualDx - (iconSizePx / 2f)
                        val rawTopLeftY = mouseOffset.y - visualDy - (iconSizePx / 2f)

                        // =================================================================================
                        // 2. THUẬT TOÁN BIÊN ĐỘNG: TRÊN CHẶN CỨNG 0, DƯỚI CHO PHÉP TỤT PHỤ THUỘC HOTSPOT
                        // =================================================================================
                        // 🔹 Biên nhỏ nhất (Cạnh Trái và Cạnh Trên):
                        // Để đỉnh mũi nhọn ghim khít mép 0 của màn hình mà không bị tụt lố lên vùng âm,
                        // ta khóa chặt điểm sàn tối thiểu tại giá trị đối nghịch của Hotspot thực tế.
                        val minBoundaryX = -visualX
                        val minBoundaryY = -visualY

                        // 🔹 Biên lớn nhất (Cạnh Phải và Cạnh Dưới Taskbar):
                        // Để thân chuột tụt hẳn xuống dưới kịch sàn Taskbar một khoảng bằng kích thước của nó,
                        // ta mở rộng biên kịch trần tiến sát đến chiều rộng/chiều cao tối đa của Video lọt lòng.
                        val maxBoundaryX = videoW
                        val maxBoundaryY = videoH

                        // Ép tọa độ dịch chuyển chạy chuẩn theo ma trận biên động kiểu Windows
                        val topLeftX = rawTopLeftX.coerceIn(minBoundaryX, maxBoundaryX)
                        val topLeftY = rawTopLeftY.coerceIn(minBoundaryY, maxBoundaryY)

                        Icon(
                            painterResource(getMouseIcon(mouseData.shape)),
                            contentDescription = null,
                            tint = if (mouseData.shape in 0..13) Color.White else Color.Transparent,
                            modifier = Modifier
                                .size(iconSize)
                                .offset {
                                    IntOffset(
                                        topLeftX.roundToInt(),
                                        topLeftY.roundToInt()
                                    )
                                }
                                .graphicsLayer {
                                    if (mouseData.shape != 0) rotationZ = rotationAngle
                                    if (mouseData.shape == 0) {
                                        rotationZ = rotationAngle// - 45f
                                    }
                                    when (mouseData.shape) {
                                        4 -> rotationZ = rotationAngle + 90f
                                        7 -> rotationZ = rotationAngle + 90f
                                        8 -> rotationZ = rotationAngle
                                        10 -> rotationZ = rotationAngle + 90f
                                    }
                                }
                                .shadow(2.dp, CircleShape)
                        )
                    }
                }
            } else {
                EmptyStreamView(
                    isConnected = isConnected,
                    isStreaming = isStreaming,
                    rotationAngle
                )
            }
        }


        // ===== CONTROLS PANEL Mouse  =====
        AnimatedVisibility(
            visible = isSetHotspot,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .padding(16.dp)
                ) {
                    // ===== HEADER =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.mirror_mouse_adjust),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { isSetHotspot = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.mirror_close),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 🔹 THANH KÉO TRỤC X
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.mirror_mouse_x),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${customOffsetX.roundToInt()} px",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customOffsetX,
                            onValueChange = { customOffsetX = it },
                            valueRange = -50f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = stringResource(R.string.mirror_mouse_x_desc),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🔹 THANH KÉO TRỤC Y
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.mirror_mouse_y),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${customOffsetY.roundToInt()} px",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customOffsetY,
                            onValueChange = { customOffsetY = it },
                            valueRange = -50f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = stringResource(R.string.mirror_mouse_y_desc),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== NÚT RESET =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                customOffsetX = 0f
                                customOffsetY = 0f
                                view.playSoundClick()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.mirror_reset),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = { isSetHotspot = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.mirror_close),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }


        // ===== CONTROLS PANEL =====
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ===== HEADER =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.mirror_stream_settings),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { showControls = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.mirror_close),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    // ===== UDP PORT =====
                    OutlinedTextField(
                        value = udpPort,
                        onValueChange = { udpPort = it },
                        label = { Text(stringResource(R.string.mirror_udp_port), fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isStreaming,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                    )

                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    // ===== VIDEO CONFIG =====
                    var expandedVideo by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedVideo,
                            onExpandedChange = { expandedVideo = it }
                        ) {
                            val selectedVideo = VIDEO_PRESETS[selectedVideoConfigIndex]
                            val displayText =
                                "${selectedVideo.name} (${selectedVideo.width}×${selectedVideo.height} @ ${selectedVideo.fps}fps, Q${selectedVideo.quality})"

                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.mirror_video_config), fontSize = 10.sp) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVideo)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = !isStreaming,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.5f
                                    )
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = expandedVideo,
                                onDismissRequest = { expandedVideo = false }
                            ) {
                                VIDEO_PRESETS.forEachIndexed { index, preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = preset.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "Q${preset.quality}",
                                                        fontSize = 10.sp,
                                                        color = when {
                                                            preset.quality >= 85 -> Color(0xFF4CAF50)
                                                            preset.quality >= 65 -> Color(0xFFFF9800)
                                                            else -> Color(0xFFF44336)
                                                        },
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = "${preset.width}×${preset.height} · ${preset.fps}fps",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedVideoConfigIndex = index
                                            expandedVideo = false
                                            videoW = preset.width.toFloat()
                                            videoH = preset.height.toFloat()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (index < VIDEO_PRESETS.size - 1) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    // ===== AUDIO CONFIG =====
                    var expandedAudio by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedAudio,
                            onExpandedChange = { expandedAudio = it }
                        ) {
                            val selectedAudio = AUDIO_PRESETS[selectedAudioConfigIndex]
                            val displayText =
                                "${selectedAudio.name} (${selectedAudio.sampleRate / 1000}kHz, ${selectedAudio.channels}ch, ${selectedAudio.bitDepth}bit)"

                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.mirror_audio_config), fontSize = 10.sp) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAudio)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = !isStreaming,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.5f
                                    )
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = expandedAudio,
                                onDismissRequest = { expandedAudio = false }
                            ) {
                                AUDIO_PRESETS.forEachIndexed { index, preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = preset.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${preset.sampleRate / 1000}kHz · ${preset.channels}ch · ${preset.bitDepth}bit",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedAudioConfigIndex = index
                                            expandedAudio = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (index < AUDIO_PRESETS.size - 1) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== RMS SLIDER =====
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.mirror_rms_threshold),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rmsThreshold.toInt().toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = rmsThreshold,
                            onValueChange = { rmsThreshold = it },
                            valueRange = 1f..150f,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isStreaming,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    // ===== PAN SPEED SLIDER =====
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.mirror_pan_speed),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = panSpeed.toInt().toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = panSpeed,
                            onValueChange = { panSpeed = it },
                            valueRange = 5f..80f,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isStreaming,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = stringResource(R.string.mirror_pan_speed_desc),
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    // ===== CHECKBOXES =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Audio Enable
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Checkbox(
                                checked = enabledAudio,
                                onCheckedChange = { enabledAudio = it },
                                enabled = !isStreaming,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.mirror_audio),
                                fontSize = 10.sp,
                                color = if (enabledAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Forced Play
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Checkbox(
                                checked = forcedPlay,
                                onCheckedChange = { forcedPlay = it },
                                enabled = !isStreaming && enabledAudio,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.mirror_forced),
                                fontSize = 10.sp,
                                color = if (forcedPlay && enabledAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    // ===== BUTTON =====
                    Button(
                        onClick = { showControls = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.mirror_apply_close),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }


        // ===== HEADER (DỌC) =====
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(1f)
        ) {

            val topMenuItems = listOf(
                HeaderMenuModel(
                    icon = "baseline_stop",
                    isDestructive = streamState.isConnected,
                    isVisible = { true },
                    onClick = {
                        inputManager.sendStringMessage("mirror stop")
                        viewModel.stopStream()
                    }
                ),
                HeaderMenuModel(
                    icon = if (isStreaming) "baseline_pause" else "baseline_play_arrow",
                    onClick = {
                        if (streamState.isBluetooth) {
                            context.showToastLong(context.getStringSafe(R.string.mirror_function_not_available_bt))
                        } else {
                            viewModel.startStream(
                                VideoConfigModel(
                                    udpPort = udpPort.toIntOrNull() ?: 5050,
                                    width = VIDEO_PRESETS[selectedVideoConfigIndex].width,
                                    height = VIDEO_PRESETS[selectedVideoConfigIndex].height,
                                    fps = VIDEO_PRESETS[selectedVideoConfigIndex].fps,
                                    quality = VIDEO_PRESETS[selectedVideoConfigIndex].quality,
                                    surface = surfaceViewInstance?.holder?.surface
                                ),
                                AudioConfigModel(
                                    udpPort = (udpPort.toIntOrNull() ?: 5050) + 1,
                                    enabled = enabledAudio,
                                    forcedPlay = forcedPlay,
                                    rmsThreshold = rmsThreshold,
                                    sampleRate = AUDIO_PRESETS[selectedAudioConfigIndex].sampleRate,
                                    channels = AUDIO_PRESETS[selectedAudioConfigIndex].channels,
                                    bitDepth = AUDIO_PRESETS[selectedAudioConfigIndex].bitDepth,
                                    codec = AUDIO_PRESETS[selectedAudioConfigIndex].codec
                                ),
                                inputManager = inputManager
                            )
                        }
                    },
                    enabled = isConnected && !isStreaming
                ),
                HeaderMenuModel(
                    isStatusDot = true,
                    isConnected = streamState.isConnected,
                    textColor = when {
                        isConnected && isStreaming -> Color(0xFF4CAF50)
                        isConnected -> Color(0xFFFFA500)
                        else -> errorColor
                    },
                    isVisible = { true },
                    modifier = Modifier.size(10.dp)
                ),
                HeaderMenuModel(
                    text = when {
                        isConnected && isStreaming -> stringResource(R.string.mirror_status_live)
                        isConnected -> if (isStreaming) stringResource(R.string.mirror_status_live) else stringResource(R.string.mirror_status_ready)
                        else -> stringResource(R.string.mirror_status_off)
                    },
                    textColor = when {
                        isConnected && isStreaming -> Color(0xFF4CAF50)
                        isConnected -> if (isStreaming) Color(0xFFFFA500) else Color.White
                        else -> errorColor
                    },
                    textFontSize = 10,
                    isVisible = { true },
                    modifier = Modifier.padding(vertical = 5.dp)
                ),
                HeaderMenuModel(
                    text = when (audioState) {
                        ReceiverState.PLAYING -> "🔊"
                        else -> "🔇"
                    },
                    textColor = when (audioState) {
                        ReceiverState.PLAYING -> Color(0xFF00E676)
                        ReceiverState.IDLE -> Color(0xFFFFC107)
                        ReceiverState.SLEEP -> Color(0xFF90A4AE)
                    }.copy(alpha = 0.7f),
                    textFontSize = 12,
                    isVisible = { true },
                )
            )

            val bottomMenuItems = listOf(
                HeaderMenuModel(
                    icon = if (showControls) "baseline_settings" else "outline_settings",
                    onClick = { toggleControls() },
                    isVisible = { true },
                    modifier = Modifier.size(18.dp)
                ),
                HeaderMenuModel(
                    icon = "baseline_settings_input_composite",
                    onClick = { isSetHotspot = !isSetHotspot },
                    iconTint = if (isSetHotspot) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.Gray,
                    isVisible = { true },
                    modifier = Modifier.size(18.dp)
                ),
                HeaderMenuModel(
                    icon = "baseline_add",
                    onClick = {
                        if (zoomScale < 4.0f) {
                            zoomScale += 0.5f
                        }
                    },
                    enabled = isStreaming,
                    iconTint = if (isStreaming) Color.White else Color.Gray,
                    isVisible = { true },
                ),
                HeaderMenuModel(
                    text = "${zoomScale}x",
                    textFontSize = 11,
                    enabled = isStreaming,
                    isVisible = { true },
                    textColor = if (isStreaming) Color.White else Color.Gray,
                ),
                HeaderMenuModel(
                    icon = "baseline_remove",
                    onClick = {
                        if (zoomScale > 1.0f) {
                            zoomScale -= 0.5f
                            if (zoomScale == 1.0f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                    iconTint = if (isStreaming) Color.White else Color.Gray,
                    enabled = isStreaming,
                    isVisible = { true },
                ),
                HeaderMenuModel(
                    icon = "baseline_close",
                    onClick = {
                        zoomScale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        view.playSoundClick()
                    },
                    isVisible = { zoomScale > 1f },
                ),
                HeaderMenuModel(
                    icon = "baseline_refresh",
                    onClick = {
                        if (streamState.isBluetooth) {
                            context.showToastLong(context.getStringSafe(R.string.mirror_function_not_available_bt))
                        } else {
                            connection.pingStream()
                        }
                    },
                    enabled = !isStreaming,
                    iconTint = if (!isStreaming) Color.White else Color.Gray,
                    isVisible = { true },
                )
            )

            HeaderVertical(
                view = view,
                modifier = Modifier.fillMaxHeight(),
                color = Color.Black.copy(alpha = 0.2f),
                topItems = topMenuItems,
                bottomItems = bottomMenuItems
            )
        }


        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .zIndex(2f)
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {

                IconButton(
                    onClick = {
                        view.playSoundClick()
                        toggleHeader()
                    },
                ) {

                    Icon(
                        painterResource(if (isHeaderVisible) R.drawable.baseline_keyboard_double_arrow_down else R.drawable.baseline_keyboard_double_arrow_up),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun EmptyStreamView(
    isConnected: Boolean,
    isStreaming: Boolean,
    rotationAngle: Float = 90f
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotationAngle },
        ) {
            Icon(
                painterResource(R.drawable.baseline_videocam_off),
                null,
                Modifier.size(100.dp),
                tint = if (isConnected) Color.White else Color.Gray
            )
            Text(
                if (isStreaming) stringResource(R.string.mirror_waiting_stream) else stringResource(
                    R.string.mirror_connecting
                ), color = if (isConnected) Color.White else Color.Gray
            )
        }
    }
}


fun mapDecoderRotatedMouse(
    xRatio: Float,
    yRatio: Float,
    videoDrawW: Float,
    videoDrawH: Float,
    rotationAngle: Float
): Offset {
    val clampedX = xRatio.coerceIn(0f, 1f)
    val clampedY = yRatio.coerceIn(0f, 1f)

    return when (rotationAngle) {
        90f -> Offset(x = (1f - clampedY) * videoDrawW, y = clampedX * videoDrawH)
        180f -> Offset(x = (1f - clampedX) * videoDrawW, y = (1f - clampedY) * videoDrawH)
        270f -> Offset(x = clampedY * videoDrawW, y = (1f - clampedX) * videoDrawH)
        else -> Offset(x = clampedX * videoDrawW, y = clampedY * videoDrawH)
    }
}


fun getMouseIcon(shape: Int): Int {
    return when (shape) {
        0 -> R.drawable.outline_arrow_selector_tool          // Mũi tên
        1 -> R.drawable.baseline_open_with          // Di chuyển (4 mũi tên)
        2 -> R.drawable.baseline_touch_app            // Bàn tay
        3 -> R.drawable.baseline_hourglass_full      // Chờ (đồng hồ cát)
        4 -> R.drawable.outline_text_format        // I-beam (chọn văn bản)
        5 -> R.drawable.baseline_open_in_full         // Resize chéo
        6 -> R.drawable.baseline_add                // Chữ thập (crosshair)
        7 -> R.drawable.baseline_unfold_more        // Resize ngang (xoay 90° nếu cần)
        8 -> R.drawable.baseline_unfold_more           // Resize dọc
        13 -> R.drawable.baseline_arrow_upward      // Mũi tên lên
        else -> R.drawable.outline_arrow_selector_tool       // Mặc định
    }
}