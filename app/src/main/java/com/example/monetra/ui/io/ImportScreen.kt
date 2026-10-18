package com.example.monetra.ui.io

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.monetra.core.money.Money
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.io.ImportPreview
import com.example.monetra.data.io.ImportResult
import com.example.monetra.data.io.RecordsCsv
import com.example.monetra.data.io.Xlsx
import com.example.monetra.di.AppContainer
import com.example.monetra.ui.appViewModel
import com.example.monetra.ui.components.SubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface ImportStep {
    data object Pick : ImportStep
    data object Working : ImportStep
    data class Preview(val preview: ImportPreview) : ImportStep
    data class Done(val result: ImportResult, val accounts: List<AccountEntity>) : ImportStep
    data class Failed(val message: String) : ImportStep
}

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    CSV("CSV", "text/csv", "csv"),
    EXCEL("Excel", Xlsx.MIME, "xlsx"),
}

enum class ExportSpan(val label: String) { MONTH("This month"), YEAR("This year"), ALL("Everything") }

/** Neutral example rows shown on screen and written by "Save sample file". */
internal val SAMPLE_ROWS = listOf(
    listOf("TIME", "TYPE", "AMOUNT", "CATEGORY", "ACCOUNT", "NOTES"),
    listOf("Oct 03, 2026 8:15 AM", "(-) Expense", "120.00", "Food", "Cash", "lunch"),
    listOf("Oct 05, 2026 6:00 PM", "(+) Income", "15000.00", "Salary", "Bank", ""),
    listOf("Oct 06, 2026 9:30 AM", "(*) Transfer", "2000.00", "", "Bank -> Cash", "ATM"),
)

internal fun sampleCsv() = SAMPLE_ROWS.joinToString("\n", postfix = "\n") { row ->
    row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
}

class ImportViewModel(private val c: AppContainer) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun dismissMessage() { _message.value = null }

    private fun write(uri: Uri, resolver: ContentResolver, done: String, block: suspend (java.io.Writer) -> Unit) = viewModelScope.launch {
        _message.value = try {
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.bufferedWriter().use { block(it) } }
            done
        } catch (e: Exception) { "Couldn't write the file: ${e.message ?: e.javaClass.simpleName}" }
    }

    private fun writeBytes(uri: Uri, resolver: ContentResolver, done: String, block: suspend (java.io.OutputStream) -> Unit) = viewModelScope.launch {
        _message.value = try {
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.buffered().use { block(it) } }
            done
        } catch (e: Exception) { "Couldn't write the file: ${e.message ?: e.javaClass.simpleName}" }
    }

    fun saveSample(uri: Uri, format: ExportFormat, resolver: ContentResolver) = when (format) {
        ExportFormat.CSV -> write(uri, resolver, "Sample saved. Fill it in a spreadsheet, save as CSV, then import it.") { it.write(sampleCsv()) }
        ExportFormat.EXCEL -> writeBytes(uri, resolver, "Sample saved. Fill it in, then import it.") {
            Xlsx.write(it, SAMPLE_ROWS, numericColumns = setOf(2), widths = listOf(22, 14, 14, 18, 22, 32))
        }
    }

    fun export(uri: Uri, span: ExportSpan, format: ExportFormat, resolver: ContentResolver) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val (start, end) = when (span) {
            ExportSpan.MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            ExportSpan.YEAR -> today.withDayOfYear(1).let { it to it.plusYears(1) }
            ExportSpan.ALL -> null to null
        }
        val from = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
        val to = end?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        val done = "Exported ${span.label.lowercase()} as ${format.label}."
        when (format) {
            ExportFormat.CSV -> write(uri, resolver, done) { c.data.exportCsv(it, from, to) }
            ExportFormat.EXCEL -> writeBytes(uri, resolver, done) { c.data.exportXlsx(it, from, to) }
        }
    }

    private val _step = MutableStateFlow<ImportStep>(ImportStep.Pick)
    val step = _step.asStateFlow()

    fun load(uri: Uri, resolver: ContentResolver) = viewModelScope.launch {
        _step.value = ImportStep.Working
        _step.value = try {
            val parsed = withContext(Dispatchers.IO) { readFile(uri, resolver) } ?: return@launch run { _step.value = ImportStep.Failed("Couldn't open that file.") }
            if (parsed.rows.isEmpty()) ImportStep.Failed(parsed.errors.firstOrNull() ?: "No records found in that file.")
            else ImportStep.Preview(c.importer.preview(parsed))
        } catch (e: Exception) {
            ImportStep.Failed("Couldn't read that file: ${e.message}")
        }
    }

    /** Tells Excel from CSV by the file's first bytes ("PK", a zip), not its name, since pickers often hide the extension. */
    private fun readFile(uri: Uri, resolver: ContentResolver): com.example.monetra.data.io.ParseResult? {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val records = try { Xlsx.read(bytes.inputStream()) } catch (e: Exception) {
                return com.example.monetra.data.io.ParseResult(emptyList(), listOf("Couldn't read that Excel file. Save it as .xlsx and try again."))
            }
            if (records.isEmpty()) com.example.monetra.data.io.ParseResult(emptyList(), listOf("The first sheet is empty."))
            else RecordsCsv.parseRecords(records)
        } else RecordsCsv.parse(bytes.inputStream().bufferedReader())
    }

    fun commit(preview: ImportPreview) = viewModelScope.launch {
        _step.value = ImportStep.Working
        val result = c.importer.commit(preview.rows)
        _step.value = ImportStep.Done(result, result.accountIds.mapNotNull { c.accounts.get(it) })
    }

    fun setStartingBalances(accounts: List<AccountEntity>, values: Map<Long, Money>) = viewModelScope.launch {
        accounts.forEach { a -> values[a.id]?.let { c.accounts.save(a.copy(initialBalance = it.minor)) } }
    }

    fun reset() { _step.value = ImportStep.Pick }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(onBack: () -> Unit) {
    val vm = appViewModel { ImportViewModel(it) }
    val step by vm.step.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.load(it, resolver) } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    var span by remember { mutableStateOf(ExportSpan.MONTH) }
    var format by remember { mutableStateOf(ExportFormat.CSV) }
    val sampleCsvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { it?.let { u -> vm.saveSample(u, ExportFormat.CSV, resolver) } }
    val sampleXlsxOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.EXCEL.mime)) { it?.let { u -> vm.saveSample(u, ExportFormat.EXCEL, resolver) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { it?.let { u -> vm.export(u, span, ExportFormat.CSV, resolver) } }
    val exportXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.EXCEL.mime)) { it?.let { u -> vm.export(u, span, ExportFormat.EXCEL, resolver) } }
    val message by vm.message.collectAsStateWithLifecycle()

    SubScreen("Import / export", onBack) {
        when (val s = step) {
            ImportStep.Pick -> {
                Section("Import")
                Text("Each row of the file is one record, in a CSV or an Excel (.xlsx) sheet. Only the first sheet is read. Accounts and categories are matched by name and created if new.",
                    style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                // The action sits above the layout so it is never below the fold.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("text/*", Xlsx.MIME, "application/octet-stream", "application/vnd.ms-excel")) }) {
                        Text("Choose CSV or Excel file")
                    }
                    OutlinedButton(onClick = { sampleCsvOut.launch("monetra-sample.csv") }) { Text("Sample CSV") }
                    OutlinedButton(onClick = { sampleXlsxOut.launch("monetra-sample.xlsx") }) { Text("Sample Excel") }
                }
                Spacer(Modifier.height(16.dp))
                Text("File layout", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SampleTable()
                Spacer(Modifier.height(8.dp))
                Text("Amounts use a dot for decimals and no currency sign. In Excel, dates can be text like the sample or real date cells.",
                    style = MaterialTheme.typography.bodySmall, color = muted)

                Section("Export")
                Text("Writes your records in the same layout, so the file opens in any spreadsheet and imports back here. Not a full backup.",
                    style = MaterialTheme.typography.bodyMedium, color = muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportSpan.entries.forEach { FilterChip(span == it, { span = it }, label = { Text(it.label) }) }
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { FilterChip(format == it, { format = it }, label = { Text(it.label) }) }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = {
                    val name = "monetra-${span.name.lowercase()}-${stamp.format(LocalDate.now())}.${format.ext}"
                    if (format == ExportFormat.CSV) exportCsv.launch(name) else exportXlsx.launch(name)
                }) {
                    Text("Export ${span.label.lowercase()} as ${format.label}")
                }
            }
            ImportStep.Working -> CircularProgressIndicator()
            is ImportStep.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = vm::reset) { Text("Choose another file") }
            }
            is ImportStep.Preview -> {
                val p = s.preview
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${p.rows.size} records", style = MaterialTheme.typography.headlineSmall)
                    if (p.firstDate != null && p.lastDate != null) {
                        Text("${dateFormat.format(p.firstDate)} – ${dateFormat.format(p.lastDate)}", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (p.duplicates > 0) Text("${p.duplicates} are already in Monetra and will be skipped.", color = muted)
                    if (p.newAccounts.isNotEmpty()) Text("New accounts: ${p.newAccounts.joinToString()}")
                    if (p.newCategories.isNotEmpty()) Text("New categories: ${p.newCategories.joinToString()}")
                    if (p.errors.isNotEmpty()) {
                        Text("${p.errors.size} lines can't be imported:", color = MaterialTheme.colorScheme.error)
                        p.errors.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { vm.commit(p) }, enabled = p.newRecords > 0) {
                        Text(if (p.newRecords > 0) "Import ${p.newRecords} records" else "Nothing new to import")
                    }
                    OutlinedButton(onClick = vm::reset) { Text("Choose another file") }
                }
            }
            is ImportStep.Done -> StartingBalances(s, vm, onBack)
        }
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = vm::dismissMessage, text = { Text(m) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } })
    }
}

@Composable
private fun StartingBalances(s: ImportStep.Done, vm: ImportViewModel, onBack: () -> Unit) {
    val inputs = remember { mutableStateMapOf<Long, String>() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Imported ${s.result.imported} records", style = MaterialTheme.typography.headlineSmall)
        if (s.result.skipped > 0) Text("${s.result.skipped} duplicates skipped.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "If your export doesn't start from day one, set what each account held before its first record " +
                "so balances match what you had.",
            style = MaterialTheme.typography.bodyLarge,
        )
        s.accounts.forEach { a ->
            val text = inputs[a.id] ?: Money(a.initialBalance).toBigDecimal().toPlainString()
            OutlinedTextField(
                text, { inputs[a.id] = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(16) },
                label = { Text("${a.name} starting balance") }, singleLine = true,
                isError = Money.parse(text) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = {
            vm.setStartingBalances(s.accounts, inputs.mapNotNull { (id, t) -> Money.parse(t)?.let { id to it } }.toMap())
            onBack()
        }) { Text("Done") }
    }
}

private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
}

/**
 * The sample as a real table turned sideways: one line per column of the file, one column per
 * example record, so all six fields fit a phone without scrolling.
 */
@Composable
private fun SampleTable() {
    val header = SAMPLE_ROWS.first()
    val examples = SAMPLE_ROWS.drop(1)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { contentDescription = "Sample CSV: " + examples.joinToString("; ") { it.joinToString(", ") } },
    ) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(vertical = 8.dp)) {
            Spacer(Modifier.width(84.dp))
            listOf("Expense", "Income", "Transfer").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
            }
        }
        header.forEachIndexed { col, name ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp).padding(start = 10.dp))
                examples.forEach { row ->
                    val cell = row[col]
                    Text(
                        cell.ifEmpty { "–" }, style = MaterialTheme.typography.bodySmall,
                        color = if (cell.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}
