package app.tasca

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Ways to open the app straight into adding something: from the home-screen widget or the daily reminder. */
enum class QuickAdd {
    /** Just open the app (the reminder itself). */
    OPEN,
    /** Open the keypad for a new expense. */
    KEYPAD,
    /** Listen to a sentence like "15 euro pranzo", then open the keypad filled in. */
    VOICE;

    fun pending(context: Context): PendingIntent = PendingIntent.getActivity(
        context, ordinal,
        Intent(context, MainActivity::class.java).putExtra(EXTRA, name).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val EXTRA = "quickAdd"
        fun of(intent: Intent?): QuickAdd? = intent?.getStringExtra(EXTRA)?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

/** Home-screen widget with two buttons: + opens the keypad, the microphone adds by voice. */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_add).apply {
            setOnClickPendingIntent(R.id.add, QuickAdd.KEYPAD.pending(context))
            setOnClickPendingIntent(R.id.voice, QuickAdd.VOICE.pending(context))
        }
        manager.updateAppWidget(ids, views)
    }
}
