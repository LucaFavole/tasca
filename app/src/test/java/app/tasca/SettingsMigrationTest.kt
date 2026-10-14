package app.tasca

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tasca.data.WidgetFigure
import app.tasca.domain.Money
import app.tasca.ui.DefaultAccounts
import app.tasca.ui.Prefs
import app.tasca.ui.Settings
import app.tasca.ui.get
import app.tasca.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The settings an installed phone kept in SharedPreferences survive the move to DataStore, for every reader. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class SettingsMigrationTest {
    @Test fun everyReaderSeesTheOldSettingsAndLaterChanges() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val old = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        old.edit()
            .putString("theme", "DARK").putString("currency", "USD").putFloat("taxRate", 12.5f)
            .putBoolean("reminder", false).putBoolean("askedNotifications", true)
            .putLong("defaultExpenseAccount", 3).putLong("defaultIncomeAccount", 4)
            .putLong("widgetAccount12", 5).putLong("widgetAccount13", -10).putLong("widgetAccount14", -11)
            .commit()

        val settings = Settings.get(context)
        assertEquals(
            Prefs(
                ThemeMode.DARK, "USD", 12.5, reminder = false, askedNotifications = true, DefaultAccounts(3, 4),
                mapOf(12 to WidgetFigure.Account(5), 13 to WidgetFigure.Total, 14 to WidgetFigure.NetWorth),
            ),
            settings.current(),
        )
        assertTrue("the old file is emptied once moved", old.all.isEmpty())
        useStoredCurrency(context)
        assertEquals("USD", Money.currency)
        assertFalse(Reminder.isOn(context))

        settings.update { it.copy(currency = "CHF", reminder = true) }
        useStoredCurrency(context)
        assertEquals("CHF", Money.currency)
        assertTrue(Reminder.isOn(context))
        Money.currency = "EUR"
    }
}
