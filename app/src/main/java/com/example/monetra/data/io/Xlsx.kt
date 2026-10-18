package com.example.monetra.data.io

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A small .xlsx reader and writer: one sheet of text and number cells, no formulas or formatting beyond a
 * bold header. An .xlsx file is zipped XML, so this avoids a spreadsheet library (Apache POI is far too heavy for a phone).
 */
object Xlsx {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Writes [rows] (header first) as one sheet. Cells in [numericColumns] that parse as numbers are stored as numbers. */
    fun write(out: OutputStream, rows: List<List<String>>, numericColumns: Set<Int> = emptySet(), widths: List<Int> = emptyList()) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", WORKBOOK)
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry("xl/styles.xml", STYLES)
            entry("xl/worksheets/sheet1.xml", sheet(rows, numericColumns, widths))
        }
    }

    private fun sheet(rows: List<List<String>>, numericColumns: Set<Int>, widths: List<Int>): String {
        val sb = StringBuilder(XML_HEAD)
        sb.append("<worksheet xmlns=\"$NS\"><sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        if (widths.isNotEmpty()) {
            sb.append("<cols>")
            widths.forEachIndexed { i, w -> sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                val ref = columnName(c) + (r + 1)
                val number = if (r > 0 && c in numericColumns) value.toBigDecimalOrNull() else null
                when {
                    value.isEmpty() -> Unit
                    number != null -> sb.append("<c r=\"$ref\" s=\"2\"><v>${number.toPlainString()}</v></c>")
                    else -> sb.append("<c r=\"$ref\" t=\"inlineStr\"${if (r == 0) " s=\"1\"" else ""}><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        return sb.append("</sheetData></worksheet>").toString()
    }

    /**
     * Reads the first sheet as (row number, cells) pairs, the same shape the CSV splitter gives, so rows share one parser.
     * Blank rows are dropped and short rows are padded with empty strings between cells, never shifted.
     */
    fun read(input: InputStream): List<Pair<Int, List<String>>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) files[e.name] = zip.readBytes()
            }
        }
        val sheetName = firstSheetPath(files)
            ?: throw IllegalArgumentException("This isn't an Excel (.xlsx) file.")
        val shared = files["xl/sharedStrings.xml"]?.let { readSharedStrings(it) }.orEmpty()
        return readSheet(files.getValue(sheetName), shared)
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }

    /** The first sheet listed in the workbook, falling back to the lowest-numbered worksheet file. */
    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val workbook = files["xl/workbook.xml"]
        val rels = files["xl/_rels/workbook.xml.rels"]
        if (workbook != null && rels != null) {
            var rid: String? = null
            parser(workbook).apply {
                while (next() != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && name == "sheet") {
                        rid = (0 until attributeCount).firstOrNull { getAttributeName(it) == "id" }?.let { getAttributeValue(it) }
                        break
                    }
                }
            }
            if (rid != null) parser(rels).apply {
                while (next() != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && name == "Relationship" && getAttributeValue(null, "Id") == rid) {
                        val target = getAttributeValue(null, "Target").removePrefix("/").removePrefix("xl/")
                        return "xl/$target".takeIf { it in files }
                    }
                }
            }
        }
        return files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = parser(bytes)
        var current: StringBuilder? = null
        var inText = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) current?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inText = false
                    "si" -> { out += current.toString(); current = null }
                }
            }
        }
        return out
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): List<Pair<Int, List<String>>> {
        val rows = mutableListOf<Pair<Int, List<String>>>()
        val p = parser(bytes)
        var rowNumber = 0
        var cells = mutableListOf<String>()
        var column = 0
        var type: String? = null
        var text: StringBuilder? = null
        var inValue = false
        var inInline = false
        fun setCell(value: String) {
            while (cells.size <= column) cells += ""
            cells[column] = value
        }
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        rowNumber = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNumber + 1)
                        cells = mutableListOf()
                        column = 0
                    }
                    "c" -> {
                        p.getAttributeValue(null, "r")?.let { column = columnIndex(it) }
                        type = p.getAttributeValue(null, "t")
                        text = StringBuilder()
                    }
                    "v" -> inValue = true
                    "is" -> inInline = true
                    "t" -> Unit
                }
                XmlPullParser.TEXT -> if (inValue || inInline) text?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inValue = false
                    "is" -> inInline = false
                    "c" -> {
                        val raw = text?.toString().orEmpty()
                        val value = when (type) {
                            "s" -> shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" }
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        if (value.isNotEmpty()) setCell(value)
                        column++
                        text = null
                    }
                    "row" -> if (cells.any { it.isNotBlank() }) rows += rowNumber to cells.toList()
                }
            }
        }
        return rows
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) { n--; sb.append('A' + n % 26); n /= 26 }
        return sb.reverse().toString()
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch !in 'A'..'Z') break; n = n * 26 + (ch - 'A' + 1) }
        return n - 1
    }

    /** XML 1.0 can't carry most control characters at all, so they are dropped rather than escaped. */
    private fun escape(s: String): String = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch < ' ' && ch != '\n' && ch != '\t' && ch != '\r' -> Unit
            else -> append(ch)
        }
    }

    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private const val CONTENT_TYPES = XML_HEAD +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

    private const val WORKBOOK = XML_HEAD +
        "<workbook xmlns=\"$NS\" xmlns:r=\"$REL_NS\"><sheets><sheet name=\"Records\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    private const val WORKBOOK_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/></Relationships>"

    // Style 0 plain, 1 bold header, 2 amount with thousands separators and two decimals.
    private const val STYLES = XML_HEAD +
        "<styleSheet xmlns=\"$NS\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "</cellXfs></styleSheet>"
}
