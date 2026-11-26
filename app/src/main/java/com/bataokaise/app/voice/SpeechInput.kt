package com.bataokaise.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Events every speech engine emits. Raw audio is never stored by any engine. */
sealed class SpeechEvent {
    data object Ready : SpeechEvent()
    data object SpeechStarted : SpeechEvent()
    data class Level(val rms: Float) : SpeechEvent()
    data class Partial(val text: String) : SpeechEvent()
    data class Final(val text: String) : SpeechEvent()
    /** Nothing was said before the engine gave up. */
    data object Silence : SpeechEvent()
    /** Something was heard but not understood (noise, mumbling). */
    data object NoMatch : SpeechEvent()
    data class Failed(val reason: String, val offline: Boolean = false) : SpeechEvent()
}

interface SpeechInput {
    /** Shown in Settings, e.g. "Voice: Android (demo)". */
    val label: String
    val isWispr: Boolean
    fun start(onEvent: (SpeechEvent) -> Unit)
    fun stop()
    fun release() {}
}

/** Android SpeechRecognizer, hi-IN. The default engine; labelled "not Wispr". */
class AndroidSpeechInput(private val context: Context) : SpeechInput {
    override val label = "Voice: Android (demo, not Wispr)"
    override val isWispr = false
    private var recognizer: SpeechRecognizer? = null
    private var heardSpeech = false

    fun available() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(onEvent: (SpeechEvent) -> Unit) {
        stop()
        if (!available()) { onEvent(SpeechEvent.Failed("no_recognizer")); return }
        heardSpeech = false
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(SpeechEvent.Ready)
            override fun onBeginningOfSpeech() { heardSpeech = true; onEvent(SpeechEvent.SpeechStarted) }
            override fun onRmsChanged(rmsdB: Float) = onEvent(SpeechEvent.Level(rmsdB))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { onEvent(SpeechEvent.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onEvent(if (text.isBlank()) SpeechEvent.NoMatch else SpeechEvent.Final(text))
            }
            override fun onError(error: Int) {
                onEvent(when (error) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechEvent.Silence
                    SpeechRecognizer.ERROR_NO_MATCH -> if (heardSpeech) SpeechEvent.NoMatch else SpeechEvent.Silence
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> SpeechEvent.Failed("network", offline = true)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechEvent.Failed("mic_permission")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechEvent.Failed("busy")
                    else -> SpeechEvent.Failed("error_$error")
                })
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Stop ~1.5 s after speech ends. Recognizers treat these as hints.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        r.startListening(intent)
    }

    override fun stop() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }

    override fun release() = stop()
}

/**
 * Text-box engine. Two uses:
 *  1. "Wispr Flow app dictation": the user dictates into our focused text box with the installed Flow app.
 *  2. "Type instead" for noisy places, and for testing on emulators without a microphone.
 * The UI calls [submit] when the box is sent.
 */
class TextBoxInput(override val label: String, override val isWispr: Boolean) : SpeechInput {
    private var sink: ((SpeechEvent) -> Unit)? = null
    override fun start(onEvent: (SpeechEvent) -> Unit) { sink = onEvent; onEvent(SpeechEvent.Ready) }
    override fun stop() { sink = null }
    fun submit(text: String) {
        val s = sink
        if (text.isBlank()) s?.invoke(SpeechEvent.NoMatch) else s?.invoke(SpeechEvent.Final(text.trim()))
    }
}
