package com.ranhroi.touchremote.ui.screens.shortkey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.ui.screens.shortkey.ShortKeysRepository
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShortKeySetupViewModel @Inject constructor(
    private val dataStore: DataStoreManager
) : ViewModel() {

    val allShortKeys: StateFlow<List<ShortKeyModel>> = dataStore.getAllShortKeys()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedKey = MutableStateFlow<String?>(null)
    val selectedKey: StateFlow<String?> = _selectedKey.asStateFlow()

    private val _isManualSelected = MutableStateFlow(false)
    val isManualSelected: StateFlow<Boolean> = _isManualSelected.asStateFlow()


    private val mediaAppManager = MediaAppManager()
    private val mediaAppHandler = MediaAppHandler(viewModelScope)

    private val _detectedAppType = MutableStateFlow<String?>(null)
    val detectedAppType: StateFlow<String?> = _detectedAppType.asStateFlow()

    init {
        checkAndInitDefaultShortKeys()

        mediaAppHandler.register(object : MediaAppHandler.Callback {
            override fun onAppId(appId: String) {
                val savedWebPlayer = ConstantsUtils.MEDIA_TYPE_YOUTUBE_WEB // hoặc lấy từ DataStore
                val type = mediaAppManager.detectAppType(appId, savedWebPlayer)
                onAppDetected(type)
            }
            override fun onError(e: Exception) {
                // log
            }
        })
    }

    /**
     * Khởi tạo các phím tắt mặc định nếu DataStore trống
     */
    private fun checkAndInitDefaultShortKeys() {
        viewModelScope.launch {
            // Lấy danh sách hiện tại (chỉ lấy 1 lần đầu để kiểm tra)
            val currentShortKeys = dataStore.getAllShortKeys().first()

            if (currentShortKeys.isEmpty()) {
                dataStore.addShortKeyList(ShortKeysRepository.shortKeysDefault)
            }
        }
    }

    fun getShortKeysByType(type: String): List<ShortKeyModel> =
        allShortKeys.value.filter { it.type == type }

    // ============= ShortKey Management =============

    /**
     * Thêm mới hoặc Cập nhật phím tắt
     */
    fun addShortKey(shortKey: ShortKeyModel) {
        viewModelScope.launch {
            try {
                dataStore.saveShortKey(shortKey)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Xóa phím tắt dựa trên ID
     */
    fun removeShortKey(shortKey: ShortKeyModel) {
        viewModelScope.launch {
            try {
                dataStore.deleteShortKey(shortKey.id)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Cập nhật gameKey (Dùng chung hàm save vì logic ID đã được xử lý)
     */
    fun updateShortKey(shortKey: ShortKeyModel) {
        addShortKey(shortKey)
    }

    fun loadSelectedKey() {
        viewModelScope.launch {
            val saved = dataStore.get(ConstantsUtils.TYPE_MEDIA, "media").first()
            _selectedKey.value = saved.ifBlank { "media" }
            // Không set manual — để auto-detect ưu tiên lần đầu
        }
    }


    /** Người dùng chọn thủ công → đánh dấu manual, không bị auto ghi đè. */
    fun selectedKey(type: String?) {
        val value = type ?: "media"
        _selectedKey.value = value
        _isManualSelected.value = true
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.TYPE_MEDIA, value)
        }
    }

    /** Auto-detect: chỉ cập nhật nếu người dùng chưa chọn thủ công. */
    fun onAppDetected(type: String) {
        _detectedAppType.value = type
        if (!_isManualSelected.value) {
            _selectedKey.value = type
        }
    }

    /** Reset về auto (nếu cần). */
    fun resetToAuto() {
        _isManualSelected.value = false
        _detectedAppType.value?.let { _selectedKey.value = it }
    }

    fun startMediaAppDetection(hybridState: HybridConnectionModel) {
        mediaAppHandler.connect(hybridState)
        mediaAppHandler.start()
    }

    fun stopMediaAppDetection() {
        mediaAppHandler.stop()
    }

    override fun onCleared() {
        super.onCleared()
        mediaAppHandler.stop()
    }
}