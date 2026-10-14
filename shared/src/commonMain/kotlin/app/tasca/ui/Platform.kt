package app.tasca.ui

import androidx.compose.runtime.Composable
import app.tasca.domain.ReceiptScan

/** What the shared screens need from the phone itself. Android and iOS each implement it with their own tools. */
interface Platform {
    /**
     * Returns the function that starts a receipt scan. [onStart] runs once the camera is showing.
     * [onResult] gets null when the scan was cancelled or failed.
     */
    @Composable fun rememberReceiptScanner(onStart: () -> Unit, onResult: (ReceiptScan?) -> Unit): () -> Unit

    /** Returns the function that lets the user pick a spreadsheet. [onPicked] gets a reader to call off the main thread. */
    @Composable fun rememberImportPicker(onPicked: (read: () -> ByteArray) -> Unit): () -> Unit

    /**
     * Returns the function that lets the user save a spreadsheet under a file name. [onPicked] gets a writer to call
     * off the main thread, which returns false when the user backed out. Android asks where before the file is
     * written, iOS after.
     */
    @Composable fun rememberExportPicker(onPicked: (write: suspend (ByteArray) -> Boolean) -> Unit): (fileName: String) -> Unit

    /** The permission the daily reminder needs. [onDenied] runs when the user refuses it. */
    @Composable fun rememberNotificationAsk(onDenied: () -> Unit): NotificationAsk
}

/** Whether the reminder may show a notification, and a way to ask the user for that. */
class NotificationAsk(val allowed: suspend () -> Boolean, val ask: () -> Unit)
