package app.tasca

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import app.tasca.data.Ledger
import app.tasca.data.TxnKind
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.ui.defaultAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget showing one account's name and balance; each widget remembers the account picked in
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

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(key(it)) } }.apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        private fun key(widgetId: Int) = "widgetAccount$widgetId"

        fun setAccount(context: Context, widgetId: Int, accountId: Long) =
            prefs(context).edit().putLong(key(widgetId), accountId).apply()

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LeftWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val accounts = Ledger.get(context).dao.balances().first()
            val prefs = prefs(context)
            for (id in ids) {
                val chosen = prefs.getLong(key(id), -1)
                val account = accounts.firstOrNull { it.id == chosen && !it.deleted }
                    ?: accounts.firstOrNull { it.id == defaultAccount(prefs, accounts, TxnKind.EXPENSE) }
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_left).apply {
                    if (account != null) {
                        setTextViewText(R.id.title, account.name)
                        setTextViewText(R.id.amount, Money.format(account.balanceCents))
                        setTextColor(R.id.amount, if (account.balanceCents >= 0) 0xFF3DDC97.toInt() else 0xFFFF6B6B.toInt())
                    }
                    setOnClickPendingIntent(R.id.root, QuickAdd.OPEN.pending(context))
                })
            }
        }
    }
}
