package app.tasca

import app.tasca.data.TxnKind
import app.tasca.io.LedgerSheets
import app.tasca.io.SheetRow
import app.tasca.io.Xlsx
import app.tasca.io.ZipArchive
import kotlinx.datetime.LocalDate
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The plain-Kotlin ZIP code iOS uses, checked against java.util.zip. */
class ZipArchiveTest {
    private val entries = listOf(
        "[Content_Types].xml" to "<Types/>".encodeToByteArray(),
        "xl/worksheets/sheet1.xml" to "àé€ ".repeat(5000).encodeToByteArray(),
        "empty.xml" to ByteArray(0),
        "image.bin" to Random(1).nextBytes(70_000),
    )

    private fun inflate(data: ByteArray, size: Int): ByteArray {
        val out = ByteArray(size)
        Inflater(true).run { setInput(data); inflate(out); end() }
        return out
    }

    @Test fun javaReadsWhatItWrites() {
        val read = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ZipArchive.write(entries).inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                read += e.name to z.readBytes()
            }
        }
        assertEquals(entries.map { it.first }, read.map { it.first })
        entries.zip(read).forEach { (a, b) -> assertContentEquals(a.second, b.second, a.first) }
    }

    @Test fun readsWhatJavaWritesCompressedOrStored() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((i, e) in entries.withIndex()) {
                val entry = ZipEntry(e.first)
                if (i % 2 == 1) {
                    entry.method = ZipEntry.STORED
                    entry.size = e.second.size.toLong()
                    entry.crc = CRC32().apply { update(e.second) }.value
                }
                zip.putNextEntry(entry)
                zip.write(e.second)
                zip.closeEntry()
            }
            zip.setComment("a trailing comment")
        }
        val read = ZipArchive.read(out.toByteArray(), { !it.endsWith(".bin") }, ::inflate)
        val expected = entries.filter { !it.first.endsWith(".bin") }
        assertEquals(expected.map { it.first }, read.map { it.first })
        expected.zip(read).forEach { (a, b) -> assertContentEquals(a.second, b.second, a.first) }
    }

    @Test fun crcMatchesJava() {
        for ((_, bytes) in entries) assertEquals(CRC32().apply { update(bytes) }.value.toInt(), ZipArchive.crc32(bytes))
    }

    @Test fun workbookRoundTripsThroughTheKotlinZip() {
        val rows = listOf(SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 9), "Main", null, "Caffè", 120, "bar & <co>"))
        val stored = ZipArchive.write(ZipArchive.read(Xlsx.write(LedgerSheets.write(rows, "EUR")), { true }, ::inflate))
        assertEquals(rows, LedgerSheets.read(Xlsx.read(stored)).rows)
    }
}
