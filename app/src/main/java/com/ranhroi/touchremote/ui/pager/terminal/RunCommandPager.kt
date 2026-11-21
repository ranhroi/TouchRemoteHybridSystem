package com.ranhroi.touchremote.ui.pager.terminal

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.HeaderMenuModel
import com.ranhroi.touchremote.ui.components.HeaderHorizontal
import com.ranhroi.touchremote.ui.components.StringOptionsDropdownMenu
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.utils.getStringSafe
import com.ranhroi.touchremote.utils.playSoundClick


@Composable
fun RunCommandPager(
    view: View,
    connection: ConnectionViewModel,
    hybridState: HybridConnectionModel,
    viewModel: RunCommandViewModel = viewModel()
) {
    val context = LocalContext.current

    val focusManager = LocalFocusManager.current
    var commandText by remember { mutableStateOf("") }

    var commandOption by remember { mutableStateOf( context.getStringSafe(R.string.cmd_choose_command))}
    val uiState = viewModel.uiState


    // Khởi tạo ViewModel với ConnectionState
    LaunchedEffect(hybridState) {
        viewModel.init(hybridState)
    }

    // Dọn dẹp khi rời màn hình
    DisposableEffect(Unit) {
        onDispose {
            viewModel.init(HybridConnectionModel())
            viewModel.reset()
        }
    }


    val leftMenuItems = listOf(
        HeaderMenuModel(
            isStatusDot = true,
            isConnected = hybridState.isConnected,
            isVisible = { true }
        ),
        HeaderMenuModel(
            text = if (hybridState.isConnected)
                stringResource(R.string.app_connected)
            else
                stringResource(R.string.app_disconnected),
            textColor = if (hybridState.isConnected) Color(0xFF4CAF50) else Color(0xFFE57373),
            isVisible = { true }
        ),
        HeaderMenuModel(
            text = hybridState.os,
            textColor = Color.Gray,
            isVisible = { true }
        )
    )

    val rightMenuItems = listOf(
        HeaderMenuModel(
            icon = "baseline_close",
            text = stringResource(R.string.cmd_clear),
            onClick = { viewModel.clearOutput() }
        ),
        HeaderMenuModel(
            icon = "baseline_refresh",
            onClick = { connection.pingCommand() }
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HeaderHorizontal(
            view = view,
            modifier = Modifier.fillMaxWidth(),
            leftItems = leftMenuItems,
            rightItems = rightMenuItems
        )

        // Input Layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth().height(45.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoCompleteTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    suggestions = uiState.suggestions,
                    onSuggestionSelected = { commandText = it },
                    onTextChanged = { text ->
                        viewModel.requestSuggestions(text)
                    },
                    onExecute = {
                        if (commandText.isNotBlank()) {
                            viewModel.runCommand(commandText)
                            commandText = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            viewModel.runCommand(commandText)
                            commandText = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = uiState.isConnected && !uiState.isLoading,
                    modifier = Modifier.fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cmd_send))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Output Layout
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(3.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = uiState.commandOutput,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }


        // Quick commands
        StringOptionsDropdownMenu(
            modifier = Modifier.height(60.dp),
            enabled = uiState.isConnected,
            text = stringResource(R.string.cmd_quick_commands),
            state = commandOption,
            options = listOf(
                stringResource(R.string.cmd_ipconfig),
                stringResource(R.string.cmd_ping),
                stringResource(R.string.cmd_systeminfo),
                stringResource(R.string.cmd_sfc),
                stringResource(R.string.cmd_chkdsk),
                stringResource(R.string.cmd_tasklist),
                stringResource(R.string.cmd_netstat),
                stringResource(R.string.cmd_dir),
                stringResource(R.string.cmd_getmac),
                stringResource(R.string.cmd_driverquery),
                stringResource(R.string.cmd_tracert),
                stringResource(R.string.cmd_nslookup),
                stringResource(R.string.cmd_pathping),
                stringResource(R.string.cmd_arp),
                stringResource(R.string.cmd_powercfg),
                stringResource(R.string.cmd_gpresult),
                stringResource(R.string.cmd_assoc),
                stringResource(R.string.cmd_cipher),
                stringResource(R.string.cmd_hostname),
                stringResource(R.string.cmd_wmic))

        ) {
            commandOption = it
            viewModel.runCommand(it)
            view.playSoundClick()
        }
    }
}


@Composable
fun AutoCompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(value, suggestions) {
        if (value.isBlank()) emptyList() else suggestions.filter {
            it.contains(value, ignoreCase = true)
        }.take(8)
    }

    // Box này đóng vai trò là "mỏ neo" cho DropdownMenu
    Box(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                onTextChanged(newValue)
                showDropdown = newValue.isNotBlank() && filteredSuggestions.isNotEmpty()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp) // Chiều cao lý tưởng để nằm trong thanh 50dp
                .padding(horizontal = 4.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onExecute() }),
            decorationBox = { innerTextField ->
                // Tự vẽ khung cho TextField
                Row(
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(stringResource(R.string.cmd_enter_command), style = TextStyle(color = Color.Gray, fontSize = 14.sp))
                        }
                        innerTextField() // Đây là nơi chữ bạn nhập hiển thị
                    }
                }
            }
        )

        // DropdownMenu phải nằm NGOÀI BasicTextField nhưng TRONG Box để tính toán vị trí
        DropdownMenu(
            expanded = showDropdown && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { showDropdown = false },
            // offset giúp đẩy danh sách xuống dưới thanh bar một chút nếu cần
            properties = PopupProperties(focusable = false), // Quan trọng: để không chiếm focus của bàn phím
            modifier = Modifier
                .fillMaxWidth(0.8f) // Rộng bằng 80% thanh nhập liệu
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp)).zIndex(1f)
        ) {
            filteredSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = suggestion,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSuggestionSelected(suggestion)
                        onTextChanged(suggestion)
                        showDropdown = false
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
