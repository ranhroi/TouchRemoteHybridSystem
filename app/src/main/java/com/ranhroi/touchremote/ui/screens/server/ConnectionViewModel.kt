package com.ranhroi.touchremote.ui.screens.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.send.SenderOutput
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.ConstantsUtils.FIRST_LAUNCH
import com.ranhroi.touchremote.utils.ConstantsUtils.getSystemInfoInt
import com.ranhroi.touchremote.utils.DataStoreConfig
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketException
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ConnectionVM"

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val senderOutput: SenderOutput,
    private val dataStore: DataStoreManager,
    private val dataStoreConfig: DataStoreConfig,
    private val connection: ConnectionManager,
) : ViewModel() {

    // ============================================================
    // CONFIG
    // ============================================================

    val config = dataStoreConfig.combine.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(500),
        CombineSettingModel()
    )

    // ============================================================
    // STATE
    // ============================================================

    private val _hasServers = MutableStateFlow(false)
    val hasServers: StateFlow<Boolean> = _hasServers.asStateFlow()

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers.asStateFlow()

    private val _uiState = MutableStateFlow(ConnectionUiModel())
    val uiState: StateFlow<ConnectionUiModel> = _uiState.asStateFlow()

    private val _connections = MutableStateFlow<Map<Byte, HybridConnectionModel>>(emptyMap())
    val connections: StateFlow<Map<Byte, HybridConnectionModel>> = _connections.asStateFlow()

    private var commandListener: ConnectionManager.Listener? = null

    val getSelectedDevice: StateFlow<DeviceServerModel> =
        dataStore.getSelectedDevice().filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(300),
                initialValue = DeviceServerModel()
            )

    private val _inputState = MutableStateFlow(
        InputManager(senderOutput) { config.value }
    )
    val inputState: StateFlow<InputManager> = _inputState.asStateFlow()

    // ============================================================
    // INIT
    // ============================================================

    init {
        loadServers()
        setupConnectionListener()
        startWithConfig()
    }

    // ============================================================
    // LOAD SERVERS
    // ============================================================
    private fun loadServers() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            try {
                dataStore.getAllDevices().collect { servers ->
                    _uiState.update { it.copy(servers = servers) }
                    _hasServers.value = servers.isNotEmpty()
                    _isLoadingServers.value = false
                }
            } catch (e: Exception) {
                _hasServers.value = false
                _isLoadingServers.value = false
            }
        }
    }

    // ============================================================
    // CONFIG & STARTUP
    // ============================================================

    private fun startWithConfig() {
        viewModelScope.launch {
            try {
                val currentConfig = config.first()
                val selectedDevice = getSelectedDevice.first()

                if (currentConfig.isAutoConnection && selectedDevice.id != 0) {
                    connectToServer(selectedDevice)
                } else {
                    _uiState.update {
                        it.copy(
                            statusMessage = context.getString(R.string.connection_auto_failed)
                        )
                    }
                    if (selectedDevice.id == 0) showNoServerMessage()
                }
            } catch (e: Exception) {
            }
        }
    }

    // ============================================================
    // CONNECTION LISTENER
    // ============================================================

    private fun setupConnectionListener() {
        commandListener?.let { connection.unregisterListener(it) }
        commandListener = null

        connection.setScope(viewModelScope)

        commandListener = object : ConnectionManager.Listener {

            override fun onConnectResult(role: Byte, state: HybridConnectionModel) {
                updateConnectionMap(role, state)

                when (role) {
                    ConnectionManager.ROLE_COMMAND,
                    ConnectionManager.ROLE_TRANSFER,
                    ConnectionManager.ROLE_STREAM -> {
                        viewModelScope.launch {
                            if (state.isConnected) {
                                handleConnectSuccess(role, state)
                            } else {
                                handleConnectFailure(role, state)
                            }
                        }
                    }
                }
            }

            override fun onNeedPassword(role: Byte, isNeedPassword: Boolean) {
                if (!isNeedPassword) {
                    // Server không cần password → thành công luôn
                    showConnectionSuccess()
                    refreshConnectionState(role)
                    return
                }

                // Server cần password → thử password đã lưu (1 lần)
                viewModelScope.launch {
                    val savedPassword = getSelectedDevice.first().password
                    if (savedPassword.isEmpty()) {
                        // Không có password → hiện dialog
                        showPasswordDialog()
                    } else {
                        // Có password → verify 1 lần
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                showPasswordDialog = false,
                                errorMessage = null,
                                statusMessage = context.getString(R.string.connection_auth_auto_retry)
                            )
                        }
                        // Kết quả sẽ được xử lý trong onVerifyResult — KHÔNG làm gì thêm ở đây
                        connection.verifyPassword(savedPassword, role)
                    }
                }
            }

            override fun onVerifyResult(role: Byte, success: Boolean) {
                if (success) {
                    // ✅ ĐÚNG → ẩn dialog ngay
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConnected = true,
                            showPasswordDialog = false,      // ← ẨN
                            errorMessage = null,
                            statusMessage = context.getString(R.string.connection_auth_success)
                        )
                    }
                    refreshConnectionState(role)
                } else {
                    // ❌ SAI → hiện dialog, báo lỗi, chờ user nhập lại
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConnected = true,              // socket vẫn còn sống
                            showPasswordDialog = true,       // ← HIỆN
                            errorMessage = context.getString(R.string.dialog_verify_invalid_password)
                        )
                    }
                }
            }

            override fun onPay(role: Byte, isPay: Boolean) {}
        }

        commandListener?.let { connection.registerListener(it) }
    }

    // ═══════════════════════════════════════════════════════════════
    //  XỬ LÝ KẾT NỐI
    // ═══════════════════════════════════════════════════════════════

    private suspend fun handleConnectSuccess(role: Byte, state: HybridConnectionModel) {
        when (role) {
            ConnectionManager.ROLE_COMMAND,
            ConnectionManager.ROLE_STREAM -> {
                senderOutput.refreshConfig(config.value, state)
                saveDeviceOsInfo(state)
                updateUiStateWithConnection(state)
                updateOsDataStore(getSystemInfoInt(state.os))
                if (state.os != "unknown") updateSystemInfo(state.os)

                // Ping im lặng sau khi state UI đã ổn định — không update UI lần 2
                pingRoleReadySilent(role)
            }
        }
    }

    private suspend fun saveDeviceOsInfo(state: HybridConnectionModel) {
        dataStore.updateDeviceOS(state.server.id, state.os)
    }

    private fun updateUiStateWithConnection(state: HybridConnectionModel) {
        _uiState.update { it.copy(state = state, password = state.server.password) }
    }

    private fun handleConnectFailure(role: Byte, state: HybridConnectionModel) {
        if (role == ConnectionManager.ROLE_COMMAND) {
            showConnectionFailure(state.server.name)
        }
        removeConnectionFromMap(role)
    }

    private fun showConnectionFailure(serverName: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isConnected = false,
                showPasswordDialog = false,
                errorMessage = context.getString(R.string.connection_failed, serverName)
            )
        }
    }

    private fun showConnectionSuccess() {
        _uiState.update {
            it.copy(
                isLoading = false,
                isConnected = true,
                showPasswordDialog = false,
                statusMessage = context.getString(R.string.connection_success)
            )
        }
    }

    private fun showPasswordDialog() {
        _uiState.update {
            it.copy(
                isLoading = false,
                isConnected = true,
                showPasswordDialog = true,
                errorMessage = context.getString(R.string.dialog_verify_invalid_password)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  XỬ LÝ XÁC THỰC — VERIFY 1 LẦN, SAI THÌ HIỆN DIALOG
    // ═══════════════════════════════════════════════════════════════

    private fun handleVerifySuccess(role: Byte) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isConnected = true,
                showPasswordDialog = false,
                errorMessage = null,
                statusMessage = context.getString(R.string.connection_auth_success)
            )
        }
    }

    /**
     * Thử verify bằng password đã lưu (nếu có).
     * - Có password → verify đúng 1 lần.
     *     + Đúng → onVerifyResult(true) sẽ đóng dialog.
     *     + Sai  → showPasswordDialog() cho user nhập lại.
     * - Không có password → showPasswordDialog() ngay.
     *
     * KHÔNG tự retry — không loop.
     */
    private fun tryVerifyWithSavedPassword(role: Byte) {
        viewModelScope.launch {
            val savedPassword = getSelectedDevice.first().password

            if (savedPassword.isEmpty()) {
                showPasswordDialog()
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    showPasswordDialog = false,
                    errorMessage = null,
                    statusMessage = context.getString(R.string.connection_auth_auto_retry)
                )
            }

            val success = connection.verifyPassword(savedPassword, role)

            if (!success) {
                // Sai → hiện dialog. onVerifyResult(false) cũng sẽ gọi showPasswordDialog()
                // (idempotent — set cùng state nên an toàn).
                showPasswordDialog()
            }
            // Nếu success = true → để onVerifyResult(true) xử lý (handleVerifySuccess).
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  QUẢN LÝ MAP KẾT NỐI
    // ═══════════════════════════════════════════════════════════════

    private fun updateConnectionMap(role: Byte, state: HybridConnectionModel) {
        _connections.value = _connections.value.toMutableMap().apply {
            put(role, state.copy())
        }
    }

    private fun removeConnectionFromMap(role: Byte) {
        _connections.value = _connections.value.toMutableMap().apply {
            remove(role)
        }
    }

    private fun refreshConnectionState(role: Byte) {
        val latestState = connection.getConnectionState(role)
        _connections.value = _connections.value.toMutableMap().apply {
            put(role, latestState.copy())
        }
    }

    private fun getRoleName(role: Byte): String {
        return when (role) {
            ConnectionManager.ROLE_COMMAND -> "Command"
            ConnectionManager.ROLE_STREAM -> "Stream"
            ConnectionManager.ROLE_TRANSFER -> "Transfer"
            else -> "Unknown"
        }
    }

    // ============================================================
    // GET CONNECTION STATE
    // ============================================================

    fun getConnectionState(role: Byte): HybridConnectionModel {
        return _connections.value[role] ?: HybridConnectionModel()
    }

    fun getCommandState(): HybridConnectionModel =
        getConnectionState(ConnectionManager.ROLE_COMMAND)

    fun getStreamState(): HybridConnectionModel =
        getConnectionState(ConnectionManager.ROLE_STREAM)

    fun getTransferState(): HybridConnectionModel =
        getConnectionState(ConnectionManager.ROLE_TRANSFER)

    // ============================================================
    // CONNECT / DISCONNECT
    // ============================================================

    fun connectToServer(server: DeviceServerModel, role: Byte = ConnectionManager.ROLE_COMMAND) {
        viewModelScope.launch {
            val roleName = getRoleName(role)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isConnected = false,
                    showPasswordDialog = false,   // reset dialog mỗi lần connect mới
                    errorMessage = null,          // reset lỗi cũ
                    statusMessage = context.getString(
                        R.string.connection_connecting_role,
                        server.name,
                        roleName
                    )
                )
            }

            connection.connect(server, role)
            dataStore.setSelectedDevice(server.id)
        }
    }

    fun disconnectRole(role: Byte = ConnectionManager.ROLE_COMMAND) {
        connection.close(role)

        _connections.value = _connections.value.toMutableMap().apply {
            put(role, HybridConnectionModel())
        }

        if (role == ConnectionManager.ROLE_COMMAND) {
            _uiState.update {
                it.copy(
                    isConnected = false,
                    isLoading = false,
                    showPasswordDialog = false,
                    statusMessage = context.getString(R.string.connection_disconnected),
                    state = getConnectionState(role)
                )
            }
        }
    }

    fun disconnectAll() {
        connection.closeAll()
        _connections.value = emptyMap()

        _uiState.update {
            it.copy(
                isConnected = false,
                isLoading = false,
                showPasswordDialog = false,
                statusMessage = context.getString(R.string.connection_disconnected),
                state = HybridConnectionModel()
            )
        }
    }

    // ============================================================
    // PASSWORD HANDLER
    // ============================================================

    /**
     * User nhập password trong dialog.
     * - Đúng → lưu/xóa password theo lựa chọn; onVerifyResult(true) đóng dialog.
     * - Sai  → giữ dialog, báo lỗi, chờ user nhập lại.
     */
    fun onPasswordEntered(
        currentServer: DeviceServerModel,
        password: String,
        rememberPass: Boolean
    ) {
        viewModelScope.launch {
            // Chỉ set loading — dialog đã hiện rồi, không cần set lại
            _uiState.update {
                it.copy(
                    isLoading = true,
                    password = password,
                    errorMessage = null,
                    statusMessage = context.getString(R.string.connection_checking_password)
                )
            }

            // ✅ KHÔNG set showPasswordDialog ở đây
            // ✅ KHÔNG set isLoading = false ở đây
            // → onVerifyResult sẽ làm cả hai sau khi verify xong

            val success = connection.verifyPassword(password, ConnectionManager.ROLE_COMMAND)

            if (success) {
                // Lưu password theo lựa chọn
                if (rememberPass) {
                    dataStore.saveDevice(
                        currentServer.copy(password = password, rememberPass = true)
                    )
                } else {
                    dataStore.saveDevice(
                        currentServer.copy(password = "", rememberPass = false)
                    )
                }
                // onVerifyResult(true) đã ẩn dialog
            }
            // Nếu sai → onVerifyResult(false) đã hiện dialog + báo lỗi
        }
    }

    // ============================================================
    // PING ROLE ONE - GỬI TÍN HIỆU SẴN SÀNG (IM LẶNG)
    // ============================================================

    /**
     * Gửi "ping" để thông báo client đã sẵn sàng.
     * KHÔNG update UI — UI đã được handleConnectSuccess set trước đó.
     * Tránh nhấp nháy do update state nhiều lần.
     */
    private fun pingRoleReadySilent(role: Byte) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = getConnectionState(role)
                if (!state.isConnected || state.output == null) return@launch
                state.output!!.apply {
                    write("ping\n".toByteArray())
                    flush()
                }
            } catch (_: SocketException) {
                // ignore — ping fail không ảnh hưởng UI
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    // ============================================================
    // PING
    // ============================================================

    fun pingRole(role: Byte = ConnectionManager.ROLE_COMMAND) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = getConnectionState(role)
            val roleName = getRoleName(role)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        statusMessage = context.getString(R.string.connection_pinging, roleName),
                        isLoading = true
                    )
                }
            }

            if (!state.isConnected || state.output == null) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            statusMessage = context.getString(
                                R.string.connection_not_connected_role,
                                roleName
                            ),
                            isLoading = false,
                            isConnected = false
                        )
                    }
                }
                attemptReconnection(
                    role,
                    context.getString(R.string.connection_reconnecting_role, roleName)
                )
                return@launch
            }

            try {
                state.output!!.apply {
                    write("ping\n".toByteArray())
                    flush()
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            statusMessage = context.getString(
                                R.string.connection_stable_role,
                                roleName
                            ),
                            errorMessage = null,
                            isConnected = true,
                            isLoading = false
                        )
                    }
                }

                when (role) {
                    ConnectionManager.ROLE_COMMAND,
                    ConnectionManager.ROLE_STREAM -> {
                        senderOutput.updateSystemInfo(getSystemInfo())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            statusMessage = context.getString(
                                R.string.connection_ping_failed_role,
                                roleName
                            ),
                            isLoading = false,
                            isConnected = false
                        )
                    }
                }
                attemptReconnection(
                    role,
                    context.getString(R.string.connection_reconnecting_role, roleName)
                )
            }
        }
    }

    fun pingCommand() = pingRole(ConnectionManager.ROLE_COMMAND)
    fun pingStream() = pingRole(ConnectionManager.ROLE_STREAM)
    fun pingTransfer() = pingRole(ConnectionManager.ROLE_TRANSFER)

    // ============================================================
    // NETWORK
    // ============================================================

    fun checkNetwork(isWifiOn: Boolean, isBluetoothOn: Boolean) {
        if (!isWifiOn && !isBluetoothOn) {
            disconnectAll()
            _uiState.update {
                it.copy(
                    errorMessage = context.getString(R.string.connection_wifi_bluetooth_off),
                    statusMessage = context.getString(R.string.connection_enable_network)
                )
            }
            return
        }
        startWithConfig()
    }

    private fun attemptReconnection(role: Byte, message: String) {
        viewModelScope.launch {
            try {
                val roleName = getRoleName(role)

                _uiState.update {
                    it.copy(
                        statusMessage = message,
                        isLoading = true,
                        isConnected = false,
                        errorMessage = null
                    )
                }

                disconnectAll()
                delay(500.milliseconds)

                val device = dataStore.getSelectedDevice().filterNotNull().first()

                _uiState.update {
                    it.copy(
                        statusMessage = context.getString(
                            R.string.connection_reconnecting_role,
                            roleName
                        ),
                        isLoading = true
                    )
                }

                connectToServer(device, role)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConnected = false,
                        statusMessage = context.getString(
                            R.string.connection_reconnect_failed
                        ),
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private fun showNoServerMessage() {
        _uiState.update {
            it.copy(
                statusMessage = context.getString(R.string.connection_no_server),
                errorMessage = context.getString(R.string.connection_select_server)
            )
        }
    }

    fun dismissPasswordDialog() {
        _uiState.update {
            it.copy(
                showPasswordDialog = false,
                statusMessage = context.getString(R.string.connection_cancelled),
            )
        }
    }

    fun updateRotation(isRotation: Boolean) {
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.SHOW_ROTATION, isRotation)
        }
    }

    private suspend fun updateSystemInfo(systemInfo: String) {
        dataStore.put(ConstantsUtils.CURRENT_SYSTEM_OS, systemInfo)
    }

    private suspend fun getSystemInfo(): String {
        return dataStore.get(ConstantsUtils.CURRENT_SYSTEM_OS, "unknown").first()
    }

    fun updateOsDataStore(os: Int) {
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.OS_INDEX, os)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            dataStore.put(FIRST_LAUNCH, true)
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    fun cleared() {
        _inputState.value.destroy()
        senderOutput.destroy()

        disconnectAll()
        commandListener?.let { connection.unregisterListener(it) }
        commandListener = null
        connection.unregisterAllListeners()
        connection.closeAll()
    }

    override fun onCleared() {
        cleared()
        super.onCleared()
    }
}