package app.tasca.ios

import app.tasca.data.Ledger
import app.tasca.ui.Settings
import app.tasca.ui.widgetSnapshot
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import kotlin.time.Clock

/** Writes widget.json to the App Group the widget extension shares, then asks WidgetKit to reload. */
internal object WidgetSnapshot {
    // To verify on a Mac: after a change, widget.json lists the balances and the latest transactions as Android's widgets show them.
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    suspend fun write(ledger: Ledger, settings: Settings) {
        val json = widgetSnapshot(ledger, settings.current().defaultAccounts, Clock.System.now())
        val folder = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier("group.app.tasca") ?: return
        val file = folder.URLByAppendingPathComponent("widget.json") ?: return
        val written = NSString.create(string = json).writeToURL(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (written) withContext(Dispatchers.Main) { IosApp.widgetsChanged() }
    }
}
