package com.ranhroi.touchremote.ui.screens.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.components.StringOptionsDropdownMenu
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.IconsMap
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    settingApp: SettingsViewModel = hiltViewModel(),
) {

    val view = LocalView.current
    val context = LocalContext.current
    val config by settingApp.settings.collectAsStateWithLifecycle()
    val isLoaded by settingApp.isLoaded.collectAsStateWithLifecycle()
    val isSave by settingApp.isSave.collectAsStateWithLifecycle()
    val languageMode by settingApp.selectedLanguageMode.collectAsStateWithLifecycle()
    val currentLanguageName = settingApp.getCurrentDisplayLanguageName(LocalContext.current)


    if (!isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var state by remember(config) { mutableStateOf(config) }

    var progress by remember(config) {
        mutableFloatStateOf(config.progress / 100f)
    }

    var scroll by remember(config) {
        mutableFloatStateOf(config.scroll / 100f)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            }
                            view.playSoundClick()
                        }
                    ) {
                        Icon(painterResource(R.drawable.baseline_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(vertical = 20.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ========== CARD NGÔN NGỮ ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_language))

                    StringOptionsDropdownMenu(
                        text = stringResource(R.string.language_select),
                        state = ConstantsUtils.getDisplayLabel(languageMode),
                        optionsList = ConstantsUtils.languageOptionsList,
                        showAutoOption = true,
                        onSelectedOption = { selected ->
                            settingApp.changeLanguage(selected)
                            view.playSoundClick()
                        }
                    )

                    Text(
                        text = if (languageMode == "auto") {
                            stringResource(R.string.language_current_auto, ConstantsUtils.getDisplayLabel(currentLanguageName))
                        } else {
                            stringResource(R.string.language_current_selected, ConstantsUtils.getDisplayLabel(languageMode))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ========== CARD CURSOR ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_cursor))

                    Text(stringResource(R.string.cursor_speed, state.progress))

                    Slider(
                        value = progress,
                        onValueChange = {
                            progress = it
                            state = state.copy(progress = (it * 100).toInt())
                        }
                    )

                    Text(stringResource(R.string.scroll_speed, state.scroll))

                    Slider(
                        value = scroll,
                        onValueChange = {
                            scroll = it
                            state = state.copy(scroll = (it * 100).toInt())
                        }
                    )
                }
            }

            // ========== CARD MOUSE ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_mouse))

                    SettingCheckBox(
                        stringResource(R.string.mouse_left_hand),
                        state.isLeftHand
                    ) {
                        state = state.copy(isLeftHand = it)
                        view.playSoundClick()
                    }

                    SettingCheckBox(
                        stringResource(R.string.mouse_natural_scroll),
                        state.isScrollDirection
                    ) {
                        state = state.copy(isScrollDirection = it)
                        view.playSoundClick()
                    }

                    SettingCheckBox(
                        stringResource(R.string.mouse_vibrate),
                        state.isVibrate
                    ) {
                        state = state.copy(isVibrate = it)
                        view.playSoundClick()
                    }
                }
            }

            // ========== CARD UI ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_ui))

                    SettingCheckBox(stringResource(R.string.ui_show_floating), state.isFloating) {
                        state = state.copy(isFloating = it)
                        view.playSoundClick()
                    }

                    SettingCheckBox(stringResource(R.string.ui_show_hotkeys_weight), state.isHotkeysWeight) {
                        state = state.copy(isHotkeysWeight = it)
                        view.playSoundClick()
                    }

                    SettingCheckBox(stringResource(R.string.ui_show_button_text), state.isButtonText) {
                        state = state.copy(isButtonText = it)
                        view.playSoundClick()
                    }

                    SettingCheckBox(stringResource(R.string.ui_show_rotation), state.isRotation) {
                        state = state.copy(isRotation = it)
                        view.playSoundClick()
                    }

                    SettingCheckBox(
                        stringResource(R.string.ui_show_media_multitasking),
                        state.isMediaMultitasking
                    ) {
                        state = state.copy(isMediaMultitasking = it)
                        view.playSoundClick()
                    }
                }
            }

            // ========== CARD SERVICE ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_service))

                    SettingCheckBox(stringResource(R.string.service_auto_task), state.isAutoTask) {
                        state = state.copy(isAutoTask = it)
                        view.playSoundClick()
                    }
                }
            }

            // ========== CARD SCREEN MIRROR ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_screen_mirror))

                    SettingCheckBox(stringResource(R.string.screen_mirror_auto), state.isAutoScreenMirror) {
                        state = state.copy(isAutoScreenMirror = it)
                        view.playSoundClick()
                    }
                }
            }

            // ========== CARD AUTO CONNECTION ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_auto_connection))

                    SettingCheckBox(stringResource(R.string.auto_connection), state.isAutoConnection) {
                        state = state.copy(isAutoConnection = it)
                        view.playSoundClick()
                    }
                }
            }

            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem("Reset ShortKey keys" )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reset system ShortKeys")
                        IconButton(
                            onClick = {
                                settingApp.resetToDefault()
                                context.showToast("Reset successful hotkey")
                                view.playSoundClick()
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_refresh),
                                contentDescription = "Reset cache"
                            )
                        }
                    }
                }
            }

            // ========== CARD ICON CACHE (THÊM MỚI) ==========
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionItem(stringResource(R.string.instruction_icon_cache))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.icon_cache_reset))
                        IconButton(
                            onClick = {
                                // ✅ Reset cache icon
                                IconsMap.resetCache()
                                context.showToast("Đã đặt lại cache icon")
                                view.playSoundClick()
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_refresh),
                                contentDescription = "Reset cache"
                            )
                        }
                    }
                }
            }

            // ========== SAVE BUTTON ==========
            Card(
                onClick = {
                    settingApp.updateSettings(state)
                    view.playSoundClick()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSave && state == config) {
                        Icon(painterResource(R.drawable.baseline_save), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.saved))
                    } else {
                        Text(stringResource(R.string.save_settings))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCheckBox(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun InstructionItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "•",
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}