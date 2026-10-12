package app.tasca.ui

import app.tasca.data.Ledger
import app.tasca.data.TxnKind
import app.tasca.data.WidgetFigure
import app.tasca.data.figureOf
import app.tasca.domain.Money
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant

/**
 * The widget.json the iOS widgets read, since a widget extension has no database. For the balance widget: every
 * account it can show, the Total, the Net worth and the default expense account, which a widget with no pick shows.
 * For the recent activity widget: the latest [RECENT] transactions. The texts are those of the Android widgets.
 */
suspend fun widgetSnapshot(ledger: Ledger, defaults: DefaultAccounts, updated: Instant): String {
    val accounts = ledger.dao.balances().first()
    val recent = ledger.recentRows(RECENT)
    fun JsonObjectBuilder.amount(cents: Long) {
        put("amount", Money.format(cents))
        put("amountCents", cents)
    }
    return buildJsonObject {
        put("defaultId", defaultAccount(defaults, accounts, TxnKind.EXPENSE))
        putJsonArray("accounts") {
            for (a in accounts.filter { !it.deleted }) addJsonObject {
                put("id", a.id)
                put("name", a.name)
                amount(a.balanceCents)
            }
        }
        ledger.figureOf(WidgetFigure.Total, accounts)?.let { (_, cents) -> putJsonObject("total") { amount(cents) } }
        ledger.figureOf(WidgetFigure.NetWorth, accounts)?.let { (_, cents) -> putJsonObject("netWorth") { amount(cents) } }
        putJsonArray("recent") {
            for (r in recent) addJsonObject {
                put("id", r.id)
                put("name", r.name)
                put("detail", r.detail)
                put("amount", r.amount)
                put("kind", r.kind.name)
            }
        }
        put("updated", updated.toString())
    }.toString()
}

/** WidgetKit lists cannot scroll, so the iOS recent activity widget shows this many rows; Android's scrolls through 30. */
private const val RECENT = 6
