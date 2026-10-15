package app.tasca

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import app.tasca.data.Ledger
import app.tasca.data.TxnKind
import app.tasca.data.get
import app.tasca.ui.RecentRow
import app.tasca.ui.recentRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget listing the latest transactions; tapping one opens it, tapping the title opens the app. */
class RecentWidget : AppWidgetProvider() {
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, RecentWidget::class.java)))
    }

    companion object {
        private const val ROWS = 30

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecentWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val rows = Ledger.get(context).recentRows(ROWS)
            val paint = WidgetPaint.of(context)
            val items = RemoteViewsCompat.RemoteCollectionItems.Builder()
                .apply { rows.forEach { addItem(it.id, row(context, paint, it)) } }
                .setHasStableIds(true)
                .setViewTypeCount(1)
                .build()
            val openTxn = PendingIntent.getActivity(
                context, 100,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_recent).apply {
                    with(paint) {
                        tint(R.id.bg) { it.background }
                        text(R.id.title) { it.subtext }
                        text(R.id.empty) { it.subtext }
                    }
                    setOnClickPendingIntent(R.id.title, QuickAdd.OPEN.pending(context))
                    setPendingIntentTemplate(R.id.list, openTxn)
                    setEmptyView(R.id.list, R.id.empty)
                }
                RemoteViewsCompat.setRemoteAdapter(context, views, id, R.id.list, items)
                manager.updateAppWidget(id, views)
            }
        }

        private fun row(context: Context, paint: WidgetPaint, t: RecentRow) = RemoteViews(context.packageName, R.layout.widget_recent_row).apply {
            setTextViewText(R.id.name, t.name)
            setTextViewText(R.id.detail, t.detail)
            setTextViewText(R.id.amount, t.amount)
            with(paint) {
                text(R.id.name) { it.text }
                text(R.id.detail) { it.subtext }
                text(R.id.amount) {
                    when (t.kind) {
                        TxnKind.EXPENSE -> it.expense
                        TxnKind.INCOME -> it.income
                        TxnKind.TRANSFER -> it.text
                    }
                }
            }
            setOnClickFillInIntent(R.id.row, Intent().putExtra(MainActivity.EXTRA_TXN, t.id))
        }
    }
}
