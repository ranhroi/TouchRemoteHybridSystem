package com.ranhroi.touchremote.ui.screens.remote

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class RemoteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStoreManager
) : ViewModel() {

    val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    // Danh sách remote có sẵn
    val availableRemotes: List<RemoteModel> by lazy {
        RemoteProfileMapper.getAllRemoteModels()
    }

    // Remote hiện tại
    private val _currentRemote = MutableStateFlow<RemoteModel?>(null)
    val currentRemote: StateFlow<RemoteModel?> = _currentRemote.asStateFlow()

    // Các state lưu trong DataStore
    val remoteId: StateFlow<String> = dataStore.get(ConstantsUtils.REMOTE_ID, "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isOn: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_ON, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val temp: StateFlow<Int> = dataStore.get(ConstantsUtils.TEMP, 24)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24)

    val mode: StateFlow<String> = dataStore.get(ConstantsUtils.MODE, "COOL")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "COOL")

    val fanSpeed: StateFlow<String> = dataStore.get(ConstantsUtils.FAN_SPEED, "AUTO")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    val isSwing: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_SWING, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isVerticalStep = MutableStateFlow(false)
    val isVerticalStep: StateFlow<Boolean> = _isVerticalStep.asStateFlow()

    val isCoilDry: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_COIL_DRY, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAirClean: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_AIR_CLEAN, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isSleep: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_SLEEP, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val timerMode: StateFlow<String> = dataStore.get(ConstantsUtils.TIMER_MODE, "NONE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NONE")

    val timerHour: StateFlow<Int> = dataStore.get(ConstantsUtils.TIMER_HOUR, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isQuiet: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_QUIET, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPowerful: StateFlow<Boolean> = dataStore.get(ConstantsUtils.IS_POWERFUL, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            RemoteCodeMap.initialize(context)
            delay(5000.milliseconds)
            val savedId = remoteId.value
            val remote = if (savedId.isNotEmpty()) {
                availableRemotes.find { it.id == savedId }
            } else {
                availableRemotes.firstOrNull()
            }
            remote?.let {
                _currentRemote.value = it
                if (savedId.isEmpty()) {
                    dataStore.put(ConstantsUtils.REMOTE_ID, it.id)
                }
            }
        }
    }

    // ===== Điều khiển =====
    fun selectRemote(remoteId: String) {
        val remote = availableRemotes.find { it.id == remoteId }
        if (remote != null) {
            _currentRemote.value = remote
            viewModelScope.launch {
                dataStore.put(ConstantsUtils.REMOTE_ID, remoteId)
                if (temp.value < remote.minTemp) updateTemp(remote.minTemp)
                if (temp.value > remote.maxTemp) updateTemp(remote.maxTemp)
            }
        }
    }

    fun togglePower() {
        viewModelScope.launch {
            val newState = !isOn.value
            dataStore.put(ConstantsUtils.IS_ON, newState)
            if (!newState) {
                if (isQuiet.value) dataStore.put(ConstantsUtils.IS_QUIET, false)
                if (isPowerful.value) dataStore.put(ConstantsUtils.IS_POWERFUL, false)
                if (isSwing.value) dataStore.put(ConstantsUtils.IS_SWING, false)
                if (isSleep.value) dataStore.put(ConstantsUtils.IS_SLEEP, false)
            }
            transmitCurrentState()
        }
    }

    fun updateTemp(newTemp: Int) {
        val current = _currentRemote.value ?: return
        val clamped = newTemp.coerceIn(current.minTemp, current.maxTemp)
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.TEMP, clamped)
            transmitCurrentState()
        }
    }

    // ===== VANE DIRECTION CONTROL =====
    fun triggerSwingStepVertical() {
        viewModelScope.launch {
            _isVerticalStep.value = true
            if (isSwing.value) {
                dataStore.put(ConstantsUtils.IS_SWING, false) // Tắt trạng thái SWING trong DataStore
            }
            transmitCurrentState()
        }
    }

    fun triggerSwingStepHorizontal() {
        viewModelScope.launch {
            _isVerticalStep.value = false
            // Tương tự, bấm chỉnh nấc ngang sẽ tự động hủy chế độ quay tự động
            if (isSwing.value) {
                dataStore.put(ConstantsUtils.IS_SWING, false)
            }
            transmitCurrentState()
        }
    }

    fun toggleSwing() {
        viewModelScope.launch {
            val newSwingState = !isSwing.value
            dataStore.put(ConstantsUtils.IS_SWING, newSwingState)
            if (newSwingState) {
                dataStore.put(ConstantsUtils.VANE_DIRECTION, 0)
            }
            transmitCurrentState()
        }
    }

    // ===== COIL DRY =====
    fun toggleCoilDry() {
        viewModelScope.launch {
            val currentMode = mode.value
            when (currentMode) {
                "COOL", "DRY" -> {
                    dataStore.put(ConstantsUtils.IS_COIL_DRY, !isCoilDry.value)
                    transmitCurrentState()
                }
                "AUTO" -> {
                    dataStore.put(ConstantsUtils.IS_COIL_DRY, !isCoilDry.value)
                    transmitCurrentState()
                }
                else -> {
                }
            }
        }
    }

    // ===== AIR CLEAN =====
    fun toggleAirClean() {
        viewModelScope.launch {
            val currentMode = mode.value
            when {
                currentMode == "HEAT" -> {
                }
                else -> {
                    dataStore.put(ConstantsUtils.IS_AIR_CLEAN, !isAirClean.value)
                    transmitCurrentState()
                }
            }
        }
    }

    // ===== CHANGE MODE =====
    fun changeMode() {
        viewModelScope.launch {
            val nextMode = when (mode.value) {
                "COOL" -> "DRY"
                "DRY" -> "FAN"
                "FAN" -> "AUTO"
                "AUTO" -> "HEAT"
                "HEAT" -> "COOL"
                else -> "COOL"
            }
            dataStore.put(ConstantsUtils.MODE, nextMode)

            if (nextMode != "COOL" && nextMode != "DRY" && nextMode != "AUTO" && isCoilDry.value) {
                dataStore.put(ConstantsUtils.IS_COIL_DRY, false)
            }
            if (nextMode == "HEAT" && isAirClean.value) {
                dataStore.put(ConstantsUtils.IS_AIR_CLEAN, false)
            }

            transmitCurrentState()
        }
    }

    fun toggleSleep() {
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.IS_SLEEP, !isSleep.value)
            transmitCurrentState()
        }
    }

    // ===== POWERFUL MODE =====
    fun togglePowerful() {
        viewModelScope.launch {
            val next = !isPowerful.value

            if (next) {
                dataStore.put(ConstantsUtils.IS_POWERFUL, true)
                if (isQuiet.value) {
                    dataStore.put(ConstantsUtils.IS_QUIET, false)
                }
                if (fanSpeed.value == "AUTO" || fanSpeed.value == "LOW") {
                    dataStore.put(ConstantsUtils.FAN_SPEED, "HIGH")
                }
            } else {
                dataStore.put(ConstantsUtils.IS_POWERFUL, false)
            }

            transmitCurrentState()
        }
    }

    // ===== QUIET MODE =====
    fun toggleQuiet() {
        viewModelScope.launch {
            val next = !isQuiet.value

            if (next) {
                dataStore.put(ConstantsUtils.IS_QUIET, true)
                if (isPowerful.value) {
                    dataStore.put(ConstantsUtils.IS_POWERFUL, false)
                }
                if (fanSpeed.value != "QUIET") {
                    dataStore.put(ConstantsUtils.FAN_SPEED, "QUIET")
                }
            } else {
                dataStore.put(ConstantsUtils.IS_QUIET, false)
                if (fanSpeed.value == "QUIET") {
                    dataStore.put(ConstantsUtils.FAN_SPEED, "AUTO")
                }
            }
            transmitCurrentState()
        }
    }

    // ===== CHANGE FAN SPEED =====
    fun changeFanSpeed() {
        viewModelScope.launch {
            val next = when (fanSpeed.value) {
                "AUTO" -> "HIGH"
                "HIGH" -> "MED"
                "MED" -> "LOW"
                "LOW" -> "QUIET"
                "QUIET" -> "AUTO"
                else -> "AUTO"
            }

            dataStore.put(ConstantsUtils.FAN_SPEED, next)

            when (next) {
                "QUIET" -> {
                    if (!isQuiet.value) {
                        dataStore.put(ConstantsUtils.IS_QUIET, true)
                    }
                    if (isPowerful.value) {
                        dataStore.put(ConstantsUtils.IS_POWERFUL, false)
                    }
                }
                "HIGH", "MED", "LOW", "AUTO" -> {
                    if (isQuiet.value) {
                        dataStore.put(ConstantsUtils.IS_QUIET, false)
                    }
                }
            }

            transmitCurrentState()
        }
    }

    // ===== TIMER CONTROLS =====
    fun changeTimerMode() {
        viewModelScope.launch {
            val next = when (timerMode.value) {
                "NONE" -> "ON"
                "ON" -> "OFF"
                else -> "NONE"
            }
            dataStore.put(ConstantsUtils.TIMER_MODE, next)
            transmitCurrentState()
        }
    }

    fun increaseTimerHour() {
        viewModelScope.launch {
            if (timerHour.value < 12 && timerMode.value == "ON") {
                dataStore.put(ConstantsUtils.TIMER_HOUR, timerHour.value + 1)
                transmitCurrentState()
            }
        }
    }

    fun decreaseTimerHour() {
        viewModelScope.launch {
            if (timerHour.value > 0 && timerMode.value == "ON") {
                dataStore.put(ConstantsUtils.TIMER_HOUR, timerHour.value - 1)
                transmitCurrentState()
            }
        }
    }

    // ===== GỬI TÍN HIỆU =====
    fun transmitCurrentState() {
        val currentModel = _currentRemote.value ?: return
        val state = RemoteState(
            isOn = isOn.value,
            temp = temp.value,
            mode = mode.value,
            fanSpeed = fanSpeed.value,
            isSwing = isSwing.value,
            isCoilDry = isCoilDry.value,
            isAirClean = isAirClean.value,
            isSleep = isSleep.value,
            timerMode = timerMode.value,
            timerHour = timerHour.value,
            isQuiet = isQuiet.value,
            isPowerful = isPowerful.value
        )

        // Ưu tiên tìm trong JSON
        val command = RemoteCodeMap.findMatchingCommand(currentModel.id, state)
        if (command != null) {
            sendIrCommand(command.frequency, command.pattern)
            return
        }

        // Fallback: dùng encoder cứng
        val fallbackPattern = FujitsuIrEncoder.generateFujitsuCommand(
            remoteModel = currentModel,
            isOn = state.isOn,
            temp = state.temp,
            mode = state.mode,
            fan = state.fanSpeed,
            swingActive = state.isSwing,
            isVerticalStep = isVerticalStep.value,
            isCoilDry = state.isCoilDry,
            isAirClean = state.isAirClean,
            isSleep = state.isSleep,
            timerMode = state.timerMode,
            timerHour = state.timerHour,
            isQuiet = state.isQuiet,
            isPowerful = state.isPowerful
        )
        sendIrCommand(38000, fallbackPattern)
    }

    private fun sendIrCommand(frequency: Int, pattern: IntArray) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            return
        }
        try {
            irManager.transmit(frequency, pattern)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}