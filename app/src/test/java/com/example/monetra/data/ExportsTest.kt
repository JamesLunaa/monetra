package com.example.monetra.data

import com.example.monetra.core.money.Money
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.CategoryType
import com.example.monetra.data.db.TransactionEntity
import com.example.monetra.data.db.TxType
import com.example.monetra.data.io.Backup
import com.example.monetra.data.io.CsvWriter
import com.example.monetra.data.io.RecordsCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.ZoneId

class ExportsTest {
    private val zone = ZoneId.of("Asia/Manila")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 8, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun tx(type: TxType, amount: Long, at: Long, acc: Long, to: Long? = null, cat: Long? = null, note: String = "") =
        TransactionEntity(type = type, amount = amount, accountId = acc, toAccountId = to, categoryId = cat, occurredAt = at, note = note, createdAt = 0, updatedAt = 0)

    @Test fun csvRoundTripsThroughTheImporterParser() {
        val txs = listOf(
            tx(TxType.EXPENSE, 12050, at(1, 9, 5), 1, cat = 10, note = "bus, \"exact\" fare"),
            tx(TxType.INCOME, 1500000, at(15, 18, 30), 1, cat = 20),
            tx(TxType.TRANSFER, 200000, at(16, 7, 0), 1, to = 2),
        )
        val out = StringWriter()
        CsvWriter.write(out, txs, mapOf(1L to "Cash", 2L to "Bank"), mapOf(10L to "Transportation", 20L to "Salary"), zone)
        val r = RecordsCsv.parse(out.toString().reader())
        assertEquals(emptyList<String>(), r.errors)
        assertEquals(3, r.rows.size)
        with(r.rows[0]) {
            assertEquals(LocalDateTime.of(2026, 8, 1, 9, 5), time)
            assertEquals(Money(12050), amount)
            assertEquals("Transportation", category)
            assertEquals("bus, \"exact\" fare", note)
            assertNull(toAccount)
        }
        with(r.rows[2]) {
            assertEquals("Cash", account)
            assertEquals("Bank", toAccount)
            assertEquals(Money(200000), amount)
        }
    }

    @Test fun sampleFileImportsCleanly() {
        val r = RecordsCsv.parse(com.example.monetra.ui.io.sampleCsv().reader())
        assertEquals(emptyList<String>(), r.errors)
        assertEquals(3, r.rows.size)
        assertEquals("Bank", r.rows[2].account)
        assertEquals("Cash", r.rows[2].toAccount)
    }

    @Test fun backupJsonRoundTrips() {
        val b = Backup(
            createdAt = 1L,
            accounts = listOf(AccountEntity(id = 1, name = "Cash", icon = "cash", colorIndex = 4, initialBalance = 100)),
            categories = listOf(CategoryEntity(id = 10, name = "Food", type = CategoryType.EXPENSE, icon = "food", colorIndex = 0)),
            transactions = listOf(tx(TxType.EXPENSE, 5000, 123L, 1, cat = 10, note = "lunch").copy(id = 7)),
            budgets = emptyList(),
        )
        val text = Backup.json.encodeToString(Backup.serializer(), b)
        assertEquals(b, Backup.json.decodeFromString(Backup.serializer(), text))
    }
}
