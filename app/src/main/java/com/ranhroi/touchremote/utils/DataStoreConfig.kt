package com.ranhroi.touchremote.utils


import com.ranhroi.touchremote.models.CombineSettingModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreConfig @Inject constructor(
    private val dataStore: DataStoreManager
) {

    val combine: Flow<CombineSettingModel> =
        dataStore.data.map { pref ->

            CombineSettingModel(
                progress = pref[ConstantsUtils.CURSOR_PROGRESS] ?: 50,
                scroll = pref[ConstantsUtils.CURSOR_SCROLL] ?: 3,
                isScrollDirection = pref[ConstantsUtils.SCROLL_DIRECTION] ?: false,
                isLeftHand = pref[ConstantsUtils.LEFT_HAND] ?: false,
                isVibrate = pref[ConstantsUtils.ON_VIBRATE] ?: false,

                isAutoTask = pref[ConstantsUtils.AUTOMATIC_TASK] ?: false,

                isAutoConnection = pref[ConstantsUtils.AUTOMATIC_CONNECTION] ?: false,
                isHotkeysWeight = pref[ConstantsUtils.SHOW_HOTKEYS_WEIGHT] ?: true,
                isRotation = pref[ConstantsUtils.SHOW_ROTATION] ?: true,
                isFloating = pref[ConstantsUtils.SHOW_FLOATING] ?: true,
                isButtonText = pref[ConstantsUtils.SHOW_BUTTON_TEXT] ?: true,
                isMediaMultitasking = pref[ConstantsUtils.SHOW_MEDIA_MULTITASKING] ?: true,

                isAutoScreenMirror = pref[ConstantsUtils.AUTOMATIC_SCREEN_MIRROR] ?: false,

                languageCode = pref[ConstantsUtils.KEY_LANGUAGE] ?: ConstantsUtils.DEFAULT_LANGUAGE
            )
        }

    // Thêm function để update language riêng
    suspend fun updateLanguage(languageCode: String) {
        dataStore.put(ConstantsUtils.KEY_LANGUAGE, languageCode)
    }
}
