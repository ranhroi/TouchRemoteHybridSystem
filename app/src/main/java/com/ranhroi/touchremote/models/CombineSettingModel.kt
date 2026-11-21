package com.ranhroi.touchremote.models

data class CombineSettingModel(
    val progress: Int = 50,
    val scroll: Int = 50,
    val isScrollDirection: Boolean = false,
    val isLeftHand: Boolean = false,
    val isVibrate: Boolean = false,

    val isAutoConnection: Boolean = false,

    val isHotkeysWeight: Boolean = true,
    val isRotation: Boolean = true,
    val isFloating: Boolean = true,
    val isButtonText: Boolean = true,
    val isMediaMultitasking: Boolean = false,

    val isAutoTask: Boolean = false,
    val isAutoScreenMirror: Boolean = false,

    val osIndex: Int = 0,
    val isAutoPing: Boolean = false,

    val languageCode: String = "en"
    )
