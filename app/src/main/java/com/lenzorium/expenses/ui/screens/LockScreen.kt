package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lenzorium.expenses.data.SettingsStore
import com.lenzorium.expenses.ui.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Full-screen PIN pad that sits on top of the app (so unsaved form input underneath is kept). */
@Composable
fun LockScreen(settings: SettingsStore, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }
    val bio = settings.current.biometricEnabled && Biometrics.isAvailable(context)

    LaunchedEffect(Unit) {
        if (bio) Biometrics.prompt(context, "Unlock", onSuccess = onUnlocked)
    }

    // Unlock as soon as the typed digits match; wrong PINs are reported when "Unlock" is pressed.
    LaunchedEffect(pin) {
        if (pin.length >= 4) {
            val candidate = pin
            val ok = withContext(Dispatchers.Default) { settings.checkPin(candidate) }
            if (ok) onUnlocked()
        }
    }
    var submit by remember { mutableStateOf(0) }
    LaunchedEffect(submit) {
        if (submit == 0) return@LaunchedEffect
        checking = true
        val ok = withContext(Dispatchers.Default) { settings.checkPin(pin) }
        checking = false
        if (ok) { onUnlocked(); return@LaunchedEffect }
        attempts++
        pin = ""
        if (attempts >= 5) {
            error = "Too many attempts. Wait 30 seconds."
            delay(30_000)
            attempts = 0
            error = null
        } else {
            error = "Incorrect PIN"
        }
    }

    Surface(
        Modifier
            .fillMaxSize()
            // Swallow touches so nothing beneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            com.lenzorium.expenses.ui.components.BrandMark(72)
            Spacer(Modifier.height(16.dp))
            com.lenzorium.expenses.ui.components.BrandWordmark(large = false)
            Spacer(Modifier.height(4.dp))
            Text("Enter your PIN to continue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(
                        Modifier.size(14.dp).background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape,
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (bio) "bio" else "", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier
                                .size(72.dp)
                                .background(if (k.length == 1) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background, CircleShape)
                                .clickable(enabled = k.isNotEmpty() && attempts < 5) {
                                    error = null
                                    when (k) {
                                        "del" -> pin = pin.dropLast(1)
                                        "bio" -> Biometrics.prompt(context, "Unlock", onSuccess = onUnlocked)
                                        else -> if (pin.length < 8) pin += k
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete")
                                "bio" -> Icon(Icons.Rounded.Fingerprint, "Use biometrics", tint = MaterialTheme.colorScheme.primary)
                                else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Button(
                onClick = { submit++ },
                enabled = pin.length >= 4 && !checking && attempts < 5,
            ) { Text("Unlock") }
        }
    }
}
