package com.ranhroi.touchremote.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.BottomNavModel
import com.ranhroi.touchremote.models.LanguageCodeModel
import com.ranhroi.touchremote.ui.tabs.TabModel

object ConstantsUtils {

    // ==================== FORMAT ====================
    fun formatUuid(input: String): String {
        val cleaned = input.replace(Regex("[^0-9a-fA-F]"), "").take(32)
        val sb = StringBuilder()
        for (i in cleaned.indices) {
            if (i == 8 || i == 12 || i == 16 || i == 20) sb.append("-")
            sb.append(cleaned[i])
        }
        return sb.toString()
    }

    // ==================== LANGUAGE ====================
    val languageOptionsList = listOf(
        LanguageCodeModel("en", R.string.language_english, "🇬🇧"),
        LanguageCodeModel("vi", R.string.language_vietnamese, "🇻🇳"),
    )

    @Composable
    fun getDisplayLabel(code: String): String = when {
        code == "auto" -> "🌐 ${stringResource(R.string.language_auto)}"
        else -> languageOptionsList.find { it.code == code }
            ?.let { "${it.flag} ${stringResource(it.labelRes)}" }
            ?: "🌐 ${stringResource(R.string.language_auto)}"
    }

    // ==================== SYSTEM ====================
    fun getSystemInfoInt(os: String): Int = when (os.lowercase()) {
        "windows" -> SERVER_TYPE_WINDOWS
        "mac" -> SERVER_TYPE_MAC
        "linux" -> SERVER_TYPE_LINUX
        "android" -> SERVER_TYPE_ANDROID
        "ios" -> SERVER_TYPE_IOS
        else -> SERVER_TYPE_UNKNOW
    }

    fun getSystemInfoText(os: Int): String = when (os) {
        SERVER_TYPE_WINDOWS -> "Windows"
        SERVER_TYPE_MAC -> "MacOS"
        SERVER_TYPE_LINUX -> "Linux"
        SERVER_TYPE_ANDROID -> "Android"
        SERVER_TYPE_IOS -> "IOS"
        else -> "Unknown"
    }


    @Composable
    fun getSendType(str: String): String {
        return when (str) {
            "0" -> stringResource(R.string.send_method_default)
            "1" -> stringResource(R.string.send_method_string)
            else -> stringResource(R.string.send_method_nothing)
        }
    }

    // Cách 1: Dùng trong @Composable
    @Composable
    fun getSendOptions(): List<Pair<String, Int>> {
        return listOf(
            stringResource(R.string.send_option_0) to 0,
            stringResource(R.string.send_option_1) to 1
        )
    }

    // Cách 2: Nếu cần dùng ở nơi không có @Composable, truyền context vào
    fun getSendOptions(context: Context): List<Pair<String, Int>> {
        return listOf(
            context.getString(R.string.send_option_0) to 0,
            context.getString(R.string.send_option_1) to 1
        )
    }

    // ==================== UI TABS ====================
    val tabs: List<TabModel> by lazy {
        listOf(
            TabModel(R.drawable.baseline_touch_app, R.drawable.outline_touch_app),
            TabModel(R.drawable.baseline_apps, R.drawable.outline_apps),
            TabModel(R.drawable.baseline_multiline_chart, R.drawable.outline_multiline_chart),
            TabModel(R.drawable.baseline_folder, R.drawable.outline_folder),
            TabModel(R.drawable.baseline_terminal, R.drawable.outline_terminal),
            TabModel(R.drawable.baseline_screen_share, R.drawable.outline_screen_share),
        )
    }

    val bottomNavRepository: List<BottomNavModel> by lazy {
        listOf(
            BottomNavModel(
                nameRes = R.string.bottom_nav_mouse,
                icon = "baseline_mouse"
            ),
            BottomNavModel(
                nameRes = R.string.bottom_nav_shortcut,
                icon = "baseline_shortcut"
            ),
            BottomNavModel(
                nameRes = R.string.bottom_nav_keyboard,
                icon = "baseline_keyboard_hide"
            ),
            BottomNavModel(
                nameRes = R.string.bottom_nav_media,
                icon = "baseline_music_note"
            ),
            BottomNavModel(
                nameRes = R.string.bottom_nav_browser,
                icon = "outline_browse"
            ),
            BottomNavModel(
                nameRes = R.string.bottom_nav_power,
                icon = "baseline_power_setting_new"
            ),
        )
    }


    val OS_INDEX = intPreferencesKey("os_index")

    // ==================== DATASTORE KEYS ====================
    // Lists
    val DEVICE_LIST_KEY = stringPreferencesKey("device_list")
    val PATH_LIST_KEY = stringPreferencesKey("path_list")
    val HOTKEY_LIST_KEY = stringPreferencesKey("hotkey_list")
    val ShortKey_LIST_KEY = stringPreferencesKey("ShortKey_list")
    val DOWNLOAD_PROGRESS_KEY = stringPreferencesKey("download_progress")
    val PATH_FILE_PC = stringPreferencesKey("path_file_pc")

    val QUICK_KEY_LIST_KEY = stringPreferencesKey("quickkey_list")

    val TYPE_MEDIA = stringPreferencesKey("type_media")
    val SELECTED_PRESET_ID = stringPreferencesKey("selected_preset_id")

    // Language

    val KEY_LANGUAGE = stringPreferencesKey("selected_language_display")
    val KEY_LANGUAGE_MODE = stringPreferencesKey("selected_language_mode")
    const val DEFAULT_LANGUAGE = "vi"

    // Cursor
    val CURSOR_PROGRESS = intPreferencesKey("CURSOR_PROGRESS")
    val CURSOR_SCROLL = intPreferencesKey("CURSOR_SCROLL")
    val SCROLL_DIRECTION = booleanPreferencesKey("SCROLL_DIRECTION")
    val LEFT_HAND = booleanPreferencesKey("LEFT_HAND")
    val ON_VIBRATE = booleanPreferencesKey("ON_VIBRATE")

    // Auto
    val AUTOMATIC_TASK = booleanPreferencesKey("AUTOMATIC_TASK")
    val AUTOMATIC_CONNECTION = booleanPreferencesKey("AUTOMATIC_CONNECTION")
    val AUTOMATIC_SCREEN_MIRROR = booleanPreferencesKey("AUTOMATIC_SCREEN_MIRROR")
    val AUTO_VERIFY_PASS = stringPreferencesKey("AUTO_VERIFY_PASS")

    // UI
    val SHOW_HOTKEYS_WEIGHT = booleanPreferencesKey("SHOW_HOTKEYS_WEIGHT")
    val SHOW_ROTATION = booleanPreferencesKey("SHOW_ROTATION")
    val SHOW_FLOATING = booleanPreferencesKey("SHOW_FLOATING")
    val SHOW_BUTTON_TEXT = booleanPreferencesKey("SHOW_BUTTON_TEXT")
    val SHOW_MEDIA_MULTITASKING = booleanPreferencesKey("SHOW_MEDIA_MULTITASKING")


    // ==================== REMOTE SELECTION ====================
    val REMOTE_ID = stringPreferencesKey("remote_id")

    //================= CONTROL STATES ====================
    val IS_ON = booleanPreferencesKey("is_on")
    val TEMP = intPreferencesKey("temp")
    val MODE = stringPreferencesKey("mode")
    val FAN_SPEED = stringPreferencesKey("fan_speed")
    val IS_SWING = booleanPreferencesKey("is_swing")
    val VANE_DIRECTION = intPreferencesKey("van_direction")
    val IS_COIL_DRY = booleanPreferencesKey("is_coil_dry")
    val IS_AIR_CLEAN = booleanPreferencesKey("is_air_clean")
    val IS_SLEEP = booleanPreferencesKey("is_sleep")
    val TIMER_MODE = stringPreferencesKey("timer_mode")
    val TIMER_HOUR = intPreferencesKey("timer_hour")

    //================= NEW FEATURES ====================
    val IS_QUIET = booleanPreferencesKey("is_quiet")
    val IS_POWERFUL = booleanPreferencesKey("is_powerful")


    // Other
    val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    val KEY_PORT_SCAN = intPreferencesKey("PORT_SCAN")
    val KEY_PORT_CONNECTION = intPreferencesKey("PORT_CONNECTION")
    val CURRENT_SYSTEM_OS = stringPreferencesKey("CURRENT_SYSTEM_OS")

    // ==================== SERVER CONSTANTS ====================
    const val PORT_SCAN = 2008
    const val PORT_CONNECTION = 1978
    const val DEFAULT_SERVER_UUID = "c05efa2b-5e9f-4a39-9705-72ccf47d2eb8"

    // Server Types
    const val SERVER_TYPE_UNKNOW = -1
    const val SERVER_TYPE_WINDOWS = 0
    const val SERVER_TYPE_MAC = 1
    const val SERVER_TYPE_LINUX = 2
    const val SERVER_TYPE_ANDROID = 3
    const val SERVER_TYPE_IOS = 4

    // ==================== MEDIA TYPES ====================

    // Native Apps (1-99)
    const val MEDIA_TYPE_VLC = 2
    const val MEDIA_TYPE_SPOTIFY = 3
    const val MEDIA_TYPE_MPLAYERX = 4
    const val MEDIA_TYPE_WINDOWSMEDIAPLAYER = 5
    const val MEDIA_TYPE_ITUNES = 6
    const val MEDIA_TYPE_POTPLAYER = 7
    const val MEDIA_TYPE_YOUTUBE_WEB = 100
    const val MEDIA_TYPE_YOUTUBE_MUSIC_WEB = 119

    // ==================== CUSTOM MAP ====================
    val customMap: MutableMap<String, Int> = mutableMapOf()

}