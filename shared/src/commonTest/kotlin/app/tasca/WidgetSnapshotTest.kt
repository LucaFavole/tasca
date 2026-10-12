package app.tasca

import app.tasca.data.Account
import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.Holding
import app.tasca.data.Quote
import app.tasca.data.Txn
import app.tasca.data.TxnKind
import app.tasca.domain.Money
import app.tasca.domain.today
import app.tasca.ui.DefaultAccounts
import app.tasca.ui.widgetSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** widget.json is the contract with the Swift widgets in iosApp/TascaWidgets. */
class WidgetSnapshotTest {
    private val ledger = inMemoryLedger()
    private val updated = Instant.parse("2026-10-14T21:00:00Z")

    @AfterTest fun close() = ledger.close()

    private suspend fun snapshot(defaults: DefaultAccounts) = Json.parseToJsonElement(widgetSnapshot(ledger, defaults, updated)).jsonObject

    private fun amount(cents: Long) = buildJsonObject {
        put("amount", Money.format(cents))
        put("amountCents", cents)
    }

    private fun account(id: Long, name: String, cents: Long) = JsonObject(mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive(name)) + amount(cents))

    @Test fun listsTheAccountsATotalWithDeletedOnesTheNetWorthAndTheDefault() = runTest {
        val main = ledger.saveAccount(Account(name = "Main", color = 0, initialCents = 10_000))
        val card = ledger.saveAccount(Account(name = "Carta", color = 0, initialCents = -1_250))
        ledger.saveAccount(Account(name = "Old", color = 0, initialCents = 2_500, deleted = true))
        ledger.dao.upsertHolding(Holding(accountId = main, symbol = "VWRA.L", name = "All-World", quantity = 10.0, investedCents = 150_000))
        ledger.dao.upsertQuotes(listOf(Quote("VWRA.L", 193.5, 192.5, "USD", 0.9, 0)))

        val json = snapshot(DefaultAccounts(expense = card))

        assertEquals(listOf("defaultId", "accounts", "total", "netWorth", "recent", "updated"), json.keys.toList())
        assertEquals(JsonPrimitive(card), json["defaultId"])
        assertEquals(buildJsonArray { add(account(main, "Main", 10_000)); add(account(card, "Carta", -1_250)) }, json["accounts"])
        assertEquals(amount(11_250), json["total"])
        assertEquals(amount(11_250 + 24_150), json["netWorth"])
        assertEquals(JsonPrimitive("2026-10-14T21:00:00Z"), json["updated"])
    }

    @Test fun listsTheSixLatestTransactionsWithTheRowTexts() = runTest {
        val main = ledger.saveAccount(Account(name = "Main", color = 0))
        val food = ledger.dao.upsertCategory(Category(name = "Cibo", kind = CategoryKind.EXPENSE, color = 0, icon = "cart"))
        val ids = (1..7).map { n ->
            ledger.dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = n * 100L, date = today(), accountId = main, categoryId = food, note = "n$n"))
        }

        val recent = snapshot(DefaultAccounts())["recent"]!!.jsonArray

        assertEquals(ids.reversed().take(6), recent.map { it.jsonObject["id"]!!.jsonPrimitive.long })
        assertEquals(
            buildJsonObject {
                put("id", ids.last())
                put("name", "n7")
                put("detail", "Main · Today")
                put("amount", "−" + Money.format(700))
                put("kind", "EXPENSE")
            },
            recent.first(),
        )
    }

    @Test fun withoutAccountsThereIsNoDefault() = runTest {
        val json = snapshot(DefaultAccounts())
        assertEquals(JsonNull, json["defaultId"])
        assertEquals(amount(0), json["total"])
    }
}
