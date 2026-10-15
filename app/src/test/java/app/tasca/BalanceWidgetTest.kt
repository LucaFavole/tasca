package app.tasca

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tasca.data.Account
import app.tasca.data.Ledger
import app.tasca.data.WidgetFigure
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.ui.Settings
import app.tasca.ui.get
import app.tasca.ui.theme.ColorTheme
import app.tasca.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager

/** Balance widgets placed before the move to DataStore keep what they showed, and a new pick in the config screen shows. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class BalanceWidgetTest {
    @Test fun widgetsShowWhatTheOldSettingsFilePickedAndNewPicks() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val ledger = Ledger.get(context)
        ledger.ensureSeeded()
        val main = ledger.dao.accounts().single { it.name == "Main" }.id
        val savings = ledger.saveAccount(Account(name = "Risparmi", color = 0, initialCents = 5_000))
        val card = ledger.saveAccount(Account(name = "Carta", color = 0, initialCents = -1_250))
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putLong("widgetAccount1", savings).putLong("widgetAccount2", -10).putLong("defaultExpenseAccount", card)
            .commit()

        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val ids = List(3) { widgets.createWidget(LeftWidget::class.java, R.layout.widget_left) }
        assertEquals("the old file names widgets 1 and 2", listOf(1, 2, 3), ids)
        widgets.awaitShown(1, "Risparmi", 5_000)
        widgets.awaitShown(2, "Total", 3_750)
        widgets.awaitShown(3, "Carta", -1_250)

        LeftWidget.setFigure(context, 3, WidgetFigure.Account(main))
        LeftWidget.refresh(context)
        widgets.awaitShown(3, "Main", 0)
        widgets.awaitShown(1, "Risparmi", 5_000)
    }

    /** Placing a widget draws it in the background, so wait for it. */
    private fun ShadowAppWidgetManager.awaitShown(widget: Int, title: String, cents: Long) {
        val expected = listOf(title, Money.format(cents))
        fun shown() = listOf(R.id.title, R.id.amount).map { getViewFor(widget).findViewById<TextView>(it).text.toString() }
        val deadline = System.currentTimeMillis() + 10_000
        while (shown() != expected && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("widget $widget", expected, shown())
    }
}

/** A balance widget draws in the colours of the theme picked in Settings. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class WidgetThemeTest {
    @Test fun balanceWidgetUsesTheChosenTheme() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        Ledger.get(context).ensureSeeded()
        Settings.get(context).update { it.copy(theme = ThemeMode.LIGHT, colors = ColorTheme.GRAPHITE) }
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val id = widgets.createWidget(LeftWidget::class.java, R.layout.widget_left)
        val deadline = System.currentTimeMillis() + 10_000
        while (widgets.getViewFor(id).findViewById<TextView>(R.id.title).text == "Account" && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val view = widgets.getViewFor(id)
        assertEquals("Graphite's light grey label", 0xFF5E5E5E.toInt(), view.findViewById<TextView>(R.id.title).currentTextColor)
        assertEquals("the light theme's income green", 0xFF12A36B.toInt(), view.findViewById<TextView>(R.id.amount).currentTextColor)
    }
}
