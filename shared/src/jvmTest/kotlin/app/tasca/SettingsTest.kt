package app.tasca

import app.tasca.data.TxnKind
import app.tasca.data.WidgetFigure
import app.tasca.ui.DefaultAccounts
import app.tasca.ui.Prefs
import app.tasca.ui.Settings
import app.tasca.ui.theme.ThemeMode
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    private fun fresh() = Settings.at(Files.createTempDirectory("settings").resolve("settings.preferences_pb").toString())

    @Test fun emptyFileReadsAsTheOldDefaults() = runTest {
        assertEquals(Prefs(ThemeMode.SYSTEM, "EUR", 26.0, reminder = true, askedNotifications = false), fresh().current())
    }

    @Test fun updateKeepsTheChangeAndReturnsWhatItReplaced() = runTest {
        val settings = fresh()
        val before = settings.update { it.copy(theme = ThemeMode.DARK, currency = "CHF", askedNotifications = true) }
        assertEquals(Prefs(), before)
        assertEquals(Prefs(ThemeMode.DARK, "CHF", askedNotifications = true), settings.current())
        assertEquals(true, settings.update { it.copy(askedNotifications = true) }.askedNotifications)
    }

    @Test fun taxRateIsStoredAsAFloatLikeBefore() = runTest {
        val settings = fresh()
        settings.update { it.copy(taxRatePct = 26.3) }
        assertEquals(26.3f.toDouble(), settings.current().taxRatePct)
    }

    @Test fun widgetFiguresAndDefaultAccountsRoundTripAndRemovedWidgetsAreForgotten() = runTest {
        val settings = fresh()
        val figures = mapOf(3 to WidgetFigure.Account(7), 4 to WidgetFigure.Total, 5 to WidgetFigure.NetWorth)
        settings.update { it.copy(defaultAccounts = DefaultAccounts(expense = 2), widgetFigures = figures) }
        settings.update { it.copy(defaultAccounts = it.defaultAccounts.with(TxnKind.INCOME, 5), widgetFigures = it.widgetFigures - 3) }
        val now = settings.current()
        assertEquals(DefaultAccounts(expense = 2, income = 5), now.defaultAccounts)
        assertEquals(mapOf(4 to WidgetFigure.Total, 5 to WidgetFigure.NetWorth), now.widgetFigures)
        assertEquals(2L, now.defaultAccounts.of(TxnKind.TRANSFER))
    }
}
