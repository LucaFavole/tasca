package app.tasca

import app.tasca.data.Account
import app.tasca.data.Budget
import app.tasca.data.CategoryKind
import app.tasca.data.Closing
import app.tasca.data.Defaults
import app.tasca.data.Holding
import app.tasca.data.Recurring
import app.tasca.data.Txn
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.domain.TradeSide
import app.tasca.io.AccountSheetRow
import app.tasca.io.Config
import app.tasca.io.LedgerSheets
import app.tasca.io.ParsedWorkbook
import app.tasca.io.SheetRow
import app.tasca.io.Xlsx
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerTest {
    private val ledger = inMemoryLedger()
    private val dao = ledger.dao

    @AfterTest fun close() = ledger.close()

    private suspend fun balances() = dao.balances().first().associate { it.name to it.balanceCents }

    private suspend fun expenseCategory(name: String) = dao.categories().first { it.kind == CategoryKind.EXPENSE && it.name == name }.id

    private val day = LocalDate(2026, 10, 9)

    private val rows = listOf(
        SheetRow(TxnKind.EXPENSE, day, "Main", null, "Alimentari", 1_000, "spesa"),
        SheetRow(TxnKind.INCOME, day, "Main", null, "Stipendio", 5_000, ""),
        SheetRow(TxnKind.TRANSFER, day, "Main", "Risparmi", null, 2_000, ""),
        SheetRow(TxnKind.EXPENSE, day, "Risparmi", null, "Svago", 300, ""),
    )

    @Test fun seedingHappensOnce() = runTest {
        ledger.ensureSeeded()
        ledger.ensureSeeded()
        assertEquals(listOf("Main" to "A1"), dao.accounts().map { it.name to it.code })
        assertEquals(Defaults.categories().size, dao.categories().size)
    }

    @Test fun importGivesBalancesAndAddsNothingTheSecondTime() = runTest {
        ledger.ensureSeeded()
        val book = LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(rows, "EUR", Config(accounts = listOf(AccountSheetRow("Main", 10_000, false)))))))
        val first = ledger.import(book)
        assertEquals(4, first.added)
        assertEquals(1, first.newAccounts)
        assertEquals(mapOf("Main" to 12_000L, "Risparmi" to 1_700L), balances())
        val second = ledger.import(book)
        assertEquals(0, second.added)
        assertEquals(4, second.skipped)
        assertEquals(mapOf("Main" to 12_000L, "Risparmi" to 1_700L), balances())
    }

    @Test fun aFailingImportLeavesNothingBehind() = runTest {
        ledger.ensureSeeded()
        val broken = ParsedWorkbook(listOf(SheetRow(TxnKind.EXPENSE, day, "Nuovo conto", null, "Nuova categoria", 0)), emptyList())
        assertFailsWith<IllegalArgumentException> { ledger.import(broken) }
        assertEquals(listOf("Main"), dao.accounts().map { it.name })
        assertFalse(dao.categories().any { it.name == "Nuova categoria" })
    }

    @Test fun duePaymentsArePostedOnce() = runTest {
        ledger.ensureSeeded()
        val main = dao.accounts().single().id
        dao.upsertRecurring(Recurring(
            name = "Affitto", kind = TxnKind.EXPENSE, amountCents = 1_000, accountId = main, categoryId = expenseCategory("Casa"),
            frequency = Frequency.MONTHLY, startDate = LocalDate(2026, 8, 15),
        ))
        assertEquals(3, ledger.postDueRecurring(LocalDate(2026, 10, 15)))
        assertEquals(0, ledger.postDueRecurring(LocalDate(2026, 10, 15)))
        assertEquals(3, dao.allRecurring().single().posted)
        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 9, 15), LocalDate(2026, 8, 15)), dao.allTxns().map { it.date }.sortedDescending())
    }

    @Test fun deletingAnAccountInUseHidesItAndPausesItsPayments() = runTest {
        ledger.ensureSeeded()
        val unused = ledger.saveAccount(Account(name = "Vuoto", color = 0))
        val used = ledger.saveAccount(Account(name = "Usato", color = 0))
        assertEquals(listOf("A1", "A2", "A3"), dao.accounts().map { it.code })
        dao.upsertRecurring(Recurring(
            name = "Palestra", kind = TxnKind.EXPENSE, amountCents = 3_000, accountId = used, categoryId = expenseCategory("Attività fisica"),
            frequency = Frequency.MONTHLY, startDate = LocalDate(2026, 1, 1),
        ))
        ledger.postDueRecurring(LocalDate(2026, 1, 1))
        ledger.deleteAccount(unused)
        ledger.deleteAccount(used)
        assertEquals(listOf("Main" to false, "Usato" to true), dao.accounts().map { it.name to it.deleted })
        assertFalse(dao.allRecurring().single().active)
    }

    private suspend fun total() = dao.balances().first().sumOf { it.balanceCents }

    private suspend fun tripInDebt(): Pair<Long, Long> {
        ledger.ensureSeeded()
        val main = dao.accounts().single().id
        val trip = ledger.saveAccount(Account(name = "Viaggio", color = 0))
        dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = 45_000, date = day, accountId = trip, categoryId = expenseCategory("Viaggi")))
        return main to trip
    }

    @Test fun closingAnAccountInDebtPaysItFromAnotherAndKeepsTheTotal() = runTest {
        val (main, trip) = tripInDebt()
        val before = total()
        ledger.deleteAccount(trip, Closing.Settle(main), day)
        assertEquals(mapOf("Main" to -45_000L, "Viaggio" to 0L), balances())
        assertEquals(before, total())
        assertTrue(dao.accounts().first { it.id == trip }.deleted)
        val paid = dao.allTxns().single { it.kind == TxnKind.TRANSFER }
        assertEquals(listOf(main, trip, 45_000L), listOf(paid.accountId, paid.toAccountId, paid.amountCents))
    }

    @Test fun closingAnAccountWithMoneyLeftMovesItToAnother() = runTest {
        ledger.ensureSeeded()
        val main = dao.accounts().single().id
        val jar = ledger.saveAccount(Account(name = "Salvadanaio", color = 0, initialCents = 3_000))
        ledger.deleteAccount(jar, Closing.Settle(main), day)
        assertEquals(mapOf("Main" to 3_000L, "Salvadanaio" to 0L), balances())
        assertEquals(jar, dao.allTxns().single().accountId)
    }

    @Test fun aBalanceLeftByAnEarlierDeleteCanBeWrittenOff() = runTest {
        val (_, trip) = tripInDebt()
        ledger.deleteAccount(trip)
        assertEquals(-45_000L, total(), "an account deleted with no closing keeps its balance in the Total")
        ledger.deleteAccount(trip, Closing.WriteOff, day)
        assertEquals(0L, total())
        assertEquals(mapOf("Main" to 0L, "Viaggio" to 0L), balances())
        assertTrue(dao.accounts().first { it.id == trip }.deleted)
        assertEquals(listOf(45_000L), dao.allTxns().map { it.amountCents }, "its expense stays in the history")
    }

    @Test fun mergingMovesEverythingAndDropsTheTransfersBetweenTheTwo() = runTest {
        val (main, trip) = tripInDebt()
        val card = ledger.saveAccount(Account(name = "Carta", color = 0))
        dao.upsertAccount(dao.accounts().first { it.id == trip }.copy(initialCents = 10_000))
        val salary = dao.categories().first { it.kind == CategoryKind.INCOME && it.name == "Stipendio" }.id
        dao.upsertTxn(Txn(kind = TxnKind.INCOME, amountCents = 5_000, date = day, accountId = trip, categoryId = salary))
        dao.upsertTxn(Txn(kind = TxnKind.TRANSFER, amountCents = 8_000, date = day, accountId = main, toAccountId = trip))
        dao.upsertTxn(Txn(kind = TxnKind.TRANSFER, amountCents = 2_000, date = day, accountId = trip, toAccountId = card))
        dao.upsertRecurring(Recurring(
            name = "Hotel", kind = TxnKind.EXPENSE, amountCents = 1_000, accountId = trip, categoryId = expenseCategory("Viaggi"),
            frequency = Frequency.MONTHLY, startDate = LocalDate(2027, 1, 1),
        ))
        dao.upsertRecurring(Recurring(
            name = "Ricarica", kind = TxnKind.TRANSFER, amountCents = 1_000, accountId = main, toAccountId = trip,
            frequency = Frequency.MONTHLY, startDate = LocalDate(2027, 1, 1),
        ))
        dao.upsertHolding(Holding(accountId = trip, symbol = "SPYY.DE", name = "SPDR ACWI", quantity = 1.0, investedCents = 1_000))
        val before = balances()
        val total = total()

        ledger.deleteAccount(trip, Closing.MergeInto(main), day)

        assertEquals(mapOf("Main" to before.getValue("Main") + before.getValue("Viaggio"), "Carta" to 2_000L), balances())
        assertEquals(total, total())
        assertEquals(listOf("Main", "Carta"), dao.accounts().map { it.name }, "the merged account goes")
        assertEquals(
            setOf(Triple(TxnKind.EXPENSE, main, null), Triple(TxnKind.INCOME, main, null), Triple(TxnKind.TRANSFER, main, card)),
            dao.allTxns().map { Triple(it.kind, it.accountId, it.toAccountId) }.toSet(),
        )
        assertEquals(listOf("Hotel" to main), dao.allRecurring().map { it.name to it.accountId })
        assertTrue(dao.allRecurring().single().active)
        assertEquals(main, dao.holdings().single().accountId)
    }

    @Test fun aSaleBooksFeeTaxAndGain() = runTest {
        ledger.ensureSeeded()
        val main = dao.accounts().single().id
        dao.upsertHolding(Holding(accountId = main, symbol = "SPYY.DE", name = "SPDR ACWI", quantity = 10.0, investedCents = 150_000))
        val holding = dao.holdings().single().id
        ledger.trade(holding, TradeSide.SELL, 4.0, 180.0, 100, 312, day)
        assertEquals(6.0, dao.holding(holding)!!.quantity, 1e-9)
        val booked = dao.allTxns().map { Triple(it.kind, it.amountCents, it.note) }.toSet()
        assertEquals(
            setOf(
                Triple(TxnKind.EXPENSE, 100L, "Sell SPDR ACWI"),
                Triple(TxnKind.EXPENSE, 312L, "Tax on SPDR ACWI"),
                Triple(TxnKind.INCOME, 12_000L, "Sold SPDR ACWI"),
            ),
            booked,
        )
        assertTrue(dao.categories().map { it.name }.containsAll(listOf("Commissioni", "Tasse", "Plusvalenze")))
    }

    @Test fun aBackupRestoresIntoAnEmptyLedger() = runTest {
        ledger.ensureSeeded()
        ledger.import(LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(rows, "EUR")))))
        val main = dao.accounts().first { it.name == "Main" }.id
        dao.upsertRecurring(Recurring(
            name = "Affitto", kind = TxnKind.EXPENSE, amountCents = 80_000, accountId = main, categoryId = expenseCategory("Casa"),
            frequency = Frequency.MONTHLY, startDate = LocalDate(2026, 1, 25), posted = 9,
        ))
        dao.upsertBudget(Budget(expenseCategory("Svago"), 20_000))
        val backup = Xlsx.write(ledger.exportBook("EUR", mapOf("currency" to "EUR")))

        val restored = inMemoryLedger()
        try {
            restored.ensureSeeded()
            val result = restored.import(LedgerSheets.read(Xlsx.read(backup)))
            assertTrue(result.fresh)
            assertEquals(balances(), restored.dao.balances().first().associate { it.name to it.balanceCents })
            assertEquals(listOf("Affitto" to 9), restored.dao.allRecurring().map { it.name to it.posted })
            assertEquals(listOf(20_000L), restored.dao.budgets().map { it.monthlyCents })
        } finally {
            restored.close()
        }
    }

    @Test fun erasingLeavesAnEmptyLedgerThatSeedsAgain() = runTest {
        ledger.ensureSeeded()
        ledger.import(LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(rows, "EUR")))))
        ledger.eraseAll()
        assertEquals(emptyList(), dao.allTxns())
        assertEquals(emptyList(), dao.accounts())
        ledger.ensureSeeded()
        assertEquals(listOf("Main"), dao.accounts().map { it.name })
    }
}
