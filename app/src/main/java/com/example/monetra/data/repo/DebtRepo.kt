package com.example.monetra.data.repo

import androidx.room.withTransaction
import com.example.monetra.data.db.DebtDirection
import com.example.monetra.data.db.DebtEntity
import com.example.monetra.data.db.DebtRow
import com.example.monetra.data.db.MonetraDatabase
import com.example.monetra.data.db.PersonEntity
import com.example.monetra.data.db.TransactionEntity
import com.example.monetra.data.db.TxType

/**
 * Debts between the user and other people. The money side always goes through ordinary records:
 * lending/borrowing cash is DEBT_OUT/DEBT_IN, repaying a split bill is a REFUND in its category
 * (so the bill's spending shrinks), and repaying someone who paid for you is an EXPENSE then.
 */
class DebtRepo(private val db: MonetraDatabase) {
    private val dao = db.debts()

    val debts = dao.observeDebts()
    val people = dao.observePeople()

    private suspend fun person(name: String): Long =
        dao.findPerson(name.trim())?.id ?: dao.insertPerson(PersonEntity(name = name.trim()))

    private fun now() = System.currentTimeMillis()

    private fun tx(type: TxType, amount: Long, accountId: Long, at: Long, debtId: Long, note: String, categoryId: Long? = null) =
        TransactionEntity(type = type, amount = amount, accountId = accountId, categoryId = categoryId, occurredAt = at,
            note = note, createdAt = now(), updatedAt = now(), debtId = debtId)

    /** Someone owes part of an expense the user paid. No money moves until they repay. */
    suspend fun split(expense: TransactionEntity, name: String, amount: Long, note: String): Long = db.withTransaction {
        require(expense.type == TxType.EXPENSE && amount in 1..expense.amount)
        dao.insert(DebtEntity(personId = person(name), direction = DebtDirection.OWED_TO_ME, amount = amount,
            categoryId = expense.categoryId, sourceTxId = expense.id, note = note, createdAt = expense.occurredAt))
    }

    /** Cash handed to someone: it leaves [accountId] now and comes back as they repay. */
    suspend fun lend(name: String, amount: Long, accountId: Long, at: Long, note: String): Long = db.withTransaction {
        val id = dao.insert(DebtEntity(personId = person(name), direction = DebtDirection.OWED_TO_ME, amount = amount, note = note, createdAt = at))
        db.transactions().insert(tx(TxType.DEBT_OUT, amount, accountId, at, id, note))
        id
    }

    /** Cash borrowed: it arrives in [accountId] now; repaying sends it back. */
    suspend fun borrow(name: String, amount: Long, accountId: Long, at: Long, note: String): Long = db.withTransaction {
        val id = dao.insert(DebtEntity(personId = person(name), direction = DebtDirection.I_OWE, amount = amount, note = note, createdAt = at))
        db.transactions().insert(tx(TxType.DEBT_IN, amount, accountId, at, id, note))
        id
    }

    /** Someone paid for something of the user's. It becomes spending in [categoryId] when the user pays them back. */
    suspend fun paidForMe(name: String, amount: Long, categoryId: Long, at: Long, note: String): Long = db.withTransaction {
        dao.insert(DebtEntity(personId = person(name), direction = DebtDirection.I_OWE, amount = amount,
            categoryId = categoryId, note = note, createdAt = at))
    }

    /** Records a (partial) repayment into or out of [accountId]. */
    suspend fun repay(debt: DebtRow, amount: Long, accountId: Long, at: Long) = db.withTransaction {
        require(amount > 0)
        val type = when (debt.direction) {
            DebtDirection.OWED_TO_ME -> if (debt.categoryId != null) TxType.REFUND else TxType.DEBT_IN
            DebtDirection.I_OWE -> if (debt.categoryId != null) TxType.EXPENSE else TxType.DEBT_OUT
        }
        val note = if (debt.direction == DebtDirection.OWED_TO_ME) "${debt.personName} paid back" else "Paid ${debt.personName} back"
        db.transactions().insert(tx(type, amount, accountId, at, debt.id, note, debt.categoryId))
    }

    /** Deletes the debt and every record it created (lending, borrowing, repayments). A split bill itself stays. */
    suspend fun delete(debtId: Long) = db.withTransaction {
        dao.deleteTransactions(debtId)
        dao.delete(debtId)
        dao.deleteUnusedPeople()
    }
}
