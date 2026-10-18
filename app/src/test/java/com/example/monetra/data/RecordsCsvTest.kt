package com.example.monetra.data

import com.example.monetra.core.money.Money
import com.example.monetra.core.money.sum
import com.example.monetra.data.io.RecordsCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class RecordsCsvTest {
    private val sample = """
        "TIME","TYPE","AMOUNT","CATEGORY","ACCOUNT","NOTES" 
        "Jul 20, 2026 12:14 PM","(+) Income","1500.00","Income","Cash","" 
        "Aug 01, 2026 11:14 AM","(-) Expense","120.00","Social","Cash","lunch" 
        "Jul 22, 2026 11:30 PM","(-) Expense","450.00","Returnable","Cash","cake 3x, ""friend"" = paid " 
    """.trimIndent()

    @Test fun parsesRows() {
        val r = RecordsCsv.parse(sample.reader())
        assertEquals(emptyList<String>(), r.errors)
        assertEquals(3, r.rows.size)
        with(r.rows[0]) {
            assertEquals(LocalDateTime.of(2026, 7, 20, 12, 14), time)
            assertTrue(income)
            assertEquals(Money(150000), amount)
            assertEquals("Income", category)
            assertEquals("Cash", account)
        }
        assertEquals(LocalDateTime.of(2026, 8, 1, 11, 14), r.rows[1].time)
        assertEquals("cake 3x, \"friend\" = paid", r.rows[2].note)
    }

    @Test fun reportsBadLinesWithoutFailing() {
        val bad = sample + "\n\"Nope\",\"(-) Expense\",\"5\",\"Food\",\"Cash\",\"\"\n\"Aug 02, 2026 1:00 PM\",\"(*) Transfer\",\"5\",\"\",\"Cash\",\"\""
        val r = RecordsCsv.parse(bad.reader())
        assertEquals(3, r.rows.size)
        assertEquals(2, r.errors.size)
        assertTrue(r.errors[0].startsWith("Line 5"))
    }

    @Test fun rejectsOtherFiles() {
        val r = RecordsCsv.parse("date,amount\n2026-01-01,5".reader())
        assertTrue(r.rows.isEmpty())
        assertEquals(1, r.errors.size)
    }

    /** Parses a real local export (gitignored, never committed) without errors, when present. */
    @Test fun realExportParsesCleanly() {
        val file = listOf(File("../sample-data.csv"), File("sample-data.csv")).firstOrNull { it.exists() }
        assumeTrue(file != null)
        val r = RecordsCsv.parse(file!!.reader())
        assertEquals(emptyList<String>(), r.errors)
        assertTrue(r.rows.isNotEmpty())
        assertTrue(r.rows.map { it.amount }.sum() > Money.ZERO)
    }
}
