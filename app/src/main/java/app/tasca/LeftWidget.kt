package app.tasca

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.tasca.data.AccountBalance
import app.tasca.data.Ledger
import app.tasca.data.Portfolio
import app.tasca.data.TxnKind
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.ui.defaultAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What one balance widget shows. Stored per widget as a Long: an account id, or a negative code for the totals. */
sealed interface WidgetFigure {
    data class Account(val id: Long) : WidgetFigure
    /** Every account added up, as the Total on the Accounts tab. */
    data object Total : WidgetFigure
    /** The Total plus what the investments gained or lost at the last fetched prices. */
    data object NetWorth : WidgetFigure

    fun encode(): Long = when (this) {
        is Account -> id
        Total -> TOTAL
        NetWorth -> NET_WORTH
    }

    companion object {
        private const val TOTAL = -10L
        private const val NET_WORTH = -11L

        fun decode(stored: Long): WidgetFigure? = when {
            stored == TOTAL -> Total
            stored == NET_WORTH -> NetWorth
            stored > 0 -> Account(stored)
            else -> null
        }
    }
}

/**
 * Home-screen widget showing one name and one amount: an account's balance, the Total or the Net worth, picked in
 * [BalanceWidgetConfig]. The class keeps its first name so widgets already placed keep working.
 */
class LeftWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { update(context, manager, ids) }
            } finally {
                pending.finish()
            }
        }
    }

    /** An app update resets placed widgets to their empty layout, so redraw them as soon as it lands. */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, LeftWidget::class.java)))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(key(it)) } }.apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        private fun key(widgetId: Int) = "widgetAccount$widgetId"

        fun setFigure(context: Context, widgetId: Int, figure: WidgetFigure) =
            prefs(context).edit().putLong(key(widgetId), figure.encode()).apply()

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LeftWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        /** The name and amount of [figure], or null when its account no longer exists. */
        suspend fun figureOf(context: Context, figure: WidgetFigure, accounts: List<AccountBalance>): Pair<String, Long>? {
            val total = accounts.sumOf { it.balanceCents }
            return when (figure) {
                is WidgetFigure.Account -> accounts.firstOrNull { it.id == figure.id && !it.deleted }?.let { it.name to it.balanceCents }
                WidgetFigure.Total -> "Total" to total
                WidgetFigure.NetWorth -> {
                    val dao = Ledger.get(context).dao
                    "Net worth" to total + Portfolio.of(dao.holdings(), dao.quotes().associateBy { it.symbol }).gainCents
                }
            }
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val accounts = Ledger.get(context).dao.balances().first()
            val prefs = prefs(context)
            val fallback = defaultAccount(prefs, accounts, TxnKind.EXPENSE)?.let { WidgetFigure.Account(it) }
            for (id in ids) {
                val chosen = WidgetFigure.decode(prefs.getLong(key(id), 0))
                val shown = chosen?.let { figureOf(context, it, accounts) } ?: fallback?.let { figureOf(context, it, accounts) }
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_left).apply {
                    if (shown != null) {
                        val (name, cents) = shown
                        setTextViewText(R.id.title, name)
                        setTextViewText(R.id.amount, Money.format(cents))
                        setTextColor(R.id.amount, if (cents >= 0) 0xFF3DDC97.toInt() else 0xFFFF6B6B.toInt())
                    }
                    setOnClickPendingIntent(R.id.root, QuickAdd.OPEN.pending(context))
                })
            }
        }
    }
}
