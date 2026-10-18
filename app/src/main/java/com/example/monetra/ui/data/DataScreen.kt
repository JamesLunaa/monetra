package com.example.monetra.ui.data

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.monetra.data.io.Backup
import com.example.monetra.di.AppContainer
import com.example.monetra.ui.appViewModel
import com.example.monetra.ui.components.SubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DataViewModel(private val c: AppContainer) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _pending = MutableStateFlow<Backup?>(null)
    /** A parsed backup waiting for the user to confirm the restore. */
    val pending = _pending.asStateFlow()

    private fun run(block: suspend () -> String) = viewModelScope.launch {
        _message.value = try { block() } catch (e: Exception) { "Something went wrong: ${e.message ?: e.javaClass.simpleName}" }
    }

    fun backup(uri: Uri, resolver: ContentResolver) = run {
        val text = c.data.backup()
        withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(text) } }
        "Backup saved. Keep a copy somewhere other than this phone."
    }

    fun read(uri: Uri, resolver: ContentResolver) = run {
        val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
        _pending.value = c.data.read(text)
        ""
    }

    fun confirmRestore() = _pending.value?.let { b ->
        _pending.value = null
        run { c.data.restore(b); "Restored ${b.transactions.size} records, ${b.accounts.size} accounts and ${b.categories.size} categories." }
    }

    fun cancelRestore() { _pending.value = null }

    fun reset() = run { c.data.reset(); "All data deleted. Monetra is back to its first-run state." }

    fun dismissMessage() { _message.value = null }
}

private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataScreen(onBack: () -> Unit) {
    val vm = appViewModel { DataViewModel(it) }
    val resolver = LocalContext.current.contentResolver
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val today = stamp.format(LocalDate.now())

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.backup(u, resolver) } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.read(u, resolver) } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SubScreen("Backup & reset", onBack) {
        Section("Backup")
        Text("One file with every record, account, category and budget. Restore it on this or another phone.",
            style = MaterialTheme.typography.bodyMedium, color = muted)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save.launch("monetra-backup-$today.json") }) { Text("Back up now") }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }) { Text("Restore…") }
        }

        Section("Delete & reset")
        Text("Deletes every record, account, category and budget, then starts fresh with Cash and the default categories. Your preferences stay.",
            style = MaterialTheme.typography.bodyMedium, color = muted)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Delete all data…") }
    }

    pending?.let { b ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text("Replace everything with this backup?") },
            text = {
                Text("The backup has ${b.transactions.size} records, ${b.accounts.size} accounts, ${b.categories.size} categories, " +
                    "${b.budgets.size} budget limits and ${b.debts.size} debts. Everything currently in Monetra will be replaced.")
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete all data?") },
            text = { Text("This can't be undone. Back up first if you might want any of it later.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.reset() }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    message?.takeIf { it.isNotEmpty() }?.let { m ->
        AlertDialog(onDismissRequest = vm::dismissMessage, text = { Text(m) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } })
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
}
