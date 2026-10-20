package io.github.jameslunaa.monetra.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.id, t.type, t.amount, t.occurredAt, t.note,
               t.accountId, a.name AS accountName, t.toAccountId, ta.name AS toAccountName,
               t.categoryId, c.name AS categoryName, c.icon AS categoryIcon, c.colorIndex AS categoryColor,
               t.debtId, d.direction AS debtDirection, p.name AS personName
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN debts d ON d.id = t.debtId
        LEFT JOIN people p ON p.id = d.personId
        WHERE t.occurredAt >= :start AND t.occurredAt < :end
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeRecords(start: Long, end: Long, accountId: Long? = null): Flow<List<RecordRow>>

    /** Income and expense in a range. Refunds lower spending; transfers and debt movements don't count. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount WHEN type = 'REFUND' THEN -amount END), 0) AS expense
        FROM transactions WHERE occurredAt >= :start AND occurredAt < :end
        """
    )
    fun observeTotals(start: Long, end: Long): Flow<Totals>

    @Query(
        """
        SELECT c.id AS categoryId, c.name, c.icon, c.colorIndex,
               SUM(CASE WHEN t.type = 'REFUND' THEN -t.amount ELSE t.amount END) AS total
        FROM transactions t JOIN categories c ON c.id = t.categoryId
        WHERE (t.type = :type OR (:type = 'EXPENSE' AND t.type = 'REFUND'))
          AND t.occurredAt >= :start AND t.occurredAt < :end
        GROUP BY c.id HAVING total != 0 ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(type: TxType, start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT accountId,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount WHEN type = 'REFUND' THEN -amount END), 0) AS expense
        FROM transactions WHERE occurredAt >= :start AND occurredAt < :end GROUP BY accountId
        """
    )
    fun observeAccountFlows(start: Long, end: Long): Flow<List<AccountFlow>>

    @Query(
        """
        SELECT CASE WHEN type = 'REFUND' THEN 'EXPENSE' ELSE type END AS type,
               CASE WHEN type = 'REFUND' THEN -amount ELSE amount END AS amount,
               occurredAt, categoryId
        FROM transactions
        WHERE type IN ('INCOME', 'EXPENSE', 'REFUND') AND occurredAt >= :start AND occurredAt < :end
        ORDER BY occurredAt
        """
    )
    fun observePoints(start: Long, end: Long): Flow<List<AmountPoint>>

    /** Matches note, category, account, or the amount typed as digits (e.g. "450" or "450.5"). */
    @Query(
        """
        SELECT t.id, t.type, t.amount, t.occurredAt, t.note,
               t.accountId, a.name AS accountName, t.toAccountId, ta.name AS toAccountName,
               t.categoryId, c.name AS categoryName, c.icon AS categoryIcon, c.colorIndex AS categoryColor,
               t.debtId, d.direction AS debtDirection, p.name AS personName
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN debts d ON d.id = t.debtId
        LEFT JOIN people p ON p.id = d.personId
        WHERE t.note LIKE :like OR c.name LIKE :like OR a.name LIKE :like OR ta.name LIKE :like
           OR (:amount IS NOT NULL AND CAST(t.amount AS TEXT) LIKE :amount)
        ORDER BY t.occurredAt DESC, t.id DESC
        LIMIT 300
        """
    )
    fun search(like: String, amount: String?): Flow<List<RecordRow>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt, id")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt, id")
    suspend fun getRange(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE occurredAt = :occurredAt AND type = :type AND amount = :amount AND accountId = :accountId
          AND categoryId IS :categoryId AND note = :note
        """
    )
    suspend fun countMatching(occurredAt: Long, type: TxType, amount: Long, accountId: Long, categoryId: Long?, note: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE occurredAt >= :start AND occurredAt < :end")
    suspend fun countBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Insert suspend fun insert(tx: TransactionEntity): Long
    @Insert suspend fun insertAll(txs: List<TransactionEntity>)
    @Update suspend fun update(tx: TransactionEntity)
    @Delete suspend fun delete(tx: TransactionEntity)
}

@Dao
interface AccountDao {
    /** Balance = initial + money in (income, refunds, borrowing/repaid loans) − money out (spending, lending) ± transfers. */
    @Query(
        """
        SELECT a.id, a.name, a.icon, a.colorIndex, a.initialBalance,
               a.initialBalance
               + COALESCE((SELECT SUM(CASE WHEN t.type IN ('INCOME', 'REFUND', 'DEBT_IN') THEN t.amount ELSE -t.amount END)
                           FROM transactions t WHERE t.accountId = a.id), 0)
               + COALESCE((SELECT SUM(t.amount) FROM transactions t
                           WHERE t.type = 'TRANSFER' AND t.toAccountId = a.id), 0) AS balance
        FROM accounts a WHERE a.archived = 0 ORDER BY a.sortOrder, a.id
        """
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts")
    suspend fun nextSortOrder(): Int

    @Insert suspend fun insert(account: AccountEntity): Long
    @Insert suspend fun insertAll(accounts: List<AccountEntity>)
    @Update suspend fun update(account: AccountEntity)
    @Delete suspend fun delete(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE type = :type")
    suspend fun nextSortOrder(type: CategoryType): Int

    @Insert suspend fun insert(category: CategoryEntity): Long
    @Insert suspend fun insertAll(categories: List<CategoryEntity>)
    @Update suspend fun update(categories: List<CategoryEntity>)
    @Delete suspend fun delete(category: CategoryEntity)
}

@Dao
interface BudgetDao {
    /** Every limit change; [io.github.jameslunaa.monetra.core.budget.Budgets] resolves which applies when. */
    @Query("SELECT * FROM budgets ORDER BY yearMonth")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY yearMonth")
    suspend fun getAll(): List<BudgetEntity>

    /** Sets the limit for a category from [BudgetEntity.yearMonth] onward (replacing a change made that same month). */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId AND yearMonth > :yearMonth")
    suspend fun deleteLaterChanges(categoryId: Long, yearMonth: String)
}

/** Wipes user data, children first so foreign keys hold. Used by restore and Delete & Reset. */
@Dao
interface WipeDao {
    @Query("DELETE FROM transactions") suspend fun transactions()
    @Query("DELETE FROM budgets") suspend fun budgets()
    @Query("DELETE FROM categories") suspend fun categories()
    @Query("DELETE FROM accounts") suspend fun accounts()
    @Query("DELETE FROM debts") suspend fun debts()
    @Query("DELETE FROM people") suspend fun people()
}

@Dao
interface DebtDao {
    /** Each debt with its person, category and what is still to be paid. */
    @Query(
        """
        SELECT d.id, d.personId, p.name AS personName, d.direction, d.amount, d.categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, c.colorIndex AS categoryColor,
               d.note, d.createdAt,
               d.amount - COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.debtId = d.id AND (
                   (d.direction = 'OWED_TO_ME' AND t.type IN ('REFUND', 'DEBT_IN')) OR
                   (d.direction = 'I_OWE' AND t.type IN ('EXPENSE', 'DEBT_OUT')))), 0) AS remaining
        FROM debts d JOIN people p ON p.id = d.personId
        LEFT JOIN categories c ON c.id = d.categoryId
        ORDER BY d.createdAt DESC, d.id DESC
        """
    )
    fun observeDebts(): Flow<List<DebtRow>>

    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findPerson(name: String): PersonEntity?

    @Insert suspend fun insertPerson(person: PersonEntity): Long
    @Insert suspend fun insertPeople(people: List<PersonEntity>)
    @Insert suspend fun insert(debt: DebtEntity): Long
    @Insert suspend fun insertAll(debts: List<DebtEntity>)

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: Long): DebtEntity?

    @Query("SELECT * FROM debts ORDER BY id") suspend fun getAll(): List<DebtEntity>
    @Query("SELECT * FROM people ORDER BY id") suspend fun getAllPeople(): List<PersonEntity>

    @Query("DELETE FROM transactions WHERE debtId = :debtId")
    suspend fun deleteTransactions(debtId: Long)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: Long)

    /** People with no debts left, after a delete. */
    @Query("DELETE FROM people WHERE id NOT IN (SELECT personId FROM debts)")
    suspend fun deleteUnusedPeople()
}
