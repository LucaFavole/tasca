package app.tasca

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import app.tasca.data.Ledger
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.ui.AppViewModel
import app.tasca.ui.Settings
import app.tasca.ui.TascaUi
import app.tasca.ui.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TascaApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        useStoredCurrency(this)
        WorkManager.getInstance(this).cancelUniqueWork("recurring")
        Daily.schedule(this)
        scope.launch {
            Ledger.get(this@TascaApp).dao.ledgerChanges().debounce(500).collect {
                runCatching { LeftWidget.refresh(this@TascaApp) }
                runCatching { RecentWidget.refresh(this@TascaApp) }
            }
        }
    }
}

/** Money is shown in the stored currency from the start: the widgets and the daily job run without the screens. */
internal fun useStoredCurrency(context: Context) {
    Money.currency = runBlocking { Settings.get(context).current().currency }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel(Ledger.get(application), Settings.get(application)) } }
    }
    /** A screen a widget or the reminder asked for, such as the keypad; the UI opens it and clears the request. */
    private val openRoute = mutableStateOf<String?>(null)
    private val listen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@registerForActivityResult
        lifecycleScope.launch {
            vm.prepareSpoken(heard)
            openRoute.value = "txn"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { TascaUi(vm, AndroidPlatform, openRoute) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        intent.getLongExtra(EXTRA_TXN, -1).takeIf { it > 0 }?.let { openRoute.value = "txn?id=$it"; return }
        val mode = QuickAdd.of(intent) ?: return
        NotificationManagerCompat.from(this).cancel(Reminder.ID)
        when (mode) {
            QuickAdd.OPEN -> Unit
            QuickAdd.KEYPAD -> openRoute.value = "txn"
            QuickAdd.VOICE -> try {
                listen.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Es. \"15 euro pranzo\""),
                )
            } catch (_: ActivityNotFoundException) {
                openRoute.value = "txn"
            }
        }
    }

    companion object {
        /** Id of a transaction to open, sent by the recent activity widget. */
        const val EXTRA_TXN = "txn"
    }
}
