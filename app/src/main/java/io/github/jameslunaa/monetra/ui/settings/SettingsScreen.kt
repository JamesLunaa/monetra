package io.github.jameslunaa.monetra.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.core.money.MoneyFormat
import io.github.jameslunaa.monetra.data.prefs.Settings
import io.github.jameslunaa.monetra.di.AppContainer
import io.github.jameslunaa.monetra.ui.appViewModel
import io.github.jameslunaa.monetra.ui.components.SubScreen
import io.github.jameslunaa.monetra.ui.components.SwitchRow
import io.github.jameslunaa.monetra.ui.theme.ThemeMode
import io.github.jameslunaa.monetra.core.security.Pin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun theme(m: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(m) }
    fun format(f: MoneyFormat) = viewModelScope.launch { c.settings.setMoneyFormat(f) }
    fun firstDay(d: DayOfWeek) = viewModelScope.launch { c.settings.setFirstDayOfWeek(d) }
    fun notes(on: Boolean) = viewModelScope.launch { c.settings.setNotesPreview(on) }

    /** Sets (or replaces) the PIN; the current session stays unlocked. */
    fun setPin(pin: String) = viewModelScope.launch {
        val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Pin.hash(pin) }
        c.settings.setPinHash(hash)
        c.lock.unlockNow()
    }

    /** Turns the lock off if [pin] is right. */
    suspend fun disableLock(pin: String): Boolean {
        val stored = c.settings.settings.first().pinHash ?: return true
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Pin.verify(pin, stored) }
        if (ok) c.settings.setPinHash(null)
        return ok
    }

    fun biometric(on: Boolean) = viewModelScope.launch { c.settings.setBiometric(on) }
    fun reminder(on: Boolean, at: Int) = viewModelScope.launch { c.settings.setReminder(on, at) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val state by vm.settings.collectAsStateWithLifecycle()
    val s: Settings = state ?: return
    val f = s.moneyFormat

    SubScreen("Preferences", onBack) {
        Section("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "Match system").forEach { (m, label) ->
                FilterChip(selected = s.themeMode == m, onClick = { vm.theme(m) }, label = { Text(label) })
            }
        }
        Section("Currency")
        var symbol by remember { mutableStateOf(f.symbol) }
        OutlinedTextField(
            symbol,
            {
                symbol = it.take(4)
                if (symbol.isNotBlank()) vm.format(f.copy(symbol = symbol.trim()))
            },
            label = { Text("Symbol") }, singleLine = true, isError = symbol.isBlank(),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = f.symbolAtStart, onClick = { vm.format(f.copy(symbolAtStart = true)) }, label = { Text("Before amount") })
            FilterChip(selected = !f.symbolAtStart, onClick = { vm.format(f.copy(symbolAtStart = false)) }, label = { Text("After amount") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = f.decimals == 2, onClick = { vm.format(f.copy(decimals = 2)) }, label = { Text("2 decimals") })
            FilterChip(selected = f.decimals == 0, onClick = { vm.format(f.copy(decimals = 0)) }, label = { Text("No decimals") })
        }
        Text("Preview: ${f.format(Money(1234550))}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Section("Weeks start on")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DayOfWeek.SUNDAY to "Sunday", DayOfWeek.MONDAY to "Monday").forEach { (d, label) ->
                FilterChip(selected = s.firstDayOfWeek == d, onClick = { vm.firstDay(d) }, label = { Text(label) })
            }
        }
        LockSection(s, vm)
        ReminderSection(s, vm)
        Section("Records")
        SwitchRow("Show notes in the list", s.notesPreview, vm::notes, "Preview each record's note under its account.")
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LockSection(s: Settings, vm: SettingsViewModel) {
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
    val canBio = remember { io.github.jameslunaa.monetra.ui.lock.biometricAvailable(activity) }
    var settingPin by remember { mutableStateOf(false) }
    var disabling by remember { mutableStateOf(false) }
    Section("App lock")
    SwitchRow("Lock with a PIN", s.lockEnabled, { on -> if (on) settingPin = true else disabling = true },
        "Asks for it when you open Monetra, or come back after a minute away.")
    if (s.lockEnabled) {
        if (canBio) SwitchRow("Unlock with fingerprint", s.biometric, vm::biometric)
        androidx.compose.material3.TextButton(onClick = { settingPin = true }) { Text("Change PIN") }
    }
    if (settingPin) SetPinDialog(onDone = { vm.setPin(it); settingPin = false }) { settingPin = false }
    if (disabling) CheckPinDialog(check = vm::disableLock, onDone = { disabling = false })
}

/** Enter a new 4–6 digit PIN twice. */
@Composable
private fun SetPinDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val mismatch = second.length >= first.length && second.isNotEmpty() && second != first
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PinField(first, { first = it }, "New PIN", supporting = "4 to 6 digits")
                PinField(second, { second = it }, "Repeat PIN", isError = mismatch)
                if (mismatch) Text("The PINs don't match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("There's no way to recover a forgotten PIN except Delete & reset, so pick one you'll remember.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = Pin.isValid(first) && first == second, onClick = { onDone(first) }) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the current PIN before turning the lock off. */
@Composable
private fun CheckPinDialog(check: suspend (String) -> Boolean, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Turn off the lock") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it; wrong = false }, "Current PIN", isError = wrong)
                if (wrong) Text("Wrong PIN.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = Pin.isValid(pin), onClick = {
                scope.launch { if (check(pin)) onDone() else wrong = true }
            }) { Text("Turn off") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(value: String, onValue: (String) -> Unit, label: String, isError: Boolean = false, supporting: String? = null) {
    OutlinedTextField(
        value, { onValue(it.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true, isError = isError,
        supportingText = supporting?.let { s -> { Text(s) } },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(s: Settings, vm: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.reminder(true, s.reminderAt) else denied = true }
    val time = java.time.LocalTime.of(s.reminderAt / 60, s.reminderAt % 60)
    val label = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()).format(time)
    Section("Reminder")
    SwitchRow("Daily reminder", s.reminder, { on ->
        when {
            !on -> vm.reminder(false, s.reminderAt)
            android.os.Build.VERSION.SDK_INT >= 33 && !io.github.jameslunaa.monetra.reminders.Reminders.canNotify(context) ->
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> vm.reminder(true, s.reminderAt)
        }
    }, "At $label, only on days you haven't logged anything.")
    if (s.reminder) androidx.compose.material3.TextButton(onClick = { picking = true }) { Text("Change time") }
    if (denied) Text("Notifications are blocked for Monetra. Allow them in the phone's settings to get the reminder.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (picking) {
        val state = androidx.compose.material3.rememberTimePickerState(time.hour, time.minute, is24Hour = false)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.reminder(true, state.hour * 60 + state.minute); picking = false }) { Text("OK") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel") } },
            text = { androidx.compose.material3.TimePicker(state) },
        )
    }
}
