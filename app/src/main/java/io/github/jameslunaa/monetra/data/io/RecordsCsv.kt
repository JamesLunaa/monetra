package io.github.jameslunaa.monetra.data.io

import io.github.jameslunaa.monetra.core.money.Money
import java.io.Reader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One record from a records CSV or spreadsheet. */
data class CsvRow(
    val line: Int,
    val time: LocalDateTime,
    val income: Boolean,
    val amount: Money,
    val category: String,
    val account: String,
    val note: String,
    /** Destination account for a transfer (Monetra's own export writes ACCOUNT as "From -> To"). */
    val toAccount: String? = null,
    /** Monetra's own refund and loan rows; null for plain income/expense/transfer. */
    val special: io.github.jameslunaa.monetra.data.db.TxType? = null,
)

data class ParseResult(val rows: List<CsvRow>, val errors: List<String>)

/**
 * Parses the records CSV format:
 * `"TIME","TYPE","AMOUNT","CATEGORY","ACCOUNT","NOTES"` with rows like
 * `"Jul 21, 2026 6:24 PM","(-) Expense","139.00","Food","Cash",""`.
 */
object RecordsCsv {
    private val timeFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)
    const val TRANSFER_ARROW = " -> "
    private val header = listOf("TIME", "TYPE", "AMOUNT", "CATEGORY", "ACCOUNT", "NOTES")

    fun parse(reader: Reader): ParseResult = parseRecords(splitRecords(reader.readText().removePrefix("\ufeff")))

    /** Reads already-split rows (header first), from a CSV or a spreadsheet. Row numbers in errors are the sheet's own. */
    fun parseRecords(records: List<Pair<Int, List<String>>>): ParseResult {
        if (records.isEmpty()) return ParseResult(emptyList(), listOf("The file is empty."))
        val head = records.first().second.map { it.trim().uppercase() }
        if (head.take(header.size) != header) {
            return ParseResult(emptyList(), listOf("This doesn't look like a records file (expected columns ${header.joinToString()})."))
        }
        val rows = mutableListOf<CsvRow>()
        val errors = mutableListOf<String>()
        for ((line, f) in records.drop(1)) {
            if (f.all { it.isBlank() }) continue
            if (f.size < 5) { errors += "Line $line: expected 6 columns, found ${f.size}."; continue }
            val time = parseTime(f[0]) ?: run {
                errors += "Line $line: can't read the date \"${f[0]}\"."; continue
            }
            val transfer = f[1].contains("Transfer", ignoreCase = true) && f[4].contains(TRANSFER_ARROW)
            val special = when {
                f[1].contains("Refund", ignoreCase = true) -> io.github.jameslunaa.monetra.data.db.TxType.REFUND
                f[1].contains("Loan in", ignoreCase = true) -> io.github.jameslunaa.monetra.data.db.TxType.DEBT_IN
                f[1].contains("Loan out", ignoreCase = true) -> io.github.jameslunaa.monetra.data.db.TxType.DEBT_OUT
                else -> null
            }
            val income = when {
                transfer || special != null -> false
                f[1].contains("Income", ignoreCase = true) -> true
                f[1].contains("Expense", ignoreCase = true) -> false
                else -> { errors += "Line $line: \"${f[1]}\" records aren't supported yet."; continue }
            }
            val amount = Money.parse(f[2])?.abs()
            if (amount == null || amount.isZero) { errors += "Line $line: can't read the amount \"${f[2]}\"."; continue }
            val (from, to) = if (transfer) f[4].split(TRANSFER_ARROW, limit = 2).map { it.trim() }.let { it[0] to it[1] } else f[4].trim() to null
            rows += CsvRow(line, time, income, amount, f[3].trim(), from, f.getOrElse(5) { "" }.trim(), to, special)
        }
        return ParseResult(rows, errors)
    }

    private val isoFormats = listOf("yyyy-MM-dd H:mm", "yyyy-MM-dd'T'H:mm", "yyyy-MM-dd H:mm:ss", "yyyy-MM-dd'T'H:mm:ss")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    /**
     * Reads the export's own text (like "Jul 21, 2026 6:24 PM"), a few ISO spellings, a bare date (midnight), or a spreadsheet date serial
     * (days since 1899-12-30), which is what Excel stores once a date cell has been edited.
     */
    internal fun parseTime(text: String): LocalDateTime? {
        val t = text.trim()
        try { return LocalDateTime.parse(t, timeFormat) } catch (_: DateTimeParseException) {}
        for (f in isoFormats) try { return LocalDateTime.parse(t, f) } catch (_: DateTimeParseException) {}
        try { return java.time.LocalDate.parse(t).atStartOfDay() } catch (_: DateTimeParseException) {}
        val serial = t.toBigDecimalOrNull() ?: return null
        if (serial.signum() <= 0 || serial > java.math.BigDecimal(2_958_465)) return null
        val minutes = serial.multiply(java.math.BigDecimal(1440)).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return java.time.LocalDate.of(1899, 12, 30).atStartOfDay().plusMinutes(minutes)
    }

    /** RFC 4180-style splitting, tolerant of spaces after closing quotes. Returns (line number, fields). */
    internal fun splitRecords(text: String): List<Pair<Int, List<String>>> {
        val out = mutableListOf<Pair<Int, List<String>>>()
        var fields = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var i = 0
        fun endField() { fields += cur.toString(); cur.clear() }
        fun endRecord() {
            endField()
            if (!(fields.size == 1 && fields[0].isBlank())) out += recordLine to fields
            fields = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cur.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> { if (ch == '\n') line++; cur.append(ch) }
                }
            } else when (ch) {
                '"' -> { cur.clear(); inQuotes = true }
                ',' -> endField()
                '\r' -> Unit
                '\n' -> { endRecord(); line++; recordLine = line }
                ' ', '\t' -> if (cur.isNotEmpty()) cur.append(ch)
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return out
    }
}
