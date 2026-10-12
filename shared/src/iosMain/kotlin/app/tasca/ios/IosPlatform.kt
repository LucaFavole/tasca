package app.tasca.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.tasca.domain.OcrLine
import app.tasca.domain.ReceiptParser
import app.tasca.domain.ReceiptScan
import app.tasca.domain.receiptRows
import app.tasca.domain.today
import app.tasca.io.toByteArray
import app.tasca.io.toNSData
import app.tasca.ui.NotificationAsk
import app.tasca.ui.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** The shared screens' doors on iOS: VisionKit and Vision for receipts, the Files pickers, the notification permission. */
internal object IosPlatform : Platform {
    /** The Compose screen, which presents the camera and the pickers. */
    var host: UIViewController? = null

    fun present(controller: UIViewController) {
        var top = host
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(controller, animated = true, completion = null)
    }

    @Composable
    override fun rememberReceiptScanner(onStart: () -> Unit, onResult: (ReceiptScan?) -> Unit): () -> Unit {
        val start by rememberUpdatedState(onStart)
        val result by rememberUpdatedState(onResult)
        return remember { { ReceiptCamera.scan(onStart = { start() }, onResult = { result(it) }) } }
    }

    // To verify on a Mac: the picker offers .xlsx files from Files and iCloud Drive, and importing one works as on Android.
    @Composable
    override fun rememberImportPicker(onPicked: (read: () -> ByteArray) -> Unit): () -> Unit {
        val picked by rememberUpdatedState(onPicked)
        return remember {
            {
                val xlsx = UTType.typeWithFilenameExtension("xlsx") ?: UTTypeData
                Files.show(UIDocumentPickerViewController(forOpeningContentTypes = listOf(xlsx), asCopy = true)) { urls ->
                    urls.firstOrNull()?.let { url -> picked { NSData.dataWithContentsOfURL(url)!!.toByteArray() } }
                }
            }
        }
    }

    // To verify on a Mac: the export saves tasca_<date>.xlsx where the user picks, and backing out shows no message.
    @Composable
    override fun rememberExportPicker(onPicked: (write: suspend (ByteArray) -> Boolean) -> Unit): (fileName: String) -> Unit {
        val picked by rememberUpdatedState(onPicked)
        return remember { { name -> picked { bytes -> Files.export(name, bytes) } } }
    }

    @Composable
    override fun rememberNotificationAsk(onDenied: () -> Unit): NotificationAsk {
        val denied by rememberUpdatedState(onDenied)
        return remember { NotificationAsk(allowed = Reminder::allowed, ask = { Reminder.ask { granted -> if (!granted) denied() } }) }
    }
}

private object Files {
    /** The picker keeps only a weak reference to its delegate. */
    private var delegate: PickerDelegate? = null

    /** iOS saves a file that already exists, so the bytes come first and the user then picks where to put a copy. */
    suspend fun export(name: String, bytes: ByteArray): Boolean {
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
        check(bytes.toNSData().writeToURL(file, atomically = true)) { "Couldn't prepare $name" }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { done ->
                show(UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)) { urls -> done.resume(urls.isNotEmpty()) }
            }
        }
    }

    fun show(picker: UIDocumentPickerViewController, onDone: (List<NSURL>) -> Unit) {
        val listener = PickerDelegate { urls ->
            delegate = null
            onDone(urls)
        }
        delegate = listener
        picker.delegate = listener
        IosPlatform.present(picker)
    }
}

private class PickerDelegate(private val done: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) =
        done(didPickDocumentsAtURLs.filterIsInstance<NSURL>())

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = done(emptyList())
}

/** Android's ML Kit document scanner, rebuilt from VisionKit's document camera and Vision's text recognition. */
private object ReceiptCamera {
    /** The camera keeps only a weak reference to its delegate. */
    private var delegate: CameraDelegate? = null

    // To verify on a Mac: the camera opens, and a scanned receipt fills amount, date and shop as on Android.
    fun scan(onStart: () -> Unit, onResult: (ReceiptScan?) -> Unit) {
        if (!VNDocumentCameraViewController.supported) return onResult(null)
        val camera = VNDocumentCameraViewController()
        val listener = CameraDelegate { scan ->
            delegate = null
            camera.dismissViewControllerAnimated(true, completion = null)
            if (scan == null || scan.pageCount == 0uL) onResult(null) else read(scan.imageOfPageAtIndex(0u), onResult)
        }
        delegate = listener
        camera.delegate = listener
        IosPlatform.present(camera)
        onStart()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun read(image: UIImage, onResult: (ReceiptScan?) -> Unit) {
        val cgImage = image.CGImage ?: return onResult(null)
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        var answered = false
        val answer = { scan: ReceiptScan? ->
            if (!answered) {
                answered = true
                dispatch_async(dispatch_get_main_queue()) { onResult(scan) }
            }
        }
        val request = VNRecognizeTextRequest { done, error ->
            val lines = done?.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>().mapNotNull { it.line(width, height) }
            answer(if (error != null) null else ReceiptParser.parse(receiptRows(lines), today()))
        }.apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            recognitionLanguages = listOf("it-IT", "en-US")
            usesLanguageCorrection = true
        }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) {
            if (!VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>()).performRequests(listOf(request), error = null)) answer(null)
        }
    }
}

/** Vision measures from the bottom left, in fractions of the image; the receipt rows want pixels from the top left. */
@OptIn(ExperimentalForeignApi::class)
private fun VNRecognizedTextObservation.line(width: Double, height: Double): OcrLine? {
    val text = (topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string ?: return null
    return boundingBox.useContents {
        OcrLine(text, (origin.x * width).roundToInt(), ((1 - origin.y - size.height) * height).roundToInt(), ((1 - origin.y) * height).roundToInt())
    }
}

private class CameraDelegate(private val done: (VNDocumentCameraScan?) -> Unit) : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {
    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFinishWithScan: VNDocumentCameraScan) =
        done(didFinishWithScan)

    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFailWithError: NSError) = done(null)

    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) = done(null)
}
