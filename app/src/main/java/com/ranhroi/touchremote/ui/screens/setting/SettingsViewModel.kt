package com.ranhroi.touchremote.ui.screens.setting

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.ui.screens.shortkey.ShortKeysRepository
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.DataStoreConfig
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreConfig: DataStoreConfig,
    private val dataStore: DataStoreManager
) : ViewModel() {

    private val _settings = MutableStateFlow(CombineSettingModel())
    val settings: StateFlow<CombineSettingModel> = _settings.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    private val _isSave = MutableStateFlow(false)
    val isSave = _isSave.asStateFlow()

    // Chế độ người dùng chọn (auto, vi, en, ja...)
    private val _selectedLanguageMode = MutableStateFlow("auto")
    val selectedLanguageMode: StateFlow<String> = _selectedLanguageMode.asStateFlow()

    init {
        viewModelScope.launch {
            // 1. Load settings cấu hình
            dataStoreConfig.combine.first().let {
                _settings.value = it
                _isLoaded.value = true
            }

            // 2. Load chế độ ngôn ngữ đã lưu từ DataStore
            dataStore.get(ConstantsUtils.KEY_LANGUAGE_MODE, "3").collect { mode ->
                _selectedLanguageMode.value = mode
            }
        }

        // Autosave settings khi có thay đổi cấu hình
        viewModelScope.launch {
            _settings
                .drop(1)
                .debounce(300.milliseconds)
                .collect { saveToDataStore(it) }
        }
    }

    /**
     * Thay đổi ngôn ngữ và kích hoạt hệ thống làm mới giao diện
     */
    fun changeLanguage(languageCode: String) {
        viewModelScope.launch {
            // 1. Lưu lựa chọn vào DataStore để ghi nhớ cho lần sau
            dataStore.put(ConstantsUtils.KEY_LANGUAGE_MODE, languageCode)
            _selectedLanguageMode.value = languageCode

            // 2. Tạo danh sách Locale dựa trên lựa chọn
            val appLocale: LocaleListCompat = if (languageCode == "auto") {
                LocaleListCompat.getEmptyLocaleList() // Theo hệ thống
            } else {
                LocaleListCompat.forLanguageTags(languageCode) // Theo mã vi, en, ja...
            }

            // 3. Gọi AppCompatDelegate (Yêu cầu MainActivity kế thừa AppCompatActivity)
            // Lệnh này sẽ khiến Activity tự động Recreate để cập nhật Resource
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    /**
     * Lấy mã ngôn ngữ thực tế (Dùng để xác định đang hiển thị tiếng gì)
     */
    private fun getActualLanguageCode(): String {
        val mode = _selectedLanguageMode.value

        // Nếu không phải auto, dùng luôn mode đó
        if (mode != "auto") return mode

        // Nếu là auto, lấy ngôn ngữ của hệ thống máy
        val systemLocale = java.util.Locale.getDefault().language

        // Kiểm tra xem app có hỗ trợ ngôn ngữ này không, nếu không trả về "en"
        return if (ConstantsUtils.languageOptionsList.any { it.code == systemLocale }) {
            systemLocale
        } else {
            "en"
        }
    }

    /**
     * Trả về tên hiển thị đầy đủ kèm Flag (Dùng cho Dropdown state)
     */
    fun getCurrentDisplayLanguageName(context: Context): String {
        val mode = _selectedLanguageMode.value
        val actualCode = getActualLanguageCode()

        // Tìm trong danh sách trung tâm LanguageCodeModel(code, labelRes, flag)
        val item = ConstantsUtils.languageOptionsList.find { it.code == actualCode }

        val name = item?.let {
            "${it.flag} ${context.getString(it.labelRes)}"
        } ?: actualCode

        // Nếu đang ở chế độ auto, thêm icon quả địa cầu phía trước
        return if (mode == "auto") "🌐 $name" else name
    }

    fun updateSettings(new: CombineSettingModel) {
        _settings.value = new
    }

    private suspend fun saveToDataStore(config: CombineSettingModel) {
        runCatching {
            dataStore.put(ConstantsUtils.CURSOR_PROGRESS, config.progress)
            dataStore.put(ConstantsUtils.CURSOR_SCROLL, config.scroll)
            dataStore.put(ConstantsUtils.SCROLL_DIRECTION, config.isScrollDirection)
            dataStore.put(ConstantsUtils.LEFT_HAND, config.isLeftHand)
            dataStore.put(ConstantsUtils.ON_VIBRATE, config.isVibrate)
            dataStore.put(ConstantsUtils.AUTOMATIC_TASK, config.isAutoTask)
            dataStore.put(ConstantsUtils.AUTOMATIC_CONNECTION, config.isAutoConnection)
            dataStore.put(ConstantsUtils.SHOW_HOTKEYS_WEIGHT, config.isHotkeysWeight)
            dataStore.put(ConstantsUtils.SHOW_ROTATION, config.isRotation)
            dataStore.put(ConstantsUtils.SHOW_FLOATING, config.isFloating)
            dataStore.put(ConstantsUtils.SHOW_BUTTON_TEXT, config.isButtonText)
            dataStore.put(ConstantsUtils.SHOW_MEDIA_MULTITASKING, config.isMediaMultitasking)
            dataStore.put(ConstantsUtils.AUTOMATIC_SCREEN_MIRROR, config.isAutoScreenMirror)
        }.onSuccess {
            _isSave.value = true
            kotlinx.coroutines.delay(1500.milliseconds)
            _isSave.value = false
        }.onFailure {
            Log.e("SettingsViewModel", "Save config error", it)
        }
    }
    fun resetToDefault() {
        viewModelScope.launch {
            dataStore.addShortKeyList(ShortKeysRepository.shortKeysDefault)
            Log.d("HotkeysViewModel", "Reset về mặc định")
        }
    }
}
