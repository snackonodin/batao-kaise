package com.bataokaise.app.core

import android.util.Log
import com.bataokaise.app.intent.Command
import com.bataokaise.app.intent.Intent
import com.bataokaise.app.intent.IntentEngine
import com.bataokaise.app.intent.RuleIntentEngine
import com.bataokaise.app.voice.Normalized
import com.bataokaise.app.voice.Normalizer
import com.bataokaise.app.voice.SpeechEvent
import com.bataokaise.app.voice.SpeechInput
import com.bataokaise.app.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { IDLE, LISTENING, PROCESSING, SPEAKING, GUIDING }

/** The four picture buttons shown after repeated failures. */
enum class Picture(val label: String, val emoji: String) {
    BILL("Bill", "🧾"), UDHAAR("Udhaar", "📒"), YOJANA("Yojana", "🏛️"), MADAD("Phone madad", "🤝")
}

data class UiState(
    val phase: Phase = Phase.IDLE,
    /** Big Hinglish text in the bubble: the line being spoken or last spoken. */
    val bubble: String = "Mic dabaiye aur boliye",
    /** Live transcript while listening. */
    val live: String = "",
    val showYesNo: Boolean = false,
    val showPictures: Boolean = false,
    /** Shown while a journey step waits for the user. */
    val showDone: Boolean = false,
    val level: Float = 0f,
    val voiceLabel: String = "",
    val intentLabel: String = "",
)

/**
 * The core loop (spec section 7):
 * tap mic -> "Batao, kya karna hai?" -> listen -> read back -> Haan/Nahi -> run task.
 * Nothing happens before Haan. Names, amounts and numbers are always repeated back.
 */
class Assistant(
    private val speaker: Speaker,
    voice: SpeechInput,
    var intentEngine: IntentEngine = RuleIntentEngine(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(UiState(voiceLabel = voice.label, intentLabel = intentEngine.label))
    val state: StateFlow<UiState> = _state

    var voice: SpeechInput = voice
        set(v) { field.stop(); field = v; _state.update { it.copy(voiceLabel = v.label) } }

    /** Runs a confirmed task. Set by the app (features live outside the loop). */
    var onTask: (Intent) -> Unit = {}
    /** Called for "peeche" while a journey runs. */
    var onBack: () -> Boolean = { false }
    /** Called for "ho gaya" while a journey runs. */
    var onDone: () -> Unit = {}
    /** Called for "naya kaam" and "ruko" so running journeys can stop. */
    var onReset: () -> Unit = {}
    /** Unknown request or "atak gaya" -> Rescue Circle. */
    var onRescue: (reason: String) -> Unit = {}

    private var answerHandler: ((Normalized, Intent) -> Unit)? = null
    private var lastQuestion: String = ""
    private var lastYesNo = false
    private var noiseCount = 0
    private var silenceCount = 0
    private var noCount = 0
    private var listenStartedAt = 0L
    private var silenceJob: Job? = null
    private var guiding = false

    init {
        speaker.onSpeakingChanged = { s ->
            if (s) _state.update { it.copy(phase = Phase.SPEAKING) }
            else if (_state.value.phase == Phase.SPEAKING) _state.update { it.copy(phase = if (guiding) Phase.GUIDING else Phase.IDLE) }
        }
    }

    // ---------- Public entry points ----------

    /** Floating mic or big mic tapped. Tapping while speaking interrupts (barge-in). */
    fun micTapped() {
        val p = _state.value.phase
        when {
            p == Phase.LISTENING -> { voice.stop(); setPhase(Phase.IDLE) }
            speaker.speaking || answerHandler != null -> { speaker.stop(); listen() }
            guiding -> { speaker.stop(); askGuide() }
            else -> startTask()
        }
    }

    fun startTask() {
        noiseCount = 0; silenceCount = 0; noCount = 0
        _state.update { it.copy(showPictures = false, showYesNo = false, live = "") }
        ask("Batao, kya karna hai?") { n, intent -> onTaskAnswer(n, intent) }
    }

    /** Typed text ("type instead" box, or Wispr Flow dictation into the box). */
    fun submitText(text: String) {
        speaker.stop(); voice.stop(); silenceJob?.cancel()
        if (answerHandler == null) answerHandler = { n, i -> onTaskAnswer(n, i) }
        handleTranscript(text)
    }

    fun yes() = injectIntent(Intent.Yes)
    fun no() = injectIntent(Intent.No)
    fun done() = injectIntent(Intent.Cmd(Command.DONE))

    fun picture(p: Picture) {
        _state.update { it.copy(showPictures = false) }
        val intent = when (p) {
            Picture.BILL -> Intent.BillPay()
            Picture.UDHAAR -> Intent.UdhaarBalance
            Picture.YOJANA -> Intent.Schemes
            Picture.MADAD -> Intent.Stuck
        }
        confirm(intent)
    }

    /** Speak a line with no answer expected. */
    fun say(line: String, then: (() -> Unit)? = null) {
        answerHandler = null
        _state.update { it.copy(bubble = line, showYesNo = false, live = "") }
        speaker.say(line, onDone = then)
    }

    /** Speak a question, then listen. [onAnswer] gets the normalized reply and its detected intent. */
    fun ask(line: String, yesNo: Boolean = false, onAnswer: (Normalized, Intent) -> Unit) {
        lastQuestion = line; lastYesNo = yesNo
        answerHandler = onAnswer
        voice.stop()
        _state.update { it.copy(bubble = line, showYesNo = yesNo, live = "") }
        speaker.say(line) { if (answerHandler === onAnswer) listen() }
    }

    /** Yes/no helper with the standard "Nahi" counting. */
    fun askYesNo(line: String, onYes: () -> Unit, onNo: () -> Unit) {
        ask(line, yesNo = true) { _, i ->
            when (i) {
                Intent.Yes -> { _state.update { it.copy(showYesNo = false) }; onYes() }
                Intent.No -> { _state.update { it.copy(showYesNo = false) }; onNo() }
                else -> reask("Haan ya nahi boliye.")
            }
        }
    }

    /** Journey runner hooks. */
    fun beginGuiding() { guiding = true; _state.update { it.copy(phase = Phase.GUIDING, showDone = true) } }
    fun endGuiding() { guiding = false; _state.update { it.copy(phase = Phase.IDLE, showDone = false) } }
    fun guideSay(line: String) {
        answerHandler = null
        _state.update { it.copy(bubble = line, showYesNo = false, showDone = true) }
        speaker.say(line)
    }

    fun repeatLast() { speaker.repeatSlow { if (answerHandler != null) listen() } }

    fun reset(say: String? = null) {
        speaker.stop(); voice.stop(); silenceJob?.cancel()
        answerHandler = null; guiding = false
        onReset()
        _state.update { UiState(voiceLabel = voice.label, intentLabel = intentEngine.label) }
        if (say != null) say(say)
    }

    // ---------- Listening ----------

    private fun listen() {
        speaker.stop()
        setPhase(Phase.LISTENING)
        _state.update { it.copy(live = "") }
        listenStartedAt = System.currentTimeMillis()
        val handlerAtStart = answerHandler
        voice.start { ev -> if (answerHandler === handlerAtStart) onSpeech(ev) }
    }

    private fun onSpeech(ev: SpeechEvent) {
        when (ev) {
            SpeechEvent.Ready -> {}
            SpeechEvent.SpeechStarted -> { silenceCount = 0 }
            is SpeechEvent.Level -> _state.update { it.copy(level = ev.rms) }
            is SpeechEvent.Partial -> _state.update { it.copy(live = ev.text) }
            is SpeechEvent.Final -> handleTranscript(ev.text)
            SpeechEvent.NoMatch -> onNoise()
            SpeechEvent.Silence -> onSilence()
            is SpeechEvent.Failed -> onVoiceFailed(ev)
        }
    }

    private fun onSilence() {
        silenceCount++
        if (silenceCount == 1) {
            // ~6 s of silence: reassure and keep listening.
            speaker.say("Main sun raha hoon.") { listen() }
            _state.update { it.copy(bubble = "Main sun raha hoon.") }
        } else {
            // ~12 s: stop listening.
            silenceCount = 0
            voice.stop()
            setPhase(if (guiding) Phase.GUIDING else Phase.IDLE)
            _state.update { it.copy(bubble = "Jab taiyaar hon, mic dabaiye.") }
        }
    }

    private fun onNoise() {
        noiseCount++
        if (noiseCount >= 2) {
            noiseCount = 0
            if (answerHandler != null && lastQuestion.startsWith("Batao")) {
                showPictures()
            } else reask(lastQuestion)
        } else {
            val h = answerHandler
            speaker.say("Thoda shor hai. Phone munh ke paas laakar dobara boliye.") { if (answerHandler === h) listen() }
            _state.update { it.copy(bubble = "Thoda shor hai. Phone munh ke paas laakar dobara boliye.") }
        }
    }

    private fun onVoiceFailed(ev: SpeechEvent.Failed) {
        Log.w(TAG, "voice failed: ${ev.reason}")
        setPhase(Phase.IDLE)
        val line = when {
            ev.offline -> "Internet nahi hai. Neeche likh kar bataiye. Udhaar aur bill padhna chalta rahega."
            ev.reason == "mic_permission" -> "Mic ki ijazat nahi hai. Neeche likh kar bataiye."
            ev.reason == "no_recognizer" -> "Is phone mein bolne ki suvidha nahi. Neeche likhiye."
            else -> "Sun nahi paaya. Mic phir se dabaiye."
        }
        _state.update { it.copy(bubble = line) }
        speaker.say(line)
    }

    private fun showPictures() {
        val line = "Neeche tasveer dabaiye: Bill, Udhaar, Yojana ya Phone madad."
        _state.update { it.copy(showPictures = true, bubble = line, showYesNo = false) }
        answerHandler = { n, i -> onTaskAnswer(n, i) }
        speaker.say(line)
    }

    private fun injectIntent(i: Intent) {
        speaker.stop(); voice.stop()
        val h = answerHandler
        when {
            h != null -> h(Normalizer.normalize(i.key), i)
            i == Intent.Cmd(Command.DONE) && guiding -> onDone()
        }
    }

    fun handleTranscript(text: String) {
        setPhase(Phase.PROCESSING)
        _state.update { it.copy(live = text) }
        val n = Normalizer.normalize(text)
        scope.launch {
            val intent = runCatching { intentEngine.detect(n) }.getOrElse { RuleIntentEngine().detectSync(n) }
            Log.i(TAG, "heard='$text' latin='${n.latin}' -> ${intent.describe()}")
            if (handleGlobal(intent)) return@launch
            val h = answerHandler
            if (h == null) { if (guiding) onGuideAnswer(intent) else onTaskAnswer(n, intent); return@launch }
            if (intent == Intent.Noise && !lastYesNo) { onNoise(); return@launch }
            noiseCount = 0
            h(n, intent)
        }
    }

    /** "ruko", "phir se", "peeche", "naya kaam" work anywhere. */
    private fun handleGlobal(i: Intent): Boolean {
        if (i !is Intent.Cmd) return false
        when (i.command) {
            Command.STOP -> { reset(); _state.update { it.copy(bubble = "Theek hai, ruk gaya.") }; speaker.say("Theek hai, ruk gaya.") }
            Command.REPEAT -> repeatLast()
            Command.BACK -> if (!onBack()) reask(lastQuestion.ifEmpty { "Batao, kya karna hai?" })
            Command.NEW_TASK -> { reset(); startTask() }
            Command.DONE -> if (guiding) onDone() else return false
        }
        return true
    }

    private fun reask(line: String) {
        val h = answerHandler ?: { n, i -> onTaskAnswer(n, i) }
        ask(line, lastYesNo, h)
    }

    private fun askGuide() {
        answerHandler = null
        listen()
    }

    private fun onGuideAnswer(i: Intent) {
        when (i) {
            Intent.Stuck -> onRescue("atak_gaya")
            Intent.Yes -> onDone()
            else -> guideSay(speaker.lastLine)
        }
    }

    // ---------- Task dialogue ----------

    private fun onTaskAnswer(n: Normalized, intent: Intent) {
        when (intent) {
            Intent.Noise -> onNoise()
            Intent.Unknown -> {
                answerHandler = null
                say("Ye main abhi nahi seekha. Kisi apne se madad maangein?") { onRescue("unknown:${n.raw}") }
            }
            Intent.Stuck -> confirm(intent)
            Intent.Yes, Intent.No -> reask("Batao, kya karna hai?")
            is Intent.UdhaarAdd -> fillUdhaar(intent.name, intent.amount) { name, amt -> confirm(Intent.UdhaarAdd(name, amt)) }
            is Intent.UdhaarRepay -> fillUdhaar(intent.name, intent.amount) { name, amt -> confirm(Intent.UdhaarRepay(name, amt)) }
            is Intent.UdhaarRemind -> if (intent.name == null) askName { confirm(Intent.UdhaarRemind(it)) } else confirm(intent)
            else -> confirm(intent)
        }
    }

    /** Misheard name or amount: ask for just the name, then just the amount. */
    private fun fillUdhaar(name: String?, amount: Long?, done: (String, Long) -> Unit) {
        when {
            name == null -> askName { fillUdhaar(it, amount, done) }
            amount == null -> askAmount { fillUdhaar(name, it, done) }
            else -> done(name, amount)
        }
    }

    fun askName(onName: (String) -> Unit) {
        ask("Sirf naam boliye.") { n, _ ->
            val name = RuleIntentEngine.extractName(n)
            if (name == null) reask("Naam samajh nahi aaya. Sirf naam boliye.") else onName(name)
        }
    }

    fun askAmount(onAmount: (Long) -> Unit) {
        ask("Kitne rupaye? Sirf rakam boliye.") { n, _ ->
            val amt = n.amounts.firstOrNull { it > 0 }
            if (amt == null) reask("Rakam samajh nahi aayi. Jaise: do sau.") else onAmount(amt)
        }
    }

    /** Read back and wait for Haan. Amounts over 2,000 get a second check. */
    fun confirm(intent: Intent) {
        val line = intent.readBack() ?: run { onTask(intent); return }
        ask(line, yesNo = true) { _, ans ->
            when (ans) {
                Intent.Yes -> {
                    _state.update { it.copy(showYesNo = false) }
                    val amt = (intent as? Intent.UdhaarAdd)?.amount ?: (intent as? Intent.UdhaarRepay)?.amount
                    if (amt != null && amt > 2000) {
                        askYesNo("Do hazaar se zyada hai. Pakka?", onYes = { onTask(intent) }, onNo = { afterNo() })
                    } else onTask(intent)
                }
                Intent.No -> afterNo()
                else -> {
                    // A fresh request instead of yes/no: treat it as a correction.
                    if (ans !is Intent.Noise && ans != Intent.Unknown) onTaskAnswer(Normalizer.normalize(""), ans)
                    else reask("Haan ya nahi boliye. $line")
                }
            }
        }
    }

    private fun afterNo() {
        noCount++
        _state.update { it.copy(showYesNo = false) }
        if (noCount >= 2) { noCount = 0; showPictures() }
        else ask("Achha, phir se bataiye.") { n, i -> onTaskAnswer(n, i) }
    }

    private fun setPhase(p: Phase) = _state.update { it.copy(phase = p) }

    companion object { const val TAG = "BataoAssistant" }
}
