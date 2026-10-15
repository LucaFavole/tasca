package app.tasca

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tasca.data.Account
import app.tasca.data.Ledger
import app.tasca.data.Txn
import app.tasca.data.TxnKind
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.domain.Period
import app.tasca.domain.PeriodKind
import app.tasca.domain.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// The ledger and the settings are one per process, so each class runs in its own JVM (forkEvery = 1) with fresh data.
// The waits are long because the first screen of a cold Robolectric JVM can take over 10 s on a busy machine.

/** The tabs and Settings show, every tab reaches Settings, and the reminder asks for notifications once at launch. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class TabsAndSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tabsSettingsAndTheLaunchPrompt() {
        compose.onNodeWithText("Accounts").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Main"), 30_000)
        for (next in listOf("Stats", "Plan", "Home")) {
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Export to Excel").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText(next).performClick()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Daily reminder"))
        compose.onNodeWithText("Daily reminder").assertExists()
        compose.onNodeWithText("Import from Excel").assertExists()
        compose.onNodeWithText("Export to Excel").assertExists()
        val asked = shadowOf(compose.activity).lastRequestedPermission?.requestedPermissions?.toList()
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), asked)
    }
}

/** The keypad's draft survives the activity being recreated, as on rotation, and saving lands on Home. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class KeypadDraftTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun keypadKeepsItsDraftAcrossRecreationAndSaves() {
        compose.onNodeWithContentDescription("Add transaction").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Caffè"), 30_000)
        compose.onNodeWithText("New transaction").assertExists()
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText(Money.format(1200)).assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Caffè"), 30_000)
        compose.onNodeWithText("New transaction").assertExists()
        compose.onNodeWithText(Money.format(1200)).assertExists()

        compose.onNodeWithText("Caffè").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntilDoesNotExist(hasText("New transaction"), 30_000)
        compose.waitUntilAtLeastOneExists(hasText("Caffè"), 30_000)
    }
}

/** A widget's + button, or the reminder's "Add expense" action, opens the keypad, also on a cold start. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class QuickAddLinkTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun keypadLinkOpensTheKeypad() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ActivityScenario.launch<MainActivity>(shadowOf(QuickAdd.KEYPAD.pending(app)).savedIntent).use {
            compose.waitUntilAtLeastOneExists(hasText("New transaction"), 30_000)
        }
    }
}

/** A row of the recent activity widget opens its transaction, also on a cold start. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class RecentTxnLinkTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun recentRowOpensItsTransaction() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val id = runBlocking {
            val ledger = Ledger.get(app)
            ledger.ensureSeeded()
            val main = ledger.dao.accounts().single().id
            val coffee = ledger.dao.categories().first { it.name == "Caffè" }.id
            ledger.dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = 1_234, date = today(), accountId = main, categoryId = coffee))
        }
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_TXN, id)).use {
            compose.waitUntilAtLeastOneExists(hasText("Edit transaction"), 30_000)
            compose.onNodeWithText(Money.format(1_234)).assertExists()
        }
    }
}

/** On Home, swiping a transaction left deletes it, with Undo in the snackbar. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class HomeSwipeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun swipeLeftDeletesWithUndo() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            val ledger = Ledger.get(app)
            ledger.ensureSeeded()
            val main = ledger.dao.accounts().single().id
            val coffee = ledger.dao.categories().first { it.name == "Caffè" }.id
            ledger.dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = 850, date = today(), accountId = main, categoryId = coffee, note = "Pizza"))
        }
        val month = Period.of(PeriodKind.MONTH)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasText(month.label()), 30_000)
            compose.onNodeWithContentDescription("Transactions").performClick()
            compose.waitUntilAtLeastOneExists(hasText("Pizza", substring = true), 30_000)

            compose.onNodeWithText("Pizza", substring = true).performTouchInput { swipeLeft() }
            compose.waitUntilAtLeastOneExists(hasText("Undo"), 30_000)
            compose.waitUntilDoesNotExist(hasText("Pizza", substring = true), 30_000)
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntilAtLeastOneExists(hasText("Pizza", substring = true), 30_000)
        }
    }
}

/** Back on Home closes the search, then undoes the narrowest filter, and only then leaves the app. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class HomeBackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun backStepsOutOfSearchAndFiltersBeforeLeaving() {
        compose.waitUntilAtLeastOneExists(hasText("Expenses"), 30_000)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Search notes, categories, accounts").assertExists()
        back()
        compose.onNodeWithText("Search notes, categories, accounts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Transactions").assertIsSelected()

        compose.onNodeWithText("Income").performClick()
        compose.onNodeWithText("Income").assertIsSelected()
        back()
        compose.onNodeWithText("Income").assertIsNotSelected()
        compose.onNodeWithContentDescription("Transactions").assertIsSelected()
        back()
        compose.onNodeWithContentDescription("Categories").assertIsSelected()
        assertFalse(compose.activity.isFinishing)
        back()
        assertTrue("with nothing narrowed, Back leaves", compose.activity.isFinishing)
    }
}

/** New entries start on Main, income on Risparmi, until an account is picked by hand; Settings changes the defaults. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class DefaultAccountTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun ComposeTestRule.openKeypad() {
        onNodeWithContentDescription("Add transaction").performClick()
        waitUntilAtLeastOneExists(hasText("New transaction"), 30_000)
        waitUntilAtLeastOneExists(hasText("Risparmi"), 30_000)
    }

    @Test fun newEntriesStartOnTheDefaultAccountOfTheirKind() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            val ledger = Ledger.get(app)
            ledger.ensureSeeded()
            ledger.saveAccount(Account(name = "Carta", color = 0))
            ledger.saveAccount(Account(name = "Risparmi", color = 0))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.openKeypad()
            compose.onNodeWithText("Main").assertIsSelected()
            compose.onNodeWithText("Income").performClick()
            compose.onNodeWithText("Risparmi").assertIsSelected()
            compose.onNodeWithText("Expense").performClick()
            compose.onNodeWithText("Main").assertIsSelected()
            compose.onNodeWithText("Carta").performClick()
            compose.onNodeWithText("Income").performClick()
            compose.onNodeWithText("Carta").assertIsSelected()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Default accounts").assertExists()
            compose.onNodeWithText("Risparmi").assertExists()
            compose.onNodeWithText("Expenses").performClick()
            compose.onNodeWithText("New expenses go to").assertExists()
            compose.onNodeWithText("Carta").performClick()
            compose.onNodeWithText("Carta").assertExists()
            compose.onNodeWithText("Main").assertDoesNotExist()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.openKeypad()
            compose.onNodeWithText("Carta").assertIsSelected()
        }
    }
}
