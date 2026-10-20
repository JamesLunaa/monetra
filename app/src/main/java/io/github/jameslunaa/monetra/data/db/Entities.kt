package io.github.jameslunaa.monetra.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * INCOME/EXPENSE count toward totals; TRANSFER moves money between accounts.
 * REFUND is money paid back for an expense: it lowers that category's spending and adds to the account.
 * DEBT_IN / DEBT_OUT move cash for a loan (borrowing, lending, repaying principal) without being income or spending.
 */
enum class TxType { INCOME, EXPENSE, TRANSFER, REFUND, DEBT_IN, DEBT_OUT }

/** Which records a category applies to. Transfers have no category. */
enum class CategoryType { INCOME, EXPENSE }

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorIndex: Int,
    /** Minor units (centavos). */
    val initialBalance: Long = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "categories", indices = [Index("type")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val colorIndex: Int,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/**
 * One record. INCOME/EXPENSE need [categoryId]; TRANSFER needs [toAccountId] and has no category.
 * [amount] is always positive, in minor units; [type] gives the direction.
 */
@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(DebtEntity::class, ["id"], ["debtId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("occurredAt"), Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("debtId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** Epoch millis. */
    val occurredAt: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    /** Set when this record lends, borrows or repays as part of a [DebtEntity]. */
    val debtId: Long? = null,
)

@Serializable
@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["categoryId", "yearMonth"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** "2026-10". */
    val yearMonth: String,
    val limitAmount: Long,
    /** How the monthly limit is shown: what's left per day, per week, or for the month (bills). */
    @ColumnInfo(defaultValue = "DAILY")
    val cadence: BudgetCadence = BudgetCadence.DAILY,
)

enum class BudgetCadence { DAILY, WEEKLY, MONTHLY }

@Serializable
@Entity(tableName = "people", indices = [Index(value = ["name"], unique = true)])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

enum class DebtDirection { OWED_TO_ME, I_OWE }

/**
 * Money between the user and one person. Remaining = [amount] minus linked repayments.
 * With a [categoryId] the debt belongs to an expense (a split bill, or someone paying for you):
 * repaying it is a REFUND (owed to me) or an EXPENSE (I owe) in that category. Without one it is
 * cash lent or borrowed, and every movement is DEBT_IN / DEBT_OUT.
 */
@Serializable
@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(PersonEntity::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("personId"), Index("categoryId")],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val direction: DebtDirection,
    /** Minor units, positive. */
    val amount: Long,
    val categoryId: Long? = null,
    /** The expense this debt was split from, if any (informational; deleting it leaves the debt). */
    val sourceTxId: Long? = null,
    val note: String = "",
    val createdAt: Long,
    val settledAt: Long? = null,
)
