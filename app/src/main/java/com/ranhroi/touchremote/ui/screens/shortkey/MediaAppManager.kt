package com.ranhroi.touchremote.ui.screens.shortkey

import com.ranhroi.touchremote.utils.ConstantsUtils

class MediaAppManager {

    private var currentAppType = -1
    private var systemType = ConstantsUtils.SERVER_TYPE_WINDOWS

    fun appTypeToString(type: Int): String = when (type) {
        ConstantsUtils.MEDIA_TYPE_YOUTUBE_WEB,
        ConstantsUtils.MEDIA_TYPE_YOUTUBE_MUSIC_WEB -> "youtube"

        ConstantsUtils.MEDIA_TYPE_VLC,
        ConstantsUtils.MEDIA_TYPE_POTPLAYER,
        ConstantsUtils.MEDIA_TYPE_MPLAYERX -> "video_player"
        ConstantsUtils.MEDIA_TYPE_WINDOWSMEDIAPLAYER,
        ConstantsUtils.MEDIA_TYPE_ITUNES,
        ConstantsUtils.MEDIA_TYPE_SPOTIFY -> "music_player"

        else -> "media"
    }
    fun detectAppType(processName: String, savedWebPlayer: Int): String {
        val app = processName.lowercase()

        // 1. Browser → trả về preset web player đã lưu
        val browserList = listOf(
            "chrome.exe", "com.google.chrome",
            "microsoftedge.exe", "iexplore.exe",
            "firefox.exe", "com.apple.safari",
            "browser.exe", "opera.exe", "coccoc.com"
        )
        if (app in browserList) {
            // savedWebPlayer là Int (MEDIA_TYPE_*) → đổi sang String
            return appTypeToString(savedWebPlayer)
        }

        // 2. Native media players → "media"
        val nativeMap = listOf(
            "vlc", "potplayer", "mplayer", "wmplayer",
            "itunes", "spotify.exe"
        )
        if (nativeMap.any { app.contains(it) }) {
            return "media"
        }

        // 3. Web media apps
        val webMap = mapOf(
            "youtube" to "youtube",
            "music.youtube" to "youtube",
            "dailymotion" to "dailymotion"
        )
        for ((key, type) in webMap) {
            if (app.contains(key)) return type
        }

        // 4. Custom mapping
        for ((key, type) in ConstantsUtils.customMap) {
            if (app.contains(key)) {
                return when (type) {
                    ConstantsUtils.MEDIA_TYPE_YOUTUBE_WEB,
                    ConstantsUtils.MEDIA_TYPE_YOUTUBE_MUSIC_WEB -> "youtube"
                    else -> "media"
                }
            }
        }

        // 5. Unknown → "media"
        return "media"
    }
}