package com.example.monetra.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.monetra.lock.AppLock
import com.example.monetra.ui.components.LogoMark
import com.example.monetra.ui.theme.MonetraTheme
import kotlinx.coroutines.launch

/** True when the device can use a strong or weak biometric right now. */
fun biometricAvailable(activity: FragmentActivity) =
    BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Monetra")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build(),
    )
}

/** Full-screen lock on the ube field: the mark, PIN dots, a keypad, and the fingerprint key when enabled. */
@Composable
fun LockScreen(lock: AppLock, biometric: Boolean) {
    val c = MonetraTheme.colors
    val activity = LocalContext.current as FragmentActivity
    val canBio = remember { biometric && biometricAvailable(activity) }
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (canBio) promptBiometric(activity) { lock.unlockedByBiometric() } }

    fun press(d: Char) {
        if (checking || pin.length >= 6) return
        error = false
        pin += d
        // Try at 4, 5 and 6 digits so the user never has to press "enter".
        if (pin.length >= 4) {
            checking = true
            scope.launch {
                val ok = lock.tryPin(pin)
                checking = false
                if (!ok && pin.length == 6) { error = true; pin = "" }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(c.field).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp).padding(24.dp)) {
            LogoMark(c.logo, c.logoShadow, Modifier.size(72.dp), contentDescription = null)
            Spacer(Modifier.height(20.dp))
            Text("Enter your PIN", style = MaterialTheme.typography.titleLarge, color = c.onField)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics { contentDescription = "${pin.length} digits entered" }) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.onField else c.onField.copy(alpha = 0.25f)))
                }
            }
            Text(
                if (error) "Wrong PIN. Try again." else " ",
                style = MaterialTheme.typography.bodyMedium, color = c.fieldExpense,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(16.dp))
            val keys = listOf("123", "456", "789")
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { d -> Key(d.toString()) { press(d) } } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (canBio) KeyIcon("Use fingerprint") { promptBiometric(activity) { lock.unlockedByBiometric() } }
                    else Spacer(Modifier.padding(6.dp).size(72.dp))
                Key("0") { press('0') }
                KeyIcon("Delete digit", backspace = true) { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    val c = MonetraTheme.colors
    Box(
        Modifier.padding(6.dp).size(72.dp).clip(CircleShape).background(c.onField.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MonetraTheme.amounts.total, color = c.onField) }
}

@Composable
private fun KeyIcon(description: String, backspace: Boolean = false, onClick: () -> Unit) {
    val c = MonetraTheme.colors
    Box(
        Modifier.padding(6.dp).size(72.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (backspace) Icons.AutoMirrored.Rounded.Backspace else Icons.Rounded.Fingerprint, null, tint = c.onField, modifier = Modifier.size(28.dp))
    }
}
