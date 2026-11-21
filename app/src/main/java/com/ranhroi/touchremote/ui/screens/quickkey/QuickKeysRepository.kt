package com.ranhroi.touchremote.ui.screens.quickkey

import com.ranhroi.touchremote.models.QuickKeysModel


object QuickKeysRepository {
    val quickKeysDefault: List<QuickKeysModel> by lazy {
        listOf(
            // ============================================================
            // ANDROID STUDIO
            // ============================================================
            QuickKeysModel(
                name = "Save All", preset = "Android Studio", icon = "baseline_save_alt",
                keys = listOf(
                    listOf("CTRL", "S"),        // Windows / Linux
                    listOf("CMD", "S"),         // macOS
                    listOf("CTRL", "S")         // Linux
                )
            ),
            QuickKeysModel(
                name = "Find", preset = "Android Studio", icon = "baseline_search",
                keys = listOf(
                    listOf("CTRL", "F"),
                    listOf("CMD", "F"),
                    listOf("CTRL", "F")
                )
            ),
            QuickKeysModel(
                name = "Replace", preset = "Android Studio", icon = "baseline_find_replace",
                keys = listOf(
                    listOf("CTRL", "R"),
                    listOf("CMD", "R"),
                    listOf("CTRL", "R")
                )
            ),
            QuickKeysModel(
                name = "Col", preset = "Android Studio", icon = "baseline_select_all",
                keys = listOf(
                    listOf("ALT", "SHIFT", "INS"),
                    listOf("ALT", "SHIFT", "INS"),
                    listOf("ALT", "SHIFT", "INS")
                )
            ),
            QuickKeysModel(
                name = "Go to Class", preset = "Android Studio", icon = "baseline_code",
                keys = listOf(
                    listOf("CTRL", "N"),
                    listOf("CMD", "O"),
                    listOf("CTRL", "N")
                )
            ),
            QuickKeysModel(
                name = "Go to File", preset = "Android Studio", icon = "baseline_folder",
                keys = listOf(
                    listOf("CTRL", "SHIFT", "N"),
                    listOf("CMD", "SHIFT", "O"),
                    listOf("CTRL", "SHIFT", "N")
                )
            ),
            QuickKeysModel(
                name = "Recent Files", preset = "Android Studio", icon = "baseline_history",
                keys = listOf(
                    listOf("CTRL", "E"),
                    listOf("CMD", "E"),
                    listOf("CTRL", "E")
                )
            ),
            QuickKeysModel(
                name = "Clear Import",
                preset = "Android Studio",
                keys = listOf(
                    listOf("CTRL", "ALT", "O"),
                    listOf("CMD", "OPTION", "O"),
                    listOf("CTRL", "ALT", "O")
                ),
                icon = "baseline_cleaning_services"
            ),
            QuickKeysModel(
                name = "Reformat Code",
                preset = "Android Studio",
                icon = "baseline_format_line_spacing",
                keys = listOf(
                    listOf("CTRL", "ALT", "L"),
                    listOf("CMD", "OPTION", "L"),
                    listOf("CTRL", "ALT", "L")
                )
            ),
            QuickKeysModel(
                name = "Comment", preset = "Android Studio", icon = "baseline_comment",
                keys = listOf(
                    listOf("CTRL", "/"),
                    listOf("CMD", "/"),
                    listOf("CTRL", "/")
                )
            ),

            QuickKeysModel(
                name = "Close Tab", preset = "Android Studio", icon = "baseline_close",
                keys = listOf(
                    listOf("CTRL", "F4"),
                    listOf("CMD", "W"),
                    listOf("CTRL", "F4")
                )
            ),
            QuickKeysModel(
                preset = "Android Studio",
                name = "Undo",
                keys = listOf(
                    listOf("CTRL", "Z"),
                    listOf("CMD", "Z"),
                    listOf("CTRL", "Z")
                ),
                icon = "baseline_undo"
            ),
            QuickKeysModel(
                preset = "Android Studio",
                name = "Redo",
                keys = listOf(
                    listOf("CTRL", "SHIFT", "Z"),
                    listOf("CMD", "SHIFT", "Z"),
                    listOf("CTRL", "SHIFT", "Z")
                ),
                icon = "baseline_redo"
            ),


            // ============================================================
            // VISUAL STUDIO CODE
            // ============================================================
            QuickKeysModel(
                name = "Command Palette", preset = "Visual Studio Code", icon = "baseline_terminal",
                keys = listOf(
                    listOf("CTRL", "SHIFT", "P"),   // Windows
                    listOf("CMD", "SHIFT", "P"),    // macOS
                    listOf("CTRL", "SHIFT", "P")    // Linux
                )
            ),
            QuickKeysModel(
                name = "Quick Open", preset = "Visual Studio Code", icon = "baseline_search",
                keys = listOf(
                    listOf("CTRL", "P"),
                    listOf("CMD", "P"),
                    listOf("CTRL", "P")
                )
            ),
            QuickKeysModel(
                name = "Find", preset = "Visual Studio Code", icon = "baseline_search",
                keys = listOf(
                    listOf("CTRL", "F"),
                    listOf("CMD", "F"),
                    listOf("CTRL", "F")
                )
            ),
            QuickKeysModel(
                name = "Replace", preset = "Visual Studio Code", icon = "baseline_find_replace",
                keys = listOf(
                    listOf("CTRL", "H"),
                    listOf("CMD", "OPTION", "F"),
                    listOf("CTRL", "H")
                )
            ),
            QuickKeysModel(
                name = "Format Document",
                preset = "Visual Studio Code",
                icon = "baseline_format_line_spacing",
                keys = listOf(
                    listOf("SHIFT", "ALT", "F"),        // Windows
                    listOf("SHIFT", "OPTION", "F"),     // macOS
                    listOf("CTRL", "SHIFT", "I")        // Linux (khác Windows)
                )
            ),
            QuickKeysModel(
                name = "Toggle Line Comment",
                preset = "Visual Studio Code",
                icon = "baseline_comment",
                keys = listOf(
                    listOf("CTRL", "/"),
                    listOf("CMD", "/"),
                    listOf("CTRL", "/")
                )
            ),
            QuickKeysModel(
                name = "Go to Definition", preset = "Visual Studio Code", icon = "baseline_code",
                keys = listOf(
                    listOf("F12"),
                    listOf("F12"),
                    listOf("F12")
                )
            ),
            QuickKeysModel(
                name = "Quick Fix", preset = "Visual Studio Code", icon = "baseline_bolt",
                keys = listOf(
                    listOf("CTRL", "."),
                    listOf("CMD", "."),
                    listOf("CTRL", ".")
                )
            ),
            QuickKeysModel(
                name = "Rename Symbol", preset = "Visual Studio Code", icon = "baseline_edit",
                keys = listOf(
                    listOf("F2"),
                    listOf("F2"),
                    listOf("F2")
                )
            ),
            QuickKeysModel(
                name = "Toggle Terminal", preset = "Visual Studio Code", icon = "baseline_terminal",
                keys = listOf(
                    listOf("CTRL", "`"),
                    listOf("CTRL", "`"),
                    listOf("CTRL", "`")
                )
            )
        )
    }
}