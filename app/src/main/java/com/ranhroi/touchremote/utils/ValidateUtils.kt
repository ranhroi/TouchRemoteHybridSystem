// utils/ValidateUtils.kt
package com.ranhroi.touchremote.utils

import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.pager.path.PathModel

object ValidateUtils {

    fun validateShortKey(
        state: ShortKeyModel,
        onDismiss: () -> Unit,
        onSendError: () -> Unit,
        onNameError: () -> Unit,
        onHotkeyError: () -> Unit,
        onIconError: () -> Unit,
        onHandler: () -> Unit = {}
    ) {
        val validators = listOf(
            state.send to onSendError,
            state.name to onNameError,
            state.keys.joinToString(",") to onHotkeyError,
            state.icon to onIconError
        )

        for ((field, errorCallback) in validators) {
            if (field.toString().isBlank()) {
                errorCallback()
                return
            }
        }

        if (state.keys.isNotEmpty()) {
            onHandler()
            onDismiss()
        } else {
            onHotkeyError()
        }
    }

    fun validatePath(
        state: PathModel,
        onDismiss: () -> Unit,
        onSendError: () -> Unit,
        onNameError: () -> Unit,
        onIconError: () -> Unit,
        onHandler: () -> Unit
    ) {
        val validators = listOf(
            state.send to onSendError,
            state.name to onNameError,
            state.icon to onIconError
        )

        for ((field, errorCallback) in validators) {
            if (field.toString().isBlank()) {
                errorCallback()
                return
            }
        }

        if (state.path.isNotEmpty()) {
            onHandler()
            onDismiss()
        } else {
            onNameError()
        }
    }
}