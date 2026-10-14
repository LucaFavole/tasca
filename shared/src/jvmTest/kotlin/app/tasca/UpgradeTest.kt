package app.tasca

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.tasca.data.AppDb
import app.tasca.data.Ledger
import app.tasca.domain.Frequency
import app.tasca.io.LedgerSheets
import app.tasca.io.Xlsx
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone already holds a version 4 tasca.db written by Room 2.6 on Android's own SQLite.
 * These tests build such files from the exported schemas and open them the way the app now does.
 */
class UpgradeTest {
    private val dir = File.createTempFile("tasca", "").let { it.delete(); it.mkdirs(); it }

    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    private fun schema(version: Int) = Json.parseToJsonElement(File("schemas/app.tasca.data.AppDb/$version.json").readText()).jsonObject.getValue("database").jsonObject

    /**
     * A database file as Room left it at [version]: its tables, Room's identity hash and Android's own metadata table
     * in the main file, and the rows [fill] writes still in the WAL file, as when the update kills the app.
     */
    private fun oldDatabase(version: Int, fill: SQLiteConnection.() -> Unit): File {
        val written = File(dir, "old/tasca.db").apply { parentFile.mkdirs() }
        val schema = schema(version)
        val connection = BundledSQLiteDriver().open(written.path)
        connection.execSQL("PRAGMA journal_mode = WAL")
        for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
            val table = entity.getValue("tableName").jsonPrimitive.content
            connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            for (index in entity["indices"]?.jsonArray.orEmpty()) {
                connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        for (query in schema.getValue("setupQueries").jsonArray) connection.execSQL(query.jsonPrimitive.content)
        connection.execSQL("CREATE TABLE android_metadata (locale TEXT)")
        connection.execSQL("INSERT INTO android_metadata VALUES ('it_IT')")
        connection.execSQL("PRAGMA user_version = $version")
        connection.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        connection.fill()
        val file = File(dir, "tasca.db")
        for (suffix in listOf("", "-wal")) File(written.path + suffix).copyTo(File(file.path + suffix))
        connection.close()
        check(File(file.path + "-wal").length() > 0) { "the copied WAL file holds the rows" }
        return file
    }

    private fun SQLiteConnection.single(sql: String): String = prepare(sql).use { it.step(); it.getText(0) }

    @Test fun aVersion4DatabaseOpensWithItsDataAndNoMigration() = runTest {
        val file = oldDatabase(4) {
            execSQL("INSERT INTO account (id, name, color, icon, initialCents, deleted, code) VALUES (1, 'Main', 0, 'wallet', 10000, 0, 'A1'), (2, 'Risparmi', 0, 'savings', 0, 0, 'A2')")
            execSQL("INSERT INTO category (id, name, kind, color, icon) VALUES (1, 'Casa', 'EXPENSE', 0, 'home'), (2, 'Stipendio', 'INCOME', 0, 'work')")
            execSQL("INSERT INTO txn (kind, amountCents, date, accountId, toAccountId, categoryId, note, recurringId, createdAt) VALUES " +
                "('EXPENSE', 80000, ${LocalDate(2026, 9, 25).toEpochDays()}, 1, NULL, 1, 'Affitto', 1, 0), " +
                "('INCOME', 250000, ${LocalDate(2026, 9, 27).toEpochDays()}, 1, NULL, 2, '', NULL, 0), " +
                "('TRANSFER', 50000, ${LocalDate(2026, 9, 28).toEpochDays()}, 1, 2, NULL, '', NULL, 0)")
            execSQL("INSERT INTO recurring (id, name, kind, amountCents, accountId, toAccountId, categoryId, frequency, every, startDate, endDate, posted, active) " +
                "VALUES (1, 'Affitto', 'EXPENSE', 80000, 1, NULL, 1, 'MONTHLY', 1, ${LocalDate(2026, 1, 25).toEpochDays()}, NULL, 9, 1)")
            execSQL("INSERT INTO budget (categoryId, monthlyCents) VALUES (1, 90000)")
            execSQL("INSERT INTO holding (id, accountId, symbol, name, quantity, investedCents) VALUES (1, 2, 'SPYY.DE', 'SPDR ACWI', 12.345678, 333333)")
            execSQL("INSERT INTO quote (symbol, price, previousClose, currency, rateToApp, fetchedAt) VALUES ('SPYY.DE', 250.5, NULL, 'EUR', 1.0, 0)")
        }
        val ledger = Ledger.open(Room.databaseBuilder<AppDb>(name = file.path))
        try {
            assertEquals(listOf("Main" to 130_000L, "Risparmi" to 50_000L), ledger.dao.balances().first().map { it.name to it.balanceCents })
            val rent = ledger.dao.allRecurring().single()
            assertEquals(Frequency.MONTHLY to LocalDate(2026, 10, 25), rent.frequency to rent.nextDate)
            assertEquals(LocalDate(2026, 9, 25), ledger.dao.allTxns().first { it.note == "Affitto" }.date)
            assertEquals(12.345678, ledger.dao.holdings().single().quantity, 0.0)
            assertEquals(0, ledger.postDueRecurring(LocalDate(2026, 10, 24)))
            val exported = LedgerSheets.read(Xlsx.read(Xlsx.write(ledger.exportBook("EUR"))))
            assertEquals(3, exported.rows.size)
            assertEquals(listOf(90_000L), exported.config.budgets.map { it.monthlyCents })
        } finally {
            ledger.close()
        }
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            assertEquals("4", connection.single("PRAGMA user_version"))
            assertEquals(schema(4).getValue("identityHash").jsonPrimitive.content, connection.single("SELECT identity_hash FROM room_master_table WHERE id = 42"))
            assertEquals("it_IT", connection.single("SELECT locale FROM android_metadata"))
        } finally {
            connection.close()
        }
    }

    @Test fun aVersion3DatabaseGetsAccountCodes() = runTest {
        val file = oldDatabase(3) {
            execSQL("INSERT INTO account (id, name, color, icon, initialCents, deleted) VALUES (1, 'Main', 0, 'wallet', 0, 0), (7, 'Risparmi', 0, 'savings', 0, 1)")
        }
        val ledger = Ledger.open(Room.databaseBuilder<AppDb>(name = file.path))
        try {
            assertEquals(listOf("A1", "A7"), ledger.dao.accounts().sortedBy { it.id }.map { it.code })
        } finally {
            ledger.close()
        }
    }
}
