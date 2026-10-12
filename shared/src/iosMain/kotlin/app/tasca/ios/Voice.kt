@file:OptIn(ExperimentalForeignApi::class)

package app.tasca.ios

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Voice entry: Android's RecognizerIntent in Italian, rebuilt on SFSpeechRecognizer with a small listening dialog.
 * It stops by itself after a pause, like Google's dialog, and hands over what it heard.
 */
internal object Voice {
    private const val PAUSE_MS = 1_500L
    private const val NOTHING_SAID_MS = 8_000L
    private val scope = MainScope()
    /** What was heard so far, shown in the dialog; null when not listening. */
    private var heard by mutableStateOf<String?>(null)
    private var finish: ((deliver: Boolean) -> Unit)? = null

    // To verify on a Mac: "3 euro e 50 caffè" opens the keypad with 3,50 € in Caffè; a refused permission opens the empty keypad.
    fun listen(onHeard: (String) -> Unit, onUnavailable: () -> Unit) {
        if (heard != null) return
        SFSpeechRecognizer.requestAuthorization { status ->
            onMain {
                if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) return@onMain onUnavailable()
                AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                    onMain { if (granted) start(onHeard, onUnavailable) else onUnavailable() }
                }
            }
        }
    }

    private fun start(onHeard: (String) -> Unit, onUnavailable: () -> Unit) {
        val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "it-IT"))
        if (!recognizer.isAvailable()) return onUnavailable()
        val audio = AVAudioSession.sharedInstance()
        audio.setCategory(AVAudioSessionCategoryRecord, mode = AVAudioSessionModeMeasurement, options = AVAudioSessionCategoryOptionDuckOthers, error = null)
        audio.setActive(true, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        val engine = AVAudioEngine()
        val request = SFSpeechAudioBufferRecognitionRequest().apply { shouldReportPartialResults = true }
        val input = engine.inputNode
        input.installTapOnBus(0u, bufferSize = 1024u, format = input.outputFormatForBus(0u)) { buffer, _ ->
            buffer?.let { request.appendAudioPCMBuffer(it) }
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            return onUnavailable()
        }

        var task: SFSpeechRecognitionTask? = null
        var timer: Job? = null
        val done: (Boolean) -> Unit = { deliver ->
            heard?.let { text ->
                timer?.cancel()
                engine.stop()
                input.removeTapOnBus(0u)
                request.endAudio()
                task?.cancel()
                audio.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
                heard = null
                finish = null
                if (deliver && text.isNotBlank()) onHeard(text.trim())
            }
        }
        fun stopAfter(ms: Long) {
            timer?.cancel()
            timer = scope.launch {
                delay(ms)
                done(true)
            }
        }
        heard = ""
        finish = done
        stopAfter(NOTHING_SAID_MS)
        task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            onMain {
                if (heard == null) return@onMain
                result?.let { heard = it.bestTranscription.formattedString }
                if (error != null || result?.isFinal() == true) done(true) else stopAfter(PAUSE_MS)
            }
        }
    }

    @Composable
    fun Dialog() {
        val text = heard ?: return
        AlertDialog(
            onDismissRequest = { finish?.invoke(false) },
            title = { Text("Listening…") },
            text = { Text(text.ifEmpty { "Es. \"15 euro pranzo\"" }) },
            confirmButton = { TextButton(onClick = { finish?.invoke(true) }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { finish?.invoke(false) }) { Text("Cancel") } },
        )
    }

    private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)
}
