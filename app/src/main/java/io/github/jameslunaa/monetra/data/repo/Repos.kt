package io.github.jameslunaa.monetra.data.repo

import androidx.room.withTransaction
import io.github.jameslunaa.monetra.core.time.Period
import io.github.jameslunaa.monetra.core.budget.LimitChange
import io.github.jameslunaa.monetra.data.db.AccountEntity
import io.github.jameslunaa.monetra.data.db.BudgetCadence
import io.github.jameslunaa.monetra.data.db.BudgetEntity
import io.github.jameslunaa.monetra.data.db.CategoryEntity
import io.github.jameslunaa.monetra.data.db.CategoryType
import io.github.jameslunaa.monetra.data.db.MonetraDatabase
import io.github.jameslunaa.monetra.data.db.TransactionEntity
import io.github.jameslunaa.monetra.data.db.TxType
import kotlinx.coroutines.flow.map
import java.time.YearMonth

class TransactionRepo(private val db: MonetraDatabase) {
    private val dao = db.transactions()

    fun records(period: Period, accountId: Long? = null) =
        dao.observeRecords(period.startMillis(), period.endMillis(), accountId)

    fun totals(period: Period) = dao.observeTotals(period.startMillis(), period.endMillis())

    /** Net of everything before [period], for carry-over. */
    fun totalsBefore(period: Period) = dao.observeTotals(Long.MIN_VALUE, period.startMillis())

    fun points(start: Long, end: Long) = dao.observePoints(start, end)

    fun recordsBetween(start: Long, end: Long) = dao.observeRecords(start, end)

    /** Free-text search; digits also match amounts ("450" finds ₱450.00). */
    fun search(query: String): kotlinx.coroutines.flow.Flow<List<io.github.jameslunaa.monetra.data.db.RecordRow>> {
        val q = query.trim()
        val digits = q.replace(",", "").takeIf { it.matches(Regex("\\d+(\\.\\d{0,2})?")) }
        // Amounts are stored in centavos: "450" -> "450__" (450.00 to 450.99), "450.5" -> "45050".
        val amount = digits?.let {
            val (whole, frac) = (it.split('.') + "").let { p -> p[0] to p[1] }
            if (it.contains('.')) whole + frac.padEnd(2, '0') else "${whole}__"
        }
        return dao.search("%$q%", amount)
    }

    fun accountFlows(period: Period) = dao.observeAccountFlows(period.startMillis(), period.endMillis())

    fun allTimeTotals() = dao.observeTotals(Long.MIN_VALUE, Long.MAX_VALUE)

    fun categoryTotals(type: TxType, period: Period) =
        dao.observeCategoryTotals(type, period.startMillis(), period.endMillis())

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(tx: TransactionEntity): Long {
        validate(tx)
        val now = System.currentTimeMillis()
        return if (tx.id == 0L) dao.insert(tx.copy(createdAt = now, updatedAt = now))
        else { dao.update(tx.copy(updatedAt = now)); tx.id }
    }

    suspend fun delete(tx: TransactionEntity) = dao.delete(tx)

    /** Re-inserts a record removed by delete, keeping its id (for Undo). */
    suspend fun restore(tx: TransactionEntity) = dao.insert(tx)

    private fun validate(tx: TransactionEntity) {
        require(tx.amount > 0) { "Amount must be positive" }
        when (tx.type) {
            TxType.TRANSFER -> require(tx.toAccountId != null && tx.toAccountId != tx.accountId && tx.categoryId == null)
            TxType.DEBT_IN, TxType.DEBT_OUT -> require(tx.debtId != null && tx.categoryId == null && tx.toAccountId == null)
            else -> require(tx.categoryId != null && tx.toAccountId == null)
        }
    }
}

class AccountRepo(private val db: MonetraDatabase) {
    private val dao = db.accounts()

    val balances = dao.observeBalances()
    val accounts = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(account: AccountEntity): Long =
        if (account.id == 0L) dao.insert(account.copy(sortOrder = dao.nextSortOrder()))
        else { dao.update(account); account.id }

    /** Deletes an account with no records; otherwise archives it so history stays intact. */
    suspend fun deleteOrArchive(account: AccountEntity): Boolean = db.withTransaction {
        if (db.transactions().countForAccount(account.id) == 0) { dao.delete(account); true }
        else { dao.update(account.copy(archived = true)); false }
    }
}

class CategoryRepo(private val db: MonetraDatabase) {
    private val dao = db.categories()

    val categories = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(category: CategoryEntity): Long =
        if (category.id == 0L) dao.insert(category.copy(sortOrder = dao.nextSortOrder(category.type)))
        else { dao.update(listOf(category)); category.id }

    /** Persists a new order for the categories of one type. */
    suspend fun reorder(ordered: List<CategoryEntity>) =
        dao.update(ordered.mapIndexed { i, c -> c.copy(sortOrder = i) })

    /** Deletes a category with no records; otherwise archives it. */
    suspend fun deleteOrArchive(category: CategoryEntity): Boolean = db.withTransaction {
        if (db.transactions().countForCategory(category.id) == 0) { dao.delete(category); true }
        else { dao.update(listOf(category.copy(archived = true))); false }
    }

    fun typeFor(tx: TxType) = if (tx == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
}

class BudgetRepo(private val db: MonetraDatabase) {
    private val dao = db.budgets()

    val changes = dao.observeAll().map { list ->
        list.map { LimitChange(it.categoryId, YearMonth.parse(it.yearMonth), it.limitAmount, it.cadence) }
    }

    /** Sets [categoryId]'s monthly limit from [month] onward; later changes are replaced. 0 removes the budget. */
    suspend fun setLimit(categoryId: Long, month: YearMonth, limit: Long, cadence: BudgetCadence) = db.withTransaction {
        dao.upsert(BudgetEntity(categoryId = categoryId, yearMonth = month.toString(), limitAmount = limit, cadence = cadence))
        dao.deleteLaterChanges(categoryId, month.toString())
    }
}
