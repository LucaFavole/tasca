package app.tasca.io

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.floor

sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Num(val value: Double) : Cell
    data class Date(val value: LocalDate) : Cell
    data object Empty : Cell
}

data class Sheet(val name: String, val rows: List<List<Cell>>)

/** Minimal .xlsx support: the cell values of every sheet, no formulas or formatting beyond dates. */
object Xlsx {
    private val EXCEL_EPOCH: LocalDate = LocalDate(1899, 12, 30)

    fun serialToDate(serial: Double): LocalDate = EXCEL_EPOCH.plus(floor(serial).toLong(), DateTimeUnit.DAY)
    fun dateToSerial(date: LocalDate): Long = EXCEL_EPOCH.daysUntil(date).toLong()

    fun read(bytes: ByteArray): List<Sheet> {
        val files = HashMap<String, ByteArray>()
        for ((name, content) in unzip(bytes) { it.endsWith(".xml") || it.endsWith(".rels") }) files[name.trimStart('/')] = content
        val workbook = files["xl/workbook.xml"] ?: error("Not an Excel .xlsx file")
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val targets = HashMap<String?, String?>()
        files["xl/_rels/workbook.xml.rels"]?.let { rels ->
            parse(rels) { name, a -> if (name == "Relationship") targets[a["Id"]] = a["Target"] }
        }
        val sheets = mutableListOf<Pair<String?, String>>()
        parse(workbook) { name, a ->
            if (name == "sheet") {
                val rid = a.entries.firstOrNull { it.key.endsWith(":id") }?.value
                val target = targets[rid] ?: "worksheets/sheet${sheets.size + 1}.xml"
                sheets += a["name"] to target.removePrefix("/xl/").removePrefix("/")
            }
        }
        return sheets.mapNotNull { (name, target) ->
            val path = if (target.startsWith("xl/")) target else "xl/$target"
            files[path]?.let { Sheet(name!!, sheetRows(it, shared)) }
        }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inT = false
        parseXml(bytes, object : XmlHandler {
            override fun start(name: String, attributes: Map<String, String>) {
                when (name.substringAfter(':')) { "si" -> sb.clear(); "t" -> inT = true }
            }
            override fun end(name: String) {
                when (name.substringAfter(':')) { "si" -> out += sb.toString(); "t" -> inT = false }
            }
            override fun text(chars: CharArray, start: Int, length: Int) { if (inT) sb.appendRange(chars, start, start + length) }
        })
        return out
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<Cell>> {
        val rows = mutableListOf<List<Cell>>()
        var row = HashMap<Int, Cell>()
        var col = 0
        var type = ""
        val text = StringBuilder()
        var capture = false
        parseXml(bytes, object : XmlHandler {
            override fun start(name: String, attributes: Map<String, String>) {
                when (name.substringAfter(':')) {
                    "row" -> row = HashMap()
                    "c" -> {
                        col = attributes["r"]?.let(::columnIndex) ?: (row.keys.maxOrNull()?.plus(1) ?: 0)
                        type = attributes["t"] ?: "n"
                        text.clear()
                    }
                    "v", "t" -> capture = true
                }
            }
            override fun end(name: String) {
                when (name.substringAfter(':')) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = text.toString()
                        row[col] = when {
                            raw.isEmpty() -> Cell.Empty
                            type == "s" -> shared.getOrNull(raw.toInt())?.let { Cell.Text(it) } ?: Cell.Empty
                            type == "inlineStr" || type == "str" -> Cell.Text(raw)
                            type == "b" -> Cell.Text(if (raw == "1") "TRUE" else "FALSE")
                            else -> raw.toDoubleOrNull()?.let { Cell.Num(it) } ?: Cell.Text(raw)
                        }
                    }
                    "row" -> {
                        val width = (row.keys.maxOrNull() ?: -1) + 1
                        rows += List(width) { row[it] ?: Cell.Empty }
                    }
                }
            }
            override fun text(chars: CharArray, start: Int, length: Int) { if (capture) text.appendRange(chars, start, start + length) }
        })
        return rows
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (!c.isLetter()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }
        return n - 1
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun parse(bytes: ByteArray, onStart: (String, Map<String, String>) -> Unit) {
        parseXml(bytes, object : XmlHandler {
            override fun start(name: String, attributes: Map<String, String>) = onStart(name.substringAfter(':'), attributes)
        })
    }

    fun write(sheets: List<Sheet>): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        fun put(name: String, content: String) {
            entries += name to content.encodeToByteArray()
        }
        val ns = "http://schemas.openxmlformats.org"
        put(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="$ns/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
                "</Types>",
        )
        put(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$ns/package/2006/relationships"><Relationship Id="rId1" Type="$ns/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        )
        put(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="$ns/spreadsheetml/2006/main" xmlns:r="$ns/officeDocument/2006/relationships"><sheets>""" +
                sheets.withIndex().joinToString("") { (i, s) -> """<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" } +
                "</sheets></workbook>",
        )
        put(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$ns/package/2006/relationships">""" +
                sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="$ns/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${sheets.size + 1}" Type="$ns/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
        )
        put(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="$ns/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
        )
        sheets.forEachIndexed { i, sheet ->
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="$ns/spreadsheetml/2006/main"><cols><col min="1" max="1" width="12" customWidth="1"/><col min="2" max="12" width="18" customWidth="1"/></cols><sheetData>""")
            sheet.rows.forEachIndexed { r, cells ->
                sb.append("""<row r="${r + 1}">""")
                val header = r < 2
                cells.forEachIndexed { c, cell ->
                    val ref = columnName(c) + (r + 1)
                    when (cell) {
                        is Cell.Text -> sb.append("""<c r="$ref" t="inlineStr"${if (header) " s=\"3\"" else ""}><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                        is Cell.Num -> sb.append("""<c r="$ref" s="2"><v>${cell.value}</v></c>""")
                        is Cell.Date -> sb.append("""<c r="$ref" s="1"><v>${dateToSerial(cell.value)}</v></c>""")
                        Cell.Empty -> {}
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            put("xl/worksheets/sheet${i + 1}.xml", sb.toString())
        }
        return zip(entries)
    }

    private fun esc(s: String) = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;")
            else -> if (ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r') Unit else append(ch)
        }
    }
}

/** Receives the events of [parseXml], with element and attribute names as written, prefixes included. */
internal interface XmlHandler {
    fun start(name: String, attributes: Map<String, String>) {}
    fun end(name: String) {}
    fun text(chars: CharArray, start: Int, length: Int) {}
}

/** Streams [bytes] as XML through [handler], entities resolved, namespaces not processed. */
internal expect fun parseXml(bytes: ByteArray, handler: XmlHandler)

/** The entries of a ZIP archive whose names pass [wanted], in archive order. */
internal expect fun unzip(bytes: ByteArray, wanted: (String) -> Boolean): List<Pair<String, ByteArray>>

/** A ZIP archive of [entries] in order. */
internal expect fun zip(entries: List<Pair<String, ByteArray>>): ByteArray
