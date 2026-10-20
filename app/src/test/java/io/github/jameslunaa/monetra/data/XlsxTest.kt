package io.github.jameslunaa.monetra.data

import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.data.io.RecordsCsv
import io.github.jameslunaa.monetra.data.io.Xlsx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxTest {
    private val header = listOf("TIME", "TYPE", "AMOUNT", "CATEGORY", "ACCOUNT", "NOTES")

    @Test fun writtenSheetReadsBackIdentically() {
        val rows = listOf(
            header,
            listOf("Oct 03, 2026 8:15 AM", "(-) Expense", "120.50", "Food", "Cash", "lunch & <coffee> \"large\""),
            listOf("Oct 05, 2026 6:00 PM", "(+) Income", "15000.00", "Salary", "Bank", ""),
            listOf("Oct 06, 2026 9:30 AM", "(*) Transfer", "2000.00", "", "Bank -> Cash", "ATM"),
        )
        val out = ByteArrayOutputStream()
        Xlsx.write(out, rows, numericColumns = setOf(2))
        val back = Xlsx.read(out.toByteArray().inputStream())
        assertEquals(listOf(1, 2, 3, 4), back.map { it.first })
        assertEquals(rows[0], back[0].second)
        assertEquals("lunch & <coffee> \"large\"", back[1].second[5])
        // Numbers come back as plain digits, and the empty trailing note is simply absent.
        assertEquals("120.50", back[1].second[2])
        assertEquals(Money(1_500_000), RecordsCsv.parseRecords(back).rows[1].amount)
        assertEquals(3, RecordsCsv.parseRecords(back).rows.size)
    }

    /** What Excel itself writes: shared strings, date serials, skipped empty cells and a different column order of rows. */
    @Test fun readsAnExcelStyleWorkbook() {
        val sheet = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c></row>
            <row r="3"><c r="A3"><v>46298.5</v></c><c r="B3" t="s"><v>6</v></c><c r="C3"><v>99.9</v></c><c r="D3" t="s"><v>7</v></c><c r="E3" t="s"><v>8</v></c></row>
            </sheetData></worksheet>"""
        val strings = listOf("TIME", "TYPE", "AMOUNT", "CATEGORY", "ACCOUNT", "NOTES", "(-) Expense", "Food", "Cash")
            .joinToString("", "<sst>", "</sst>") { "<si><t>$it</t></si>" }
        val bytes = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(strings.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val records = Xlsx.read(bytes.inputStream())
        assertEquals(listOf(1, 3), records.map { it.first })
        val parsed = RecordsCsv.parseRecords(records)
        assertEquals(emptyList<String>(), parsed.errors)
        with(parsed.rows.single()) {
            assertEquals(LocalDateTime.of(2026, 10, 3, 12, 0), time)
            assertEquals(Money(9990), amount)
            assertEquals("Food", category)
            assertEquals("Cash", account)
        }
    }

    @Test fun notAWorkbookIsRejected() {
        val bytes = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { it.putNextEntry(ZipEntry("a.txt")); it.closeEntry() } }
        val failed = runCatching { Xlsx.read(bytes.toByteArray().inputStream()) }.exceptionOrNull()
        assertTrue(failed is IllegalArgumentException)
    }

    @Test fun parsesSpreadsheetDateSerialsAndIsoDates() {
        assertEquals(LocalDateTime.of(2026, 10, 3, 12, 0), RecordsCsv.parseTime("46298.5"))
        assertEquals(LocalDateTime.of(2026, 10, 3, 0, 0), RecordsCsv.parseTime("2026-10-03"))
        assertEquals(LocalDateTime.of(2026, 10, 3, 8, 15), RecordsCsv.parseTime("2026-10-03 08:15"))
        assertEquals(null, RecordsCsv.parseTime("soon"))
    }
}
