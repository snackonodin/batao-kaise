package com.bataokaise.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.UUID

/**
 * Text-to-speech, hi-IN at 0.9 speed. "Phir se" repeats the last line slower (0.7).
 * Callbacks arrive on the main thread.
 */
class Speaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableMapOf<String, () -> Unit>()
    private var ready = false
    private val queue = mutableListOf<Pair<String, (() -> Unit)?>>()
    var lastLine: String = ""
        private set
    var hindiAvailable = false
        private set
    var speaking = false
        private set
    var onSpeakingChanged: (Boolean) -> Unit = {}

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val r = tts.setLanguage(Locale("hi", "IN"))
            hindiAvailable = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (!hindiAvailable) tts.setLanguage(Locale("en", "IN"))
            tts.setSpeechRate(0.9f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.post { setSpeaking(true) } }
                override fun onDone(id: String?) { main.post { finish(id) } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { finish(id) } }
                override fun onStop(id: String?, interrupted: Boolean) { main.post { callbacks.remove(id); setSpeaking(false) } }
            })
            val pending = queue.toList(); queue.clear()
            pending.forEach { (t, cb) -> say(t, onDone = cb) }
        }
    }

    private fun setSpeaking(v: Boolean) { if (speaking != v) { speaking = v; onSpeakingChanged(v) } }

    private fun finish(id: String?) {
        setSpeaking(false)
        callbacks.remove(id)?.invoke()
    }

    /** Speak one line (keep it under 15 words). [onDone] runs when it finishes or fails. */
    fun say(text: String, rate: Float = 0.9f, onDone: (() -> Unit)? = null) {
        lastLine = text
        if (!ready) { queue.add(text to onDone); return }
        val id = UUID.randomUUID().toString()
        if (onDone != null) callbacks[id] = onDone
        tts.setSpeechRate(rate)
        val r = tts.speak(spoken(text), TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (r != TextToSpeech.SUCCESS) finish(id)
    }

    /** "Phir se": the last line again, slower. */
    fun repeatSlow(onDone: (() -> Unit)? = null) { if (lastLine.isNotEmpty()) say(lastLine, 0.7f, onDone) }

    /** Stop talking immediately (barge-in, "ruko"). Pending callbacks are dropped. */
    fun stop() { callbacks.clear(); if (ready) tts.stop(); setSpeaking(false) }

    fun shutdown() { tts.shutdown() }

    companion object {
        /** Digits are read one by one ("4-5-1-2" -> "4 5 1 2") so TTS does not say "four thousand...". */
        fun spoken(text: String): String = text.replace(Regex("(\\d)-(?=\\d)"), "$1 ").replace("₹", " rupaye ")

        /** "451277883301" -> "4-5-1-2-7-7-8-8-3-3-0-1" for display and digit-by-digit speech. */
        fun digits(s: String): String = s.filter(Char::isDigit).toList().joinToString("-")
    }
}
