package app.tasca

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import app.tasca.data.Ledger
import app.tasca.domain.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget showing what will be left at the end of the month, after the regular payments still due. */
class LeftWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                manager.updateAppWidget(ids, views(context))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LeftWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private suspend fun views(context: Context): RemoteViews {
            val m = Ledger.get(context).monthLeft()
            return RemoteViews(context.packageName, R.layout.widget_left).apply {
                setTextViewText(R.id.amount, Money.format(m.leftCents))
                setTextColor(R.id.amount, if (m.leftCents >= 0) 0xFF3DDC97.toInt() else 0xFFFF6B6B.toInt())
                setTextViewText(
                    R.id.detail,
                    "Spent ${Money.format(m.spentCents)}" + if (m.dueCents > 0) " · due ${Money.format(m.dueCents)}" else "",
                )
                setOnClickPendingIntent(R.id.root, QuickAdd.OPEN.pending(context))
            }
        }
    }
}
