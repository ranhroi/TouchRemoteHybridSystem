package com.ranhroi.touchremote.ui.screens.shortkey

import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.models.ShortKeyPresetModel

object ShortKeysRepository {
    val shortKeysDefault: List<ShortKeyModel> by lazy {
        listOf(

            // ============================================================
            // 1. FLOATING — send = 0 (ẩn)
            // ============================================================
            ShortKeyModel(
                type = "floating",
                name = "Hide",
                keys = listOf(
                    listOf("WIN", "D"),
                    listOf("CMD", "H"),
                    listOf("CTRL", "ALT", "D")
                ),
                icon = "baseline_computer"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Refresh",
                keys = listOf(
                    listOf("F5"),
                    listOf("CMD", "R"),
                    listOf("F5")
                ),
                icon = "baseline_refresh"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Select All",
                keys = listOf(
                    listOf("CTRL", "A"),
                    listOf("CMD", "A"),
                    listOf("CTRL", "A")
                ),
                icon = "baseline_select_all"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Copy",
                keys = listOf(
                    listOf("CTRL", "C"),
                    listOf("CMD", "C"),
                    listOf("CTRL", "C")
                ),
                icon = "outline_content_copy"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Paste",
                keys = listOf(
                    listOf("CTRL", "V"),
                    listOf("CMD", "V"),
                    listOf("CTRL", "V")
                ),
                icon = "outline_content_paste"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Keyboard",
                keys = emptyList(),
                icon = "outline_keyboard_keys"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Enter",
                keys = listOf(
                    listOf("ENTER"),
                    listOf("ENTER"),
                    listOf("ENTER")
                ),
                icon = "baseline_subdirectory_arrow_left"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Cut",
                keys = listOf(
                    listOf("CTRL", "X"),
                    listOf("CMD", "X"),
                    listOf("CTRL", "X")
                ),
                icon = "baseline_content_cut"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Undo",
                keys = listOf(
                    listOf("CTRL", "Z"),
                    listOf("CMD", "Z"),
                    listOf("CTRL", "Z")
                ),
                icon = "baseline_undo"
            ),
            ShortKeyModel(
                type = "floating",
                name = "Redo",
                keys = listOf(
                    listOf("CTRL", "Y"),
                    listOf("CMD", "SHIFT", "Z"),
                    listOf("CTRL", "SHIFT", "Z")
                ),
                icon = "baseline_redo"
            ),

            // ============================================================
            // 2. BROWSER — send = 1
            // ============================================================
            ShortKeyModel(
                type = "browser",
                name = "Back",
                keys = listOf(
                    listOf("browser back"),
                    listOf("browser back"),
                    listOf("browser back")
                ),
                icon = "baseline_arrow_back",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Forward",
                keys = listOf(
                    listOf("browser forward"),
                    listOf("browser forward"),
                    listOf("browser forward")
                ),
                icon = "baseline_arrow_forward",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Home",
                keys = listOf(
                    listOf("browser home"),
                    listOf("browser home"),
                    listOf("browser home")
                ),
                icon = "baseline_home",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Search",
                keys = listOf(
                    listOf("browser search"),
                    listOf("browser search"),
                    listOf("browser search")
                ),
                icon = "baseline_search",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Zoom In",
                keys = listOf(
                    listOf("zoomin"),
                    listOf("zoomin"),
                    listOf("zoomin")
                ),
                icon = "outline_zoom_in",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Zoom Out",
                keys = listOf(
                    listOf("zoomout"),
                    listOf("zoomout"),
                    listOf("zoomout")
                ),
                icon = "baseline_zoom_out",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Refresh",
                keys = listOf(
                    listOf("browser refresh"),
                    listOf("browser refresh"),
                    listOf("browser refresh")
                ),
                icon = "baseline_refresh",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "New Tab",
                keys = listOf(
                    listOf("browser newtab"),
                    listOf("browser newtab"),
                    listOf("browser newtab")
                ),
                icon = "baseline_add_box",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Favorites",
                keys = listOf(
                    listOf("browser favorites"),
                    listOf("browser favorites"),
                    listOf("browser favorites")
                ),
                icon = "outline_favorite",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Maximize",
                keys = listOf(
                    listOf("window maximize"),
                    listOf("window maximize"),
                    listOf("window maximize")
                ),
                icon = "baseline_fit_screen",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Minimize",
                keys = listOf(
                    listOf("window minimize"),
                    listOf("window minimize"),
                    listOf("window minimize")
                ),
                icon = "baseline_minimize",
                send = 1
            ),
            ShortKeyModel(
                type = "browser",
                name = "Close",
                keys = listOf(
                    listOf("browser closetab"),
                    listOf("browser closetab"),
                    listOf("browser closetab")
                ),
                icon = "baseline_close",
                send = 1
            ),

            // ============================================================
            // 4. POWER — send = 1
            // ============================================================
            ShortKeyModel(
                type = "power",
                name = "Brightness Down",
                keys = listOf(
                    listOf("brightnesslow"),
                    listOf("brightnesslow"),
                    listOf("brightnesslow")
                ),
                icon = "baseline_brightness_low",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Brightness Up",
                keys = listOf(
                    listOf("brightnesshigh"),
                    listOf("brightnesshigh"),
                    listOf("brightnesshigh")
                ),
                icon = "baseline_brightness_high",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Power Off",
                keys = listOf(
                    listOf("poweroff"),
                    listOf("poweroff"),
                    listOf("poweroff")
                ),
                icon = "baseline_power_setting_new",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Restart",
                keys = listOf(
                    listOf("reboot"),
                    listOf("reboot"),
                    listOf("reboot")
                ),
                icon = "outline_restart_alt",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Sleep",
                keys = listOf(
                    listOf("sleep"),
                    listOf("sleep"),
                    listOf("sleep")
                ),
                icon = "baseline_hotel",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Wake",
                keys = listOf(
                    listOf("wake"),
                    listOf("wake"),
                    listOf("wake")
                ),
                icon = "outline_notifications_paused",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Log Out",
                keys = listOf(
                    listOf("logoff"),
                    listOf("logoff"),
                    listOf("logoff")
                ),
                icon = "baseline_logout",
                send = 1
            ),
            ShortKeyModel(
                type = "power",
                name = "Lock",
                keys = listOf(
                    listOf("WIN", "L"),
                    listOf("CMD", "CTRL", "Q"),
                    listOf("CTRL", "ALT", "L")
                ),
                icon = "baseline_lock",
                send = 1
            )
        ) + shortKeysMediaDefault + shortKeysYouTube + shortKeysDailymotion + shortKeysVideoPlayer + shortKeysMusicPlayer
    }

    // ============================================================
    // 1. MEDIA DEFAULT — send = 0/1 theo chuẩn
    // ============================================================
    val shortKeysMediaDefault: List<ShortKeyModel> by lazy {
        listOf(
            ShortKeyModel(
                type = "media",
                name = "Back",
                keys = listOf(listOf("media prev"), listOf("media prev"), listOf("media prev")),
                icon = "baseline_arrow_back",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Play / Pause",
                keys = listOf(listOf("media play"), listOf("media play"), listOf("media play")),
                icon = "baseline_play_arrow",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Next",
                keys = listOf(listOf("media next"), listOf("media next"), listOf("media next")),
                icon = "baseline_arrow_forward",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Volume Up",
                keys = listOf(listOf("VOLUMEUP"), listOf("VOLUMEUP"), listOf("VOLUMEUP")),
                icon = "baseline_volume_up",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Mute",
                keys = listOf(listOf("MUTE"), listOf("MUTE"), listOf("MUTE")),
                icon = "baseline_volume_off",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Volume Down",
                keys = listOf(listOf("VOLUMEDN"), listOf("VOLUMEDN"), listOf("VOLUMEDN")),
                icon = "baseline_volume_down",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Captions",
                keys = listOf(listOf("C"), listOf("C"), listOf("C")),
                icon = "baseline_caption"
            ),
            ShortKeyModel(
                type = "media",
                name = "Rewind",
                keys = listOf(listOf("LF"), listOf("LF"), listOf("LF")),
                icon = "baseline_fast_rewind"
            ),
            ShortKeyModel(
                type = "media",
                name = "Stop",
                keys = listOf(listOf("media stop"), listOf("media stop"), listOf("media stop")),
                icon = "baseline_stop",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Fast Forward",
                keys = listOf(listOf("RT"), listOf("RT"), listOf("RT")),
                icon = "baseline_fast_forward"
                // send = 0 → ẩn
            ),
            ShortKeyModel(
                type = "media",
                name = "Replay",
                keys = listOf(
                    listOf("CTRL", "R"),
                    listOf("CMD", "R"),
                    listOf("CTRL", "R")
                ),
                icon = "baseline_replay"
                // send = 0 → ẩn
            ),
            ShortKeyModel(
                type = "media",
                name = "Maximize",
                keys = listOf(
                    listOf("window maximize"),
                    listOf("window maximize"),
                    listOf("window maximize")
                ),
                icon = "baseline_fit_screen",
                send = 1
            ),
            ShortKeyModel(
                type = "media",
                name = "Close Window",
                keys = listOf(
                    listOf("window close"),
                    listOf("wppt close"),
                    listOf("window close")
                ),
                icon = "baseline_close",
                send = 1
            ),

            // ============================================================
            // 6. FOLDER - Computer Paths
            // ============================================================
            ShortKeyModel(
                type = "folder",
                name = "Computer",
                keys = listOf(
                    listOf("/"),
                    listOf("/"),
                    listOf("/")
                ),
                icon = "baseline_computer"
            ),
            ShortKeyModel(
                type = "folder",
                name = "Desktop",
                keys = listOf(
                    listOf("~/desktop"),
                    listOf("~/desktop"),
                    listOf("~/desktop")
                ),
                icon = "baseline_desktop_windows"
            ),
            ShortKeyModel(
                type = "folder",
                name = "Documents",
                keys = listOf(
                    listOf("~/documents"),
                    listOf("~/documents"),
                    listOf("~/documents")
                ),
                icon = "baseline_insert_drive_file"
            ),
            ShortKeyModel(
                type = "folder",
                name = "Downloads",
                keys = listOf(
                    listOf("~/downloads"),
                    listOf("~/downloads"),
                    listOf("~/downloads")
                ),
                icon = "baseline_download"
            ),
            ShortKeyModel(
                type = "folder",
                name = "Music",
                keys = listOf(
                    listOf("~/music"),
                    listOf("~/music"),
                    listOf("~/music")
                ),
                icon = "baseline_music_video"
            ),
            ShortKeyModel(
                type = "folder",
                name = "Videos",
                keys = listOf(
                    listOf("~/videos"),
                    listOf("~/videos"),
                    listOf("~/videos")
                ),
                icon = "baseline_music_video"
            ),
            ShortKeyModel(
                send = 0,
                type = "folder",
                name = "Pictures",
                keys = listOf(
                    listOf("~/pictures"),
                    listOf("~/pictures"),
                    listOf("~/pictures")
                ),
                icon = "outline_photo"
            ),
        )
    }

    // ============================================================
    // 2. YOUTUBE — 14 items (10 keyboard + 2 window + 2 group)
    // ============================================================
    val shortKeysYouTube: List<ShortKeyModel> by lazy {
        listOf(
            // 1. Lùi về
            ShortKeyModel(
                type = "youtube",
                name = "Back",
                keys = listOf(listOf("SHIFT", "P"), listOf("SHIFT", "P"), listOf("SHIFT", "P")),
                icon = "baseline_arrow_back"
            ),
            // 2. Phát / Tạm dừng
            ShortKeyModel(
                type = "youtube",
                name = "Play / Pause",
                keys = listOf(listOf("K"), listOf("K"), listOf("K")),
                icon = "baseline_play_arrow"
            ),
            // 3. Video tiếp
            ShortKeyModel(
                type = "youtube",
                name = "Next Video",
                keys = listOf(listOf("SHIFT", "N"), listOf("SHIFT", "N"), listOf("SHIFT", "N")),
                icon = "baseline_arrow_forward"
            ),
            // 4. Tăng âm lượng
            ShortKeyModel(
                type = "youtube",
                name = "Volume Up",
                keys = listOf(listOf("UP"), listOf("UP"), listOf("UP")),
                icon = "baseline_volume_up"
            ),
            // 5. Tắt tiếng
            ShortKeyModel(
                type = "youtube",
                name = "Mute",
                keys = listOf(listOf("M"), listOf("M"), listOf("M")),
                icon = "baseline_volume_off"
            ),
            // 6. Giảm âm lượng
            ShortKeyModel(
                type = "youtube",
                name = "Volume Down",
                keys = listOf(listOf("DOWN"), listOf("DOWN"), listOf("DOWN")),
                icon = "baseline_volume_down"
            ),
            // 7. Phụ đề
            ShortKeyModel(
                type = "youtube",
                name = "Captions",
                keys = listOf(listOf("C"), listOf("C"), listOf("C")),
                icon = "baseline_caption"
            ),
            // 8. Tua lại
            ShortKeyModel(
                type = "youtube",
                name = "Rewind",
                keys = listOf(listOf("J"), listOf("J"), listOf("J")),
                icon = "baseline_fast_rewind"
            ),
            // 9. Dừng (YouTube không có phím chữ, dùng media key STOP)
            ShortKeyModel(
                type = "youtube",
                name = "Stop",
                keys = listOf(listOf("MEDIA_STOP"), listOf("MEDIA_STOP"), listOf("MEDIA_STOP")),
                icon = "baseline_stop"
            ),
            // 10. Tua tới
            ShortKeyModel(
                type = "youtube",
                name = "Fast Forward",
                keys = listOf(listOf("L"), listOf("L"), listOf("L")),
                icon = "baseline_fast_forward"
            ),
            // 11. Video trước / Phát lại
            ShortKeyModel(
                type = "youtube",
                name = "Replay",
                keys = listOf(listOf("0"), listOf("0"), listOf("0")),
                icon = "baseline_replay"
            ),
            // 12. Toàn màn hình
            ShortKeyModel(
                type = "youtube",
                name = "Fullscreen",
                keys = listOf(listOf("F"), listOf("F"), listOf("F")),
                icon = "baseline_fit_screen"
            ),
            // 13. Đóng cửa sổ
            ShortKeyModel(
                type = "youtube",
                name = "Close Window",
                keys = listOf(
                    listOf("ALT", "F4"),
                    listOf("CMD", "W"),
                    listOf("ALT", "F4")
                ),
                icon = "baseline_close"
            )
        )
    }

    // ============================================================
    // 3. DAILYMOTION — 14 items (10 keyboard + 2 window + 2 group)
    // ============================================================
    val shortKeysDailymotion: List<ShortKeyModel> by lazy {
        listOf(
            // 1. Lùi về (dùng LEFT快退)
            ShortKeyModel(
                type = "dailymotion",
                name = "Back",
                keys = listOf(listOf("SHIFT", "P"), listOf("SHIFT", "P"), listOf("SHIFT", "P")),
                icon = "baseline_arrow_back"
            ),
            // 2. Phát / Tạm dừng — Space
            ShortKeyModel(
                type = "dailymotion",
                name = "Play / Pause",
                keys = listOf(listOf("SPACE"), listOf("SPACE"), listOf("SPACE")),
                icon = "baseline_play_arrow"
            ),
            // 3. Tiếp — RIGHT (快进)
            ShortKeyModel(
                type = "dailymotion",
                name = "Next",
                keys = listOf(listOf("SHIFT", "N"), listOf("SHIFT", "N"), listOf("SHIFT", "N")),
                icon = "baseline_arrow_forward"
            ),
            // 4. Tăng âm lượng — UP
            ShortKeyModel(
                type = "dailymotion",
                name = "Volume Up",
                keys = listOf(listOf("UP"), listOf("UP"), listOf("UP")),
                icon = "baseline_volume_up"
            ),
            // 5. Tắt tiếng — M
            ShortKeyModel(
                type = "dailymotion",
                name = "Mute",
                keys = listOf(listOf("M"), listOf("M"), listOf("M")),
                icon = "baseline_volume_off"
            ),
            // 6. Giảm âm lượng — DOWN
            ShortKeyModel(
                type = "dailymotion",
                name = "Volume Down",
                keys = listOf(listOf("DOWN"), listOf("DOWN"), listOf("DOWN")),
                icon = "baseline_volume_down"
            ),
            // 7. Phụ đề (Dailymotion无官方C键，保留占位)
            ShortKeyModel(
                type = "dailymotion",
                name = "Captions",
                keys = listOf(listOf("C"), listOf("C"), listOf("C")),
                icon = "baseline_caption"
            ),
            // 8. Tua lại — LEFT (快退 10s)
            ShortKeyModel(
                type = "dailymotion",
                name = "Rewind",
                keys = listOf(listOf("LEFT"), listOf("LEFT"), listOf("LEFT")),
                icon = "baseline_fast_rewind"
            ),
            // 9. Dừng (无官方停止键，保留 SPACE 占位)
            ShortKeyModel(
                type = "dailymotion",
                name = "Stop",
                keys = listOf(listOf("SPACE"), listOf("SPACE"), listOf("SPACE")),
                icon = "baseline_stop"
            ),
            // 10. Tua tới — RIGHT (快进 10s)
            ShortKeyModel(
                type = "dailymotion",
                name = "Fast Forward",
                keys = listOf(listOf("RIGHT"), listOf("RIGHT"), listOf("RIGHT")),
                icon = "baseline_fast_forward"
            ),
            // 11. Phát lại — 数字 0 跳回开头
            ShortKeyModel(
                type = "dailymotion",
                name = "Replay",
                keys = listOf(listOf("0"), listOf("0"), listOf("0")),
                icon = "baseline_replay"
            ),
            // 12. Toàn màn hình — F
            ShortKeyModel(
                type = "dailymotion",
                name = "Fullscreen",
                keys = listOf(listOf("F"), listOf("F"), listOf("F")),
                icon = "baseline_fit_screen"
            ),
            // 13. Đóng cửa sổ
            ShortKeyModel(
                type = "dailymotion",
                name = "Close Window",
                keys = listOf(
                    listOf("ALT", "F4"),
                    listOf("CMD", "W"),
                    listOf("ALT", "F4")
                ),
                icon = "baseline_close"
            )
        )
    }

    // ============================================================
    // 4. VIDEO PLAYER (WIN 11) — 14 items (10 keyboard + 2 window + 2 group)
    // ============================================================
    val shortKeysVideoPlayer: List<ShortKeyModel> by lazy {
        listOf(
            // 1. Lùi về
            ShortKeyModel(
                type = "video_player",
                name = "Back",
                keys = listOf(listOf("LEFT"), listOf("LEFT"), listOf("LEFT")),
                icon = "baseline_arrow_back"
            ),
            // 2. Phát / Tạm dừng — Space
            ShortKeyModel(
                type = "video_player",
                name = "Play / Pause",
                keys = listOf(listOf("SPACE"), listOf("SPACE"), listOf("SPACE")),
                icon = "baseline_play_arrow"
            ),
            // 3. Tiếp — RIGHT
            ShortKeyModel(
                type = "video_player",
                name = "Next",
                keys = listOf(listOf("RIGHT"), listOf("RIGHT"), listOf("RIGHT")),
                icon = "baseline_arrow_forward"
            ),
            // 4. Tăng âm lượng — UP
            ShortKeyModel(
                type = "video_player",
                name = "Volume Up",
                keys = listOf(listOf("UP"), listOf("UP"), listOf("UP")),
                icon = "baseline_volume_up"
            ),
            // 5. Tắt tiếng — M
            ShortKeyModel(
                type = "video_player",
                name = "Mute",
                keys = listOf(listOf("M"), listOf("M"), listOf("M")),
                icon = "baseline_volume_off"
            ),
            // 6. Giảm âm lượng — DOWN
            ShortKeyModel(
                type = "video_player",
                name = "Volume Down",
                keys = listOf(listOf("DOWN"), listOf("DOWN"), listOf("DOWN")),
                icon = "baseline_volume_down"
            ),
            // 7. Phụ đề (无官方V键，保留占位)
            ShortKeyModel(
                type = "video_player",
                name = "Captions",
                keys = listOf(listOf("V"), listOf("V"), listOf("V")),
                icon = "baseline_caption"
            ),
            // 8. Tua lại — LEFT
            ShortKeyModel(
                type = "video_player",
                name = "Rewind",
                keys = listOf(listOf("LEFT"), listOf("LEFT"), listOf("LEFT")),
                icon = "baseline_fast_rewind"
            ),
            // 9. Dừng (无官方S键，保留占位)
            ShortKeyModel(
                type = "video_player",
                name = "Stop",
                keys = listOf(listOf("S"), listOf("S"), listOf("S")),
                icon = "baseline_stop"
            ),
            // 10. Tua tới — RIGHT
            ShortKeyModel(
                type = "video_player",
                name = "Fast Forward",
                keys = listOf(listOf("RIGHT"), listOf("RIGHT"), listOf("RIGHT")),
                icon = "baseline_fast_forward"
            ),
            // 11. Phát lại (无官方P键，保留占位)
            ShortKeyModel(
                type = "video_player",
                name = "Replay",
                keys = listOf(listOf("P"), listOf("P"), listOf("P")),
                icon = "baseline_replay"
            ),
            // 12. Toàn màn hình — F
            ShortKeyModel(
                type = "video_player",
                name = "Fullscreen",
                keys = listOf(listOf("F"), listOf("F"), listOf("F")),
                icon = "baseline_fit_screen"
            ),
            // 13. Đóng cửa sổ
            ShortKeyModel(
                type = "video_player",
                name = "Close Window",
                keys = listOf(
                    listOf("ALT", "F4"),
                    listOf("CMD", "W"),
                    listOf("ALT", "F4")
                ),
                icon = "baseline_close"
            )
        )
    }

    // ============================================================
    // 5. MUSIC PLAYER (WIN 11) — 14 items (10 keyboard + 2 window + 2 group)
    // ============================================================
    val shortKeysMusicPlayer: List<ShortKeyModel> by lazy {
        listOf(
            // 1. Lùi về
            ShortKeyModel(
                type = "music_player",
                name = "Back",
                keys = listOf(
                    listOf("CTRL", "LEFT"),
                    listOf("CMD", "LEFT"),
                    listOf("CTRL", "LEFT")
                ),
                icon = "baseline_arrow_back"
            ),
            // 2. Phát / Tạm dừng — Space
            ShortKeyModel(
                type = "music_player",
                name = "Play / Pause",
                keys = listOf(listOf("SPACE"), listOf("SPACE"), listOf("SPACE")),
                icon = "baseline_play_arrow"
            ),
            // 3. Bài tiếp
            ShortKeyModel(
                type = "music_player",
                name = "Next Track",
                keys = listOf(
                    listOf("CTRL", "RIGHT"),
                    listOf("CMD", "RIGHT"),
                    listOf("CTRL", "RIGHT")
                ),
                icon = "baseline_arrow_forward"
            ),
            // 4. Tăng âm lượng
            ShortKeyModel(
                type = "music_player",
                name = "Volume Up",
                keys = listOf(listOf("VOLUME_UP"), listOf("VOLUME_UP"), listOf("VOLUME_UP")),
                icon = "baseline_volume_up"
            ),
            // 5. Tắt tiếng
            ShortKeyModel(
                type = "music_player",
                name = "Mute",
                keys = listOf(listOf("MUTE"), listOf("MUTE"), listOf("MUTE")),
                icon = "baseline_volume_off"
            ),
            // 6. Giảm âm lượng
            ShortKeyModel(
                type = "music_player",
                name = "Volume Down",
                keys = listOf(listOf("VOLUME_DOWN"), listOf("VOLUME_DOWN"), listOf("VOLUME_DOWN")),
                icon = "baseline_volume_down"
            ),
            // 7. Yêu thích (无官方快捷键，保留占位)
            ShortKeyModel(
                type = "music_player",
                name = "Like",
                keys = listOf(
                    listOf("ALT", "SHIFT", "B"),
                    listOf("CMD", "SHIFT", "B"),
                    listOf("ALT", "SHIFT", "B")
                ),
                icon = "outline_favorite"
            ),
            // 8. Tua lại
            ShortKeyModel(
                type = "music_player",
                name = "Rewind",
                keys = listOf(
                    listOf("CTRL", "LEFT"),
                    listOf("CMD", "LEFT"),
                    listOf("CTRL", "LEFT")
                ),
                icon = "baseline_fast_rewind"
            ),
            // 9. Dừng — Space
            ShortKeyModel(
                type = "music_player",
                name = "Stop",
                keys = listOf(listOf("SPACE"), listOf("SPACE"), listOf("SPACE")),
                icon = "baseline_stop"
            ),
            // 10. Tua tới
            ShortKeyModel(
                type = "music_player",
                name = "Fast Forward",
                keys = listOf(
                    listOf("CTRL", "RIGHT"),
                    listOf("CMD", "RIGHT"),
                    listOf("CTRL", "RIGHT")
                ),
                icon = "baseline_fast_forward"
            ),
            // 11. Phát lại / Bài trước
            ShortKeyModel(
                type = "music_player",
                name = "Previous Track",
                keys = listOf(
                    listOf("CTRL", "LEFT"),
                    listOf("CMD", "LEFT"),
                    listOf("CTRL", "LEFT")
                ),
                icon = "baseline_replay"
            ),
            // 12. Toàn màn hình
            ShortKeyModel(
                type = "music_player",
                name = "Fullscreen",
                keys = listOf(listOf("F"), listOf("F"), listOf("F")),
                icon = "baseline_fit_screen"
            ),
            // 13. Đóng cửa sổ
            ShortKeyModel(
                type = "music_player",
                name = "Close Window",
                keys = listOf(
                    listOf("ALT", "F4"),
                    listOf("CMD", "W"),
                    listOf("ALT", "F4")
                ),
                icon = "baseline_close"
            )
        )
    }

    val shortKeyPreset: List<ShortKeyPresetModel> = listOf(
        ShortKeyPresetModel("media", "Media", "ic_media"),
        ShortKeyPresetModel("youtube", "YouTube", "ic_youtube"),
        ShortKeyPresetModel("dailymotion", "Dailymotion", "ic_dailymotion"),
        ShortKeyPresetModel("video_player", "Video Player", "ic_videoplay"),
        ShortKeyPresetModel("music_player", "Music Player", "ic_musicplay"),
    )
}