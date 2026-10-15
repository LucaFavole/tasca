package app.tasca.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.tasca.data.Ledger
import app.tasca.data.open
import app.tasca.domain.Money
import app.tasca.ui.AppViewModel
import app.tasca.ui.Settings
import app.tasca.ui.TascaUi
import app.tasca.ui.theme.TascaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSThread
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * What the Swift app calls: the Compose UI, the tasca:// links, and the hook that reloads the widgets.
 * The links are the three ways Android's QuickAdd opens the app: "open", "add" (the keypad) and "voice", plus
 * "txn/<id>", which opens a transaction as a row of Android's recent activity widget does.
 */
object IosApp {
    /** Swift sets it to reload the WidgetKit timelines; called after each new widget snapshot. */
    var reloadWidgets: (() -> Unit)? = null

    private val folder = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    private val ledger by lazy { Ledger.open("$folder/tasca.db") }
    private val settings by lazy { Settings.at("$folder/settings.preferences_pb") }
    private val scope = MainScope()
    private val openRoute = mutableStateOf<String?>(null)
    private var vm: AppViewModel? = null
    /** A link that came before the UI existed, as on a cold launch from Siri, the control or a widget. */
    private var pendingLink: String? = null

    init {
        // Apple asks for the delegate before the app finishes launching, so a tap that launched the app still arrives.
        Reminder.install(onLink = ::open)
    }

    // To verify on a Mac: the app opens full screen on Home with the data in Documents/tasca.db.
    fun mainViewController(): UIViewController {
        val model = vm ?: start()
        val controller = ComposeUIViewController {
            Box(Modifier.fillMaxSize()) {
                TascaUi(model, IosPlatform, openRoute)
                val theme by model.theme.collectAsState()
                val colors by model.colors.collectAsState()
                TascaTheme(theme, colors) { Voice.Dialog() }
            }
        }
        IosPlatform.host = controller
        pendingLink?.let {
            pendingLink = null
            follow(it, model)
        }
        return controller
    }

    // To verify on a Mac: tasca://add opens the keypad, tasca://txn/<id> that transaction and tasca://voice listens, also on a cold launch.
    fun open(url: String) {
        if (!NSThread.isMainThread) return dispatch_async(dispatch_get_main_queue()) { open(url) }
        val model = vm
        if (model == null) pendingLink = url else follow(url, model)
    }

    private fun follow(url: String, model: AppViewModel) {
        val path = url.removePrefix("tasca://").substringBefore('?').split('/')
        when (path.first()) {
            "add" -> openRoute.value = "txn"
            "txn" -> path.getOrNull(1)?.toLongOrNull()?.let { openRoute.value = "txn?id=$it" }
            "voice" -> Voice.listen(
                onHeard = { text ->
                    scope.launch {
                        model.prepareSpoken(text)
                        openRoute.value = "txn"
                    }
                },
                onUnavailable = { openRoute.value = "txn" },
            )
        }
    }

    // To verify on a Mac: a payment due today is posted when the app opens or comes back to the foreground.
    /** Builds the model once and starts what Android's TascaApp and daily job do: the widget, posting, the reminder. */
    @OptIn(FlowPreview::class)
    private fun start(): AppViewModel {
        Money.currency = runBlocking { settings.current().currency }
        val model = AppViewModel(ledger, settings)
        vm = model
        scope.launch(Dispatchers.IO) {
            ledger.dao.ledgerChanges().debounce(500).collect { runCatching { WidgetSnapshot.write(ledger, settings) } }
        }
        scope.launch { settings.prefs.map { it.reminder }.distinctUntilChanged().collect { Reminder.schedule(it) } }
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            scope.launch {
                runCatching { ledger.postDueRecurring() }
                runCatching { WidgetSnapshot.write(ledger, settings) }
                Reminder.schedule(settings.current().reminder)
            }
        }
        return model
    }

    internal fun widgetsChanged() = reloadWidgets?.invoke()
}
