package app.tasca

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.tasca.data.Ledger
import app.tasca.data.TxnKind
import app.tasca.data.WidgetFigure
import app.tasca.data.figureOf
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.ui.Settings
import app.tasca.ui.defaultAccount
import app.tasca.ui.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget showing one name and one amount: an account's balance, the Total or the Net worth, picked in
 * [BalanceWidgetConfig]. The class keeps its first name so widgets already placed keep working.
 */
class LeftWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = inBackground {
        update(context, manager, ids)
    }

    /** An app update resets placed widgets to their empty layout, so redraw them as soon as it lands. */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, LeftWidget::class.java)))
    }

    override fun onDeleted(context: Context, ids: IntArray) = inBackground {
        Settings.get(context).update { it.copy(widgetFigures = it.widgetFigures - ids.toSet()) }
    }

    companion object {
        suspend fun setFigure(context: Context, widgetId: Int, figure: WidgetFigure) {
            Settings.get(context).update { it.copy(widgetFigures = it.widgetFigures + (widgetId to figure)) }
        }

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LeftWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val ledger = Ledger.get(context)
            val accounts = ledger.dao.balances().first()
            val prefs = Settings.get(context).current()
            val paint = WidgetPaint.of(context)
            val fallback = defaultAccount(prefs.defaultAccounts, accounts, TxnKind.EXPENSE)?.let { WidgetFigure.Account(it) }
            for (id in ids) {
                val chosen = prefs.widgetFigures[id]
                val shown = chosen?.let { ledger.figureOf(it, accounts) } ?: fallback?.let { ledger.figureOf(it, accounts) }
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_left).apply {
                    with(paint) {
                        tint(R.id.bg) { it.background }
                        text(R.id.title) { it.subtext }
                        if (shown != null) {
                            val (name, cents) = shown
                            setTextViewText(R.id.title, name)
                            setTextViewText(R.id.amount, Money.format(cents))
                            text(R.id.amount) { if (cents >= 0) it.income else it.expense }
                        }
                    }
                    setOnClickPendingIntent(R.id.root, QuickAdd.OPEN.pending(context))
                })
            }
        }
    }
}

private fun BroadcastReceiver.inBackground(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            runCatching { block() }
        } finally {
            pending.finish()
        }
    }
}
