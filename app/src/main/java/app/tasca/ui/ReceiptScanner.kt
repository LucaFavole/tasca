package app.tasca.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.tasca.domain.ReceiptLine
import app.tasca.domain.ReceiptParser
import app.tasca.domain.ReceiptScan
import app.tasca.domain.today
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

/**
 * Photographs a receipt with Google's document scanner and reads it on the phone with ML Kit; nothing is uploaded.
 * Returns the function that starts a scan. [onResult] gets null when the scan was cancelled or failed.
 */
@Composable
fun rememberReceiptScanner(onStart: () -> Unit, onResult: (ReceiptScan?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val uri = GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.firstOrNull()?.imageUri
        if (r.resultCode != Activity.RESULT_OK || uri == null) result(null) else read(context, uri, result)
    }
    return {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(context.activity())
            .addOnSuccessListener {
                // The screen may have closed while the intent was prepared, leaving the launcher unregistered.
                runCatching { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                    .onSuccess { onStart() }
                    .onFailure { result(null) }
            }
            .addOnFailureListener { result(null) }
    }
}

private fun read(context: Context, uri: Uri, onResult: (ReceiptScan?) -> Unit) {
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse { onResult(null); return }
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { text -> onResult(ReceiptParser.parse(rows(text), today())) }
        .addOnFailureListener { onResult(null) }
        .addOnCompleteListener { recognizer.close() }
}

/** Rebuilds printed rows: a label and its amount are often recognised as separate blocks side by side. */
private fun rows(text: Text): List<ReceiptLine> {
    val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }.sortedBy { it.boundingBox!!.centerY() }
    val rows = mutableListOf<MutableList<Text.Line>>()
    for (line in lines) {
        val box = line.boundingBox!!
        val ref = rows.lastOrNull()?.first()?.boundingBox
        if (ref != null && abs(box.centerY() - ref.centerY()) < ref.height() * 0.6) rows.last() += line else rows += mutableListOf(line)
    }
    return rows.map { row ->
        ReceiptLine(row.sortedBy { it.boundingBox!!.left }.joinToString("  ") { it.text }, row.maxOf { it.boundingBox!!.height() })
    }
}

private fun Context.activity(): Activity {
    var c: Context = this
    while (c !is Activity) c = (c as ContextWrapper).baseContext
    return c
}
