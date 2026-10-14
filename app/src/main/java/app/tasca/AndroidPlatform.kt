package app.tasca

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.tasca.domain.ReceiptScan
import app.tasca.ui.NotificationAsk
import app.tasca.ui.Platform
import app.tasca.ui.rememberReceiptScanner as rememberMlKitScanner

/** The shared screens' doors on Android: ML Kit for receipts, the system file picker, the notification permission. */
object AndroidPlatform : Platform {
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    @Composable
    override fun rememberReceiptScanner(onStart: () -> Unit, onResult: (ReceiptScan?) -> Unit): () -> Unit =
        rememberMlKitScanner(onStart, onResult)

    @Composable
    override fun rememberImportPicker(onPicked: (read: () -> ByteArray) -> Unit): () -> Unit {
        val resolver = LocalContext.current.applicationContext.contentResolver
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { onPicked { resolver.openInputStream(it)!!.use { stream -> stream.readBytes() } } }
        }
        return { importer.launch(arrayOf(XLSX, "application/octet-stream")) }
    }

    @Composable
    override fun rememberExportPicker(onPicked: (write: suspend (ByteArray) -> Boolean) -> Unit): (fileName: String) -> Unit {
        val resolver = LocalContext.current.applicationContext.contentResolver
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
            uri?.let { onPicked { bytes -> resolver.openOutputStream(it, "wt")!!.use { stream -> stream.write(bytes) }; true } }
        }
        return { name -> exporter.launch(name) }
    }

    @Composable
    override fun rememberNotificationAsk(onDenied: () -> Unit): NotificationAsk {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) onDenied()
        }
        return NotificationAsk(allowed = { Reminder.allowed(context) }, ask = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) })
    }
}
