package com.example.monetra.data.io

import androidx.room.withTransaction
import com.example.monetra.core.money.Money
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.db.BudgetEntity
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.DebtEntity
import com.example.monetra.data.db.PersonEntity
import com.example.monetra.data.db.MonetraDatabase
import com.example.monetra.data.db.TransactionEntity
import com.example.monetra.data.db.TxType
import com.example.monetra.data.db.seed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Writes records in the import CSV layout, so exports open in any spreadsheet and re-import into Monetra. */
object CsvWriter {
    private val timeFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm a", Locale.ENGLISH)

    val HEADER = listOf("TIME", "TYPE", "AMOUNT", "CATEGORY", "ACCOUNT", "NOTES")

    /** Header first, then one row per record, as plain text fields. */
    fun rows(
        txs: List<TransactionEntity>,
        accounts: Map<Long, String>,
        categories: Map<Long, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<List<String>> = listOf(HEADER) + txs.map { t ->
        val type = when (t.type) {
            TxType.INCOME -> "(+) Income"
            TxType.EXPENSE -> "(-) Expense"
            TxType.TRANSFER -> "(*) Transfer"
            TxType.REFUND -> "(+) Refund"
            TxType.DEBT_IN -> "(+) Loan in"
            TxType.DEBT_OUT -> "(-) Loan out"
        }
        val account = accounts[t.accountId].orEmpty() +
            (t.toAccountId?.let { RecordsCsv.TRANSFER_ARROW + accounts[it].orEmpty() } ?: "")
        listOf(
            timeFormat.format(Instant.ofEpochMilli(t.occurredAt).atZone(zone)),
            type,
            Money(t.amount).toBigDecimal().toPlainString(),
            t.categoryId?.let { categories[it] }.orEmpty(),
            account,
            t.note,
        )
    }

    fun write(
        out: Writer,
        txs: List<TransactionEntity>,
        accounts: Map<Long, String>,
        categories: Map<Long, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        for (row in rows(txs, accounts, categories, zone)) out.write(row.joinToString(",") { q(it) } + "\n")
    }
}

/** Everything the user owns, at one moment. Restoring replaces all data with this. */
@Serializable
data class Backup(
    val version: Int = VERSION,
    val createdAt: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    // Added in version 2; version 1 backups simply have none.
    val people: List<PersonEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
) {
    companion object {
        const val VERSION = 2
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    }
}

class DataManager(private val db: MonetraDatabase) {
    suspend fun exportCsv(out: Writer, start: Long, end: Long) {
        val accounts = db.accounts().getAll().associate { it.id to it.name }
        val categories = db.categories().getAll().associate { it.id to it.name }
        CsvWriter.write(out, db.transactions().getRange(start, end), accounts, categories)
    }

    /** The same records and layout as [exportCsv], as an Excel workbook (amounts are real numbers, so sheets can sum them). */
    suspend fun exportXlsx(out: java.io.OutputStream, start: Long, end: Long) {
        val accounts = db.accounts().getAll().associate { it.id to it.name }
        val categories = db.categories().getAll().associate { it.id to it.name }
        Xlsx.write(out, CsvWriter.rows(db.transactions().getRange(start, end), accounts, categories),
            numericColumns = setOf(2), widths = listOf(22, 14, 14, 18, 22, 32))
    }

    suspend fun backup(): String = Backup.json.encodeToString(
        Backup.serializer(),
        Backup(
            createdAt = System.currentTimeMillis(),
            accounts = db.accounts().getAll(),
            categories = db.categories().getAll(),
            transactions = db.transactions().getAll(),
            budgets = db.budgets().getAll(),
            people = db.debts().getAllPeople(),
            debts = db.debts().getAll(),
        ),
    )

    /** Parses and checks a backup without touching data, so the screen can confirm what will be restored. */
    fun read(text: String): Backup {
        val b = Backup.json.decodeFromString(Backup.serializer(), text)
        require(b.version <= Backup.VERSION) { "This backup is from a newer version of Monetra." }
        return b
    }

    /** Replaces all data with [backup], atomically: on any failure nothing changes. */
    suspend fun restore(backup: Backup) = db.withTransaction {
        wipe()
        db.accounts().insertAll(backup.accounts)
        db.categories().insertAll(backup.categories)
        db.debts().insertPeople(backup.people)
        db.debts().insertAll(backup.debts)
        db.transactions().insertAll(backup.transactions)
        db.budgets().insertAll(backup.budgets)
    }

    /** Deletes every record, account, category and budget, then restores the first-run defaults. Settings stay. */
    suspend fun reset() = db.withTransaction {
        wipe()
        seed(db.openHelper.writableDatabase)
    }

    private suspend fun wipe() = with(db.wipe()) {
        transactions(); budgets(); debts(); people(); categories(); accounts()
    }
}
