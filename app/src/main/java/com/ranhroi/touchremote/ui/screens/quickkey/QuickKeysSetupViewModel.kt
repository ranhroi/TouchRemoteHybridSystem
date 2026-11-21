package com.ranhroi.touchremote.ui.screens.quickkey

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.QuickKeysModel
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.DataStoreConfig
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale.getDefault
import javax.inject.Inject

@HiltViewModel
class QuickKeysSetupViewModel @Inject constructor(
    private val dataStore: DataStoreManager,
    private val dataStoreConfig: DataStoreConfig,
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
    // DANH SÁCH QUICK KEY
    // ============================================================
    val allQuickKeys: StateFlow<List<QuickKeysModel>> = dataStore.getAllQuickKeys()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _editingQuickKey = MutableStateFlow(QuickKeysModel())
    val editingQuickKey: StateFlow<QuickKeysModel> = _editingQuickKey.asStateFlow()

    private val _selectedKeys = MutableStateFlow<List<String>>(emptyList())
    val selectedKeys: StateFlow<List<String>> = _selectedKeys.asStateFlow()

    /** presetId đang chọn trong dropdown (null = "All"). */
    private val _selectedPresetId = MutableStateFlow<String?>(null)
    val selectedPresetId: StateFlow<String?> = _selectedPresetId.asStateFlow()

    init {
        checkAndInitDefaultQuickKeys()
    }

    private fun checkAndInitDefaultQuickKeys() {
        viewModelScope.launch {
            val current = dataStore.getAllQuickKeys().first()
            if (current.isEmpty()) {
                dataStore.addQuickKeysList(QuickKeysRepository.quickKeysDefault)
            }
        }
    }

    // ============================================================
    // CRUD
    // ============================================================
    fun addQuickKeys(quickKey: QuickKeysModel) {
        viewModelScope.launch {
            try {
                dataStore.saveQuickKeys(quickKey)
            } catch (e: Exception) {
                Log.e("QuickKeysVM", "Lỗi khi lưu quick key", e)
            }
        }
    }

    fun removeQuickKeys(quickKey: QuickKeysModel) {
        viewModelScope.launch {
            try {
                dataStore.deleteQuickKeys(quickKey.id)
            } catch (e: Exception) {
                Log.e("QuickKeysVM", "Lỗi khi xóa quick key", e)
            }
        }
    }

    fun updateQuickKeys(quickKey: QuickKeysModel) {
        addQuickKeys(quickKey)
    }

    // ============================================================
    // EDIT STATE
    // ============================================================
    fun startNewQuickKey(openApp: Boolean = false) {
        _editingQuickKey.value = QuickKeysModel(openApp = openApp)
        _selectedKeys.value = emptyList()
        _selectedPresetId.value = null
    }

    fun startEditQuickKey(quickKey: QuickKeysModel) {
        _editingQuickKey.value = quickKey
        _selectedPresetId.value = quickKey.presetId.ifBlank { null }
        _selectedKeys.value = quickKey.keys.getOrElse(config.value.osIndex) { emptyList() }
    }

    fun setName(name: String) {
        _editingQuickKey.value = _editingQuickKey.value.copy(name = name)
    }

    fun setIcon(icon: String) {
        _editingQuickKey.value = _editingQuickKey.value.copy(icon = icon)
    }

    fun setType(openApp: Boolean) {
        _editingQuickKey.value = _editingQuickKey.value.copy(openApp = openApp)
    }

    // ============================================================
    // PRESET — đồng bộ presetId + preset name
    // ============================================================
    /** Đọc presetId đã lưu (gọi khi mở app). */
    fun loadSelectedPresetId() {
        viewModelScope.launch {
            val saved = dataStore.get(ConstantsUtils.SELECTED_PRESET_ID, "all").first()
            _selectedPresetId.value = if (saved == "all") null else saved
        }
    }

    /**
     * Chọn preset có sẵn trong dropdown.
     * Đồng thời cập nhật cả [selectedPresetId] (highlight) và [editing.preset] (hiển thị + lưu).
     */
    fun selectPreset(presetId: String?, presetName: String) {
        _selectedPresetId.value = presetId
        _editingQuickKey.value = _editingQuickKey.value.copy(
            preset = presetName,
            presetId = presetId ?: ""
        )
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.SELECTED_PRESET_ID, presetId ?: "all")
        }
    }

    /** Chọn "All" — reset cả preset name và id. */
    fun selectAllPresets() {
        selectPreset(null, "")
    }

    /**
     * Thêm preset mới và chọn luôn.
     * Sinh presetId mới dựa trên tên, đảm bảo không trùng với preset đã có.
     */
    fun addAndSelectPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        val baseId = trimmed.replace(" ", "").trim().lowercase(getDefault())
        val existingIds = allQuickKeys.value.map { it.presetId }.toSet()

        var candidate = baseId
        var suffix = 1
        while (existingIds.contains(candidate)) {
            candidate = "$baseId${suffix++}"
        }

        _selectedPresetId.value = candidate
        _editingQuickKey.value = _editingQuickKey.value.copy(
            preset = trimmed,
            presetId = candidate
        )
        viewModelScope.launch {
            dataStore.put(ConstantsUtils.SELECTED_PRESET_ID, candidate)
        }
    }

    /**
     * Danh sách preset distinct từ toàn bộ quick keys.
     * Trả về List<Pair<presetName, presetId>> đã sắp xếp theo tên.
     */
    fun getAvailablePresets(): List<Pair<String, String>> {
        return allQuickKeys.value
            .filter { it.preset.isNotBlank() }
            .map { it.preset to it.presetId }
            .distinctBy { it.second }
            .sortedBy { it.first }
    }

    // ============================================================
    // KEY SELECTION
    // ============================================================
    fun toggleKey(code: String) {
        val current = _selectedKeys.value
        _selectedKeys.value = if (current.contains(code)) current - code else current + code
    }

    fun isKeySelected(code: String): Boolean = _selectedKeys.value.contains(code)

    fun removeKey(code: String) {
        _selectedKeys.value = _selectedKeys.value - code
    }

    fun clearKeys() {
        _selectedKeys.value = emptyList()
    }

    // ============================================================
    // SAVE / CANCEL
    // ============================================================
    fun saveEditingQuickKey(): Boolean {
        val editing = _editingQuickKey.value
        if (editing.name.isBlank()) return false
        if (_selectedKeys.value.isEmpty()) return false

        val currentKeys = editing.keys.toMutableList()
        while (currentKeys.size < 3) currentKeys.add(emptyList())
        currentKeys[config.value.osIndex] = _selectedKeys.value

        val toSave = editing.copy(keys = currentKeys)

        if (toSave.id == 0) addQuickKeys(toSave) else updateQuickKeys(toSave)

        _editingQuickKey.value = QuickKeysModel()
        _selectedKeys.value = emptyList()
        _selectedPresetId.value = null
        return true
    }

    fun cancelEditing() {
        _editingQuickKey.value = QuickKeysModel()
        _selectedKeys.value = emptyList()
        _selectedPresetId.value = null
    }

    // ============================================================
    // PREVIEW
    // ============================================================
    fun previewKeys(): String = _selectedKeys.value.joinToString(" + ")
}