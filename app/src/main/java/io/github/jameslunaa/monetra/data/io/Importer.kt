package io.github.jameslunaa.monetra.data.io

import androidx.room.withTransaction
import io.github.jameslunaa.monetra.data.db.AccountEntity
import io.github.jameslunaa.monetra.data.db.CategoryEntity
import io.github.jameslunaa.monetra.data.db.CategoryType
import io.github.jameslunaa.monetra.data.db.MonetraDatabase
import io.github.jameslunaa.monetra.data.db.TransactionEntity
import io.github.jameslunaa.monetra.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId

data class ImportPreview(
    val rows: List<CsvRow>,
    val errors: List<String>,
    val newRecords: Int,
    val duplicates: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val newCategories: List<String>,
    val newAccounts: List<String>,
)

data class ImportResult(val imported: Int, val skipped: Int, val accountIds: List<Long>)

/** Turns parsed CSV rows into records, creating missing accounts and categories by name. */
class Importer(private val db: MonetraDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {

    suspend fun preview(parsed: ParseResult): ImportPreview {
        val accounts = db.accounts().getAll().associateBy { it.name.lowercase() }
        val categories = db.categories().getAll().associateBy { key(it.name, it.type) }
        var dupes = 0
        for (r in parsed.rows) {
            val acc = accounts[r.account.lowercase()] ?: continue
            val catId = if (r.isTransfer) null else categories[key(r.category, r.categoryType)]?.id ?: continue
            if (isDuplicate(r, acc.id, catId)) dupes++
        }
        val dates = parsed.rows.map { it.time.toLocalDate() }
        return ImportPreview(
            rows = parsed.rows,
            errors = parsed.errors,
            newRecords = parsed.rows.size - dupes,
            duplicates = dupes,
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
            newCategories = parsed.rows.filter { !it.isTransfer && key(it.category, it.categoryType) !in categories }
                .map { it.category }.distinctBy { it.lowercase() },
            newAccounts = parsed.rows.flatMap { listOfNotNull(it.account, it.toAccount) }
                .filter { it.lowercase() !in accounts }.distinctBy { it.lowercase() },
        )
    }

    suspend fun commit(rows: List<CsvRow>): ImportResult = db.withTransaction {
        val accounts = db.accounts().getAll().associateBy { it.name.lowercase() }.toMutableMap()
        val categories = db.categories().getAll().associateBy { key(it.name, it.type) }.toMutableMap()
        val now = System.currentTimeMillis()
        var imported = 0
        var skipped = 0
        val touched = linkedSetOf<Long>()
        suspend fun account(name: String) = accounts.getOrPut(name.lowercase()) {
            val a = AccountEntity(name = name, icon = "wallet", colorIndex = (accounts.size * 3 + 4) % 12,
                sortOrder = db.accounts().nextSortOrder())
            a.copy(id = db.accounts().insert(a))
        }
        for (r in rows) {
            val acc = account(r.account)
            val to = r.toAccount?.let { account(it) }
            val catId = if (r.isTransfer) null else categories.getOrPut(key(r.category, r.categoryType)) {
                val c = CategoryEntity(name = r.category, type = r.categoryType, icon = guessIcon(r.category),
                    colorIndex = (categories.size * 5) % 12, sortOrder = db.categories().nextSortOrder(r.categoryType))
                c.copy(id = db.categories().insert(c))
            }.id
            touched += acc.id
            to?.let { touched += it.id }
            if (isDuplicate(r, acc.id, catId)) { skipped++; continue }
            db.transactions().insert(
                TransactionEntity(
                    type = r.txType,
                    amount = r.amount.minor, accountId = acc.id, toAccountId = to?.id, categoryId = catId,
                    occurredAt = r.millis(), note = r.note, createdAt = now, updatedAt = now,
                ),
            )
            imported++
        }
        ImportResult(imported, skipped, touched.toList())
    }

    private suspend fun isDuplicate(r: CsvRow, accountId: Long, categoryId: Long?) =
        db.transactions().countMatching(r.millis(), r.txType, r.amount.minor, accountId, categoryId, r.note) > 0

    /** Rows with no category: transfers and loan movements (imported without their debt link). */
    private val CsvRow.isTransfer get() = toAccount != null || special == TxType.DEBT_IN || special == TxType.DEBT_OUT
    private val CsvRow.txType get() = when {
        special != null -> special
        toAccount != null -> TxType.TRANSFER
        income -> TxType.INCOME
        else -> TxType.EXPENSE
    }

    private fun CsvRow.millis() = time.atZone(zone).toInstant().toEpochMilli()

    private fun key(name: String, type: CategoryType) = type.name + ":" + name.lowercase()

    private val CsvRow.categoryType get() = if (income) CategoryType.INCOME else CategoryType.EXPENSE

    private fun guessIcon(name: String) = when (name.lowercase()) {
        "returnable" -> "returnable"
        "unrecorded" -> "unrecorded"
        "salary" -> "salary"
        "refunds", "refund" -> "refund"
        else -> "other"
    }
}
