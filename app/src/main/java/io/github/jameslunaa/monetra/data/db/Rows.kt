package io.github.jameslunaa.monetra.data.db

/** A record joined with its account and category for display. */
data class RecordRow(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val occurredAt: Long,
    val note: String,
    val accountId: Long,
    val accountName: String,
    val toAccountId: Long?,
    val toAccountName: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Int?,
    val debtId: Long? = null,
    val debtDirection: DebtDirection? = null,
    val personName: String? = null,
)

data class DebtRow(
    val id: Long,
    val personId: Long,
    val personName: String,
    val direction: DebtDirection,
    val amount: Long,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Int?,
    val note: String,
    val createdAt: Long,
    val remaining: Long,
) {
    val settled get() = remaining <= 0
    val paid get() = amount - remaining
}

data class Totals(val income: Long, val expense: Long) {
    val net get() = income - expense
}

data class AccountBalance(
    val id: Long,
    val name: String,
    val icon: String,
    val colorIndex: Int,
    val initialBalance: Long,
    val balance: Long,
)

/** One income or expense amount, for bucketing by day or month in local time. */
data class AmountPoint(val type: TxType, val amount: Long, val occurredAt: Long, val categoryId: Long?)

data class AccountFlow(val accountId: Long, val income: Long, val expense: Long)

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val colorIndex: Int,
    val total: Long,
)
