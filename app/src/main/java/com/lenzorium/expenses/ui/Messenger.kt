package com.lenzorium.expenses.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide short messages ("Expense saved", "Deleted · Undo"). Lives above the navigation graph,
 * so a message raised as a screen closes is still shown and its Undo still works.
 */
class Messenger(private val host: SnackbarHostState?, private val scope: CoroutineScope?) {
    fun show(text: String, actionLabel: String? = null, onAction: (suspend () -> Unit)? = null) {
        val h = host ?: return
        val s = scope ?: return
        s.launch {
            h.currentSnackbarData?.dismiss()
            val r = h.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                withDismissAction = actionLabel == null,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalMessenger = staticCompositionLocalOf { Messenger(null, null) }

/** Light confirmation tick after a successful save. */
fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )
}
