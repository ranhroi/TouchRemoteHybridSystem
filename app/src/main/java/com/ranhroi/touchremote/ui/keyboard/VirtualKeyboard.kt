package com.ranhroi.touchremote.ui.keyboard

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.screens.main.SelectAppState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VirtualKeyboard(
    modifier: Modifier = Modifier,
    inputManager: InputManager? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tạo EditText ẩn để gọi bàn phím hệ thống
    val editText = remember {
        object : AppCompatEditText(context) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
                outAttrs.actionLabel = null
                outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
                outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE

                return object : BaseInputConnection(this, true) {
                    private val editable = SpannableStringBuilder()
                    override fun getEditable() = editable

                    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
                    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        text?.toString()?.let {
                            inputManager?.onTextInput(it)
                        }
                        return super.commitText(text, newCursorPosition)
                    }

                    override fun deleteSurroundingText(
                        beforeLength: Int,
                        afterLength: Int
                    ): Boolean {
                        if (editable.isNotEmpty()) editable.delete(
                            editable.length - 1,
                            editable.length
                        )
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: KeyEvent?): Boolean {
                        if (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                            inputManager?.sendEnter()
                            return true
                        }
                        if (event?.keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                            inputManager?.sendBackspace()
                            return true
                        }
                        return super.sendKeyEvent(event)
                    }
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.size(0.dp),
        factory = {

            editText.apply {

                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundColor(Color.TRANSPARENT)

                requestFocus()

                post {
                    val imm =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            editText.clearFocus()
        }
    }
}
