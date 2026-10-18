package com.example.monetra

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.CategoryType
import com.example.monetra.data.db.MonetraDatabase
import com.example.monetra.data.db.TransactionEntity
import com.example.monetra.data.db.TxType
import com.example.monetra.data.repo.DebtRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Money rules for debts, against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class DebtsDbTest {
    private lateinit var db: MonetraDatabase
    private lateinit var debts: DebtRepo
    private var cash = 0L
    private var food = 0L

    @Before fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MonetraDatabase::class.java).build()
        debts = DebtRepo(db)
        cash = db.accounts().insert(AccountEntity(name = "Cash", icon = "cash", colorIndex = 0, initialBalance = 1000_00))
        food = db.categories().insert(CategoryEntity(name = "Food", type = CategoryType.EXPENSE, icon = "food", colorIndex = 0))
    }

    @After fun tearDown() = db.close()

    private suspend fun balance() = db.accounts().observeBalances().first().single { it.id == cash }.balance
    private suspend fun spent() = db.transactions().observeTotals(Long.MIN_VALUE, Long.MAX_VALUE).first().expense
    private suspend fun foodSpent() = db.transactions().observeCategoryTotals(TxType.EXPENSE, Long.MIN_VALUE, Long.MAX_VALUE).first()
        .firstOrNull { it.categoryId == food }?.total ?: 0L
    private suspend fun debt() = db.debts().observeDebts().first().single()

    @Test fun splitBillRepaymentLowersSpending(): Unit = runBlocking {
        val id = db.transactions().insert(TransactionEntity(type = TxType.EXPENSE, amount = 450_00, accountId = cash, categoryId = food,
            occurredAt = 1, createdAt = 0, updatedAt = 0))
        debts.split(db.transactions().get(id)!!, "Ate", 300_00, "")
        assertEquals(450_00L, foodSpent())
        assertEquals(300_00L, debt().remaining)

        debts.repay(debt(), 100_00, cash, 2)
        assertEquals(350_00L, foodSpent())
        assertEquals(350_00L, spent())
        assertEquals(200_00L, debt().remaining)
        assertEquals(1000_00L - 450_00 + 100_00, balance())

        debts.repay(debt(), 200_00, cash, 3)
        assertTrue(debt().settled)
        assertEquals(150_00L, foodSpent())
    }

    @Test fun lentCashMovesBalanceNotSpending(): Unit = runBlocking {
        debts.lend("Mama", 500_00, cash, 1, "")
        assertEquals(500_00L, balance())
        assertEquals(0L, spent())
        debts.repay(debt(), 500_00, cash, 2)
        assertEquals(1000_00L, balance())
        assertEquals(0L, spent())
        assertTrue(debt().settled)
    }

    @Test fun paidForMeBecomesSpendingWhenRepaid(): Unit = runBlocking {
        debts.paidForMe("Ben", 200_00, food, 1, "")
        assertEquals(0L, foodSpent())
        assertEquals(1000_00L, balance())
        debts.repay(debt(), 200_00, cash, 2)
        assertEquals(200_00L, foodSpent())
        assertEquals(800_00L, balance())
    }

    @Test fun deletingDebtRemovesItsRecordsAndPerson(): Unit = runBlocking {
        debts.borrow("Kuya", 300_00, cash, 1, "")
        assertEquals(1300_00L, balance())
        debts.delete(debt().id)
        assertEquals(1000_00L, balance())
        assertEquals(0, db.debts().observePeople().first().size)
    }
}
