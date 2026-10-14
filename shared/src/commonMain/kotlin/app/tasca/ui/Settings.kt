package app.tasca.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.tasca.data.TxnKind
import app.tasca.data.WidgetFigure
import app.tasca.ui.theme.ThemeMode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

/** What the user chose in Settings. A value never stored reads as its default. */
data class Prefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val currency: String = "EUR",
    val taxRatePct: Double = 26.0,
    val reminder: Boolean = true,
    /** Whether the app already asked for notifications at launch, which it does once. */
    val askedNotifications: Boolean = false,
    val defaultAccounts: DefaultAccounts = DefaultAccounts(),
    /** What each placed balance widget shows, by widget id. */
    val widgetFigures: Map<Int, WidgetFigure> = emptyMap(),
)

/** The accounts picked in Settings › Default accounts; null where none was picked. Transfers use the expense one. */
data class DefaultAccounts(val expense: Long? = null, val income: Long? = null) {
    fun of(kind: TxnKind): Long? = if (kind == TxnKind.INCOME) income else expense
    fun with(kind: TxnKind, id: Long) = if (kind == TxnKind.INCOME) copy(income = id) else copy(expense = id)
}

/**
 * The settings file, shared by the screens, the daily job and the widgets.
 * Keys and types are those of the SharedPreferences file "settings" that earlier Android versions wrote.
 */
class Settings internal constructor(private val store: DataStore<Preferences>) {
    val prefs: Flow<Prefs> = store.data.map { it.toPrefs() }

    suspend fun current(): Prefs = prefs.first()

    /** Applies [change] in one atomic write and returns the values it replaced. Finishes even if the caller is cancelled. */
    suspend fun update(change: (Prefs) -> Prefs): Prefs = withContext(NonCancellable) {
        lateinit var old: Prefs
        store.edit {
            old = it.toPrefs()
            it.put(change(old))
        }
        old
    }

    companion object {
        /** The file name, also of the SharedPreferences file it replaces. */
        const val NAME = "settings"

        private val THEME = stringPreferencesKey("theme")
        private val CURRENCY = stringPreferencesKey("currency")
        private val TAX_RATE = floatPreferencesKey("taxRate")
        private val REMINDER = booleanPreferencesKey("reminder")
        private val ASKED_NOTIFICATIONS = booleanPreferencesKey("askedNotifications")
        private val DEFAULT_EXPENSE_ACCOUNT = longPreferencesKey("defaultExpenseAccount")
        private val DEFAULT_INCOME_ACCOUNT = longPreferencesKey("defaultIncomeAccount")
        /** Followed by the widget id. The name is from when a widget could only show an account. */
        private const val WIDGET_FIGURE = "widgetAccount"

        private fun Preferences.toPrefs() = Prefs().let { d ->
            Prefs(
                theme = this[THEME]?.let { t -> ThemeMode.entries.firstOrNull { it.name == t } } ?: d.theme,
                currency = this[CURRENCY] ?: d.currency,
                taxRatePct = this[TAX_RATE]?.toDouble() ?: d.taxRatePct,
                reminder = this[REMINDER] ?: d.reminder,
                askedNotifications = this[ASKED_NOTIFICATIONS] ?: d.askedNotifications,
                defaultAccounts = DefaultAccounts(this[DEFAULT_EXPENSE_ACCOUNT], this[DEFAULT_INCOME_ACCOUNT]),
                widgetFigures = asMap().entries.mapNotNull { (key, value) ->
                    val widget = key.name.takeIf { it.startsWith(WIDGET_FIGURE) }?.removePrefix(WIDGET_FIGURE)?.toIntOrNull()
                    val figure = (value as? Long)?.let(WidgetFigure::decode)
                    if (widget != null && figure != null) widget to figure else null
                }.toMap(),
            )
        }

        private fun MutablePreferences.put(p: Prefs) {
            this[THEME] = p.theme.name
            this[CURRENCY] = p.currency
            this[TAX_RATE] = p.taxRatePct.toFloat()
            this[REMINDER] = p.reminder
            this[ASKED_NOTIFICATIONS] = p.askedNotifications
            putOrRemove(DEFAULT_EXPENSE_ACCOUNT, p.defaultAccounts.expense)
            putOrRemove(DEFAULT_INCOME_ACCOUNT, p.defaultAccounts.income)
            asMap().keys.filter { it.name.startsWith(WIDGET_FIGURE) }.forEach { this -= it }
            p.widgetFigures.forEach { (widget, figure) -> this[longPreferencesKey("$WIDGET_FIGURE$widget")] = figure.encode() }
        }

        private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
            if (value == null) remove(key) else this[key] = value
        }

        /** Settings kept in the file at [path], which must end in ".preferences_pb". One instance per file. */
        fun at(path: String) = Settings(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
