package com.bataokaise.app.journey

import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.bataokaise.app.BataoApp
import com.bataokaise.app.core.Assistant
import com.bataokaise.app.guide.Decision
import com.bataokaise.app.guide.GuideBus
import com.bataokaise.app.guide.GuideLogic
import com.bataokaise.app.guide.NodeData

/** What the Rescue Circle needs to describe a stuck task (no screenshots). */
data class JourneyState(
    val goal: String, val app: String, val screen: String, val failedStep: String, val attempts: Int, val stepsDone: Int,
)

/**
 * Runs one journey: speaks each step, highlights the target via the guide service,
 * advances automatically when the next expected screen appears, and falls back to
 * spoken-only steps ("Ho gaya?") when the guide is off or the element is not found.
 */
class JourneyRunner(private val app: BataoApp, private val a: Assistant) {
    enum class Mode { SCREEN, SPOKEN }

    private val main = Handler(Looper.getMainLooper())
    var journey: Journey? = null; private set
    var index = 0; private set
    var mode = Mode.SPOKEN; private set
    private var vars: Map<String, String> = emptyMap()
    private var spokenIndex = -1
    private var lastKind: String = ""
    private var notFoundSince = 0L
    private val failSpoken = mutableSetOf<Int>()
    private val failCount = mutableMapOf<Int, Int>()
    private var rescueOffered = false
    private var gotSnapshot = false
    var onFinished: (Journey) -> Unit = {}
    var onRescue: (JourneyState) -> Unit = {}
    val running get() = journey != null

    fun start(j: Journey, variables: Map<String, String>, launch: Boolean = true) {
        stop(silent = true)
        val launchIntent = if (launch) app.packageManager.getLaunchIntentForPackage(j.appPackage) else null
        if (launch && launchIntent == null) {
            a.say("Ye app phone mein nahi hai.")
            return
        }
        journey = j; index = 0; vars = variables; spokenIndex = -1; lastKind = ""
        failSpoken.clear(); failCount.clear(); rescueOffered = false; gotSnapshot = false
        mode = if (GuideBus.service != null && !app.settings.a11yRefused) Mode.SCREEN else Mode.SPOKEN
        a.beginGuiding()
        a.onDone = ::onUserDone
        a.onBack = ::onUserBack
        a.onReset = { stop(silent = true) }
        if (mode == Mode.SCREEN) {
            GuideBus.listener = ::onScreen
            GuideBus.active = true
        }
        launchIntent?.let { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Log.i(TAG, "start ${j.id} mode=$mode")
        if (mode == Mode.SPOKEN) main.postDelayed({ speakStep(0) }, 900)
        else {
            main.postDelayed({ GuideBus.service?.requestSnapshot() }, 700)
            // If no snapshot arrives (e.g. service stalled), fall back to spoken steps.
            main.postDelayed({ if (running && !gotSnapshot) { mode = Mode.SPOKEN; speakStep(index) } }, 3500)
        }
    }

    fun stop(silent: Boolean = false) {
        if (journey == null) return
        GuideBus.active = false
        GuideBus.listener = null
        GuideBus.service?.removeOverlay()
        journey = null
        main.removeCallbacksAndMessages(null)
        a.onDone = {}; a.onBack = { false }; a.onReset = {}
        a.endGuiding()
        if (!silent) a.say("Theek hai, band kar diya.")
    }

    fun state(): JourneyState? {
        val j = journey ?: return null
        val s = j.steps[index]
        return JourneyState(
            goal = j.title, app = j.appPackage, screen = s.id, failedStep = fill(s.say),
            attempts = (failCount[index] ?: 0) + 1, stepsDone = index,
        )
    }

    private fun fill(s: String) = JourneyMatcher.fill(s, vars)

    private fun speakStep(i: Int) {
        val j = journey ?: return
        index = i.coerceIn(0, j.steps.lastIndex)
        spokenIndex = index
        val step = j.steps[index]
        a.guideSay(fill(step.say))
        if (step.final) main.postDelayed({ finish() }, 2500)
    }

    private fun finish() {
        val j = journey ?: return
        stop(silent = true)
        onFinished(j)
    }

    // ---------- Screen mode ----------

    private fun onScreen(pkg: String, nodes: List<NodeData>) {
        val j = journey ?: return
        gotSnapshot = true
        val d = GuideLogic.evaluate(j, index, pkg, nodes, vars)
        val svc = GuideBus.service
        val showLabel = !Settings.canDrawOverlays(app)
        when (d) {
            Decision.Ignore -> {}
            is Decision.Show -> {
                lastKind = "show"
                if (d.index != spokenIndex) { notFoundSince = 0; speakStep(d.index) }
                index = d.index
                val t = d.target
                if (t != null) {
                    notFoundSince = 0
                    svc?.showHighlight(Rect(t.bounds[0], t.bounds[1], t.bounds[2], t.bounds[3]), if (showLabel) fill(j.steps[index].say) else null)
                } else {
                    svc?.hideHighlight()
                    val now = System.currentTimeMillis()
                    if (notFoundSince == 0L) notFoundSince = now
                    else if (now - notFoundSince > 2000 && index !in failSpoken) {
                        // Element not found: spoken steps only for this step.
                        failSpoken.add(index); bumpFail()
                        j.steps[index].onFail.takeIf { it.isNotBlank() }?.let { a.guideSay(fill(it)) }
                    }
                    main.postDelayed({ GuideBus.service?.requestSnapshot() }, 1200)
                }
            }
            is Decision.Sensitive -> {
                svc?.hideHighlight()
                if (d.index != spokenIndex) speakStep(d.index)
                else if (lastKind != "sensitive") a.guideSay("Ye aap khud daaliye.")
                index = d.index; lastKind = "sensitive"
            }
            is Decision.WrongScreen -> {
                val back = d.back
                if (back != null) svc?.showHighlight(Rect(back.bounds[0], back.bounds[1], back.bounds[2], back.bounds[3]), if (showLabel) "Peeche" else null)
                else svc?.hideHighlight()
                if (lastKind != "wrong") {
                    lastKind = "wrong"; bumpFail()
                    a.guideSay("Ye galat screen hai, peeche jaaiye.")
                    // Speak the step again once they are back.
                    spokenIndex = -1
                }
            }
            is Decision.Finished -> {
                svc?.hideHighlight()
                if (lastKind != "finished") { lastKind = "finished"; speakStep(d.index) }
            }
        }
    }

    private fun bumpFail() {
        val c = (failCount[index] ?: 0) + 1
        failCount[index] = c
        if (c >= 2 && !rescueOffered) {
            rescueOffered = true
            state()?.let { st -> main.postDelayed({ if (running) onRescue(st) }, 2500) }
        }
    }

    // ---------- Voice commands while guiding ----------

    private fun onUserDone() {
        val j = journey ?: return
        if (mode == Mode.SPOKEN) {
            if (index >= j.steps.lastIndex) finish() else speakStep(index + 1)
        } else {
            // Screen mode advances by itself; re-check and repeat the current step if nothing changed.
            GuideBus.service?.requestSnapshot()
            main.postDelayed({ if (running && spokenIndex == index) a.guideSay("Abhi ye baaki hai: " + fill(j.steps[index].say)) }, 900)
        }
    }

    private fun onUserBack(): Boolean {
        if (journey == null) return false
        if (mode == Mode.SPOKEN) speakStep(index - 1)
        else a.guideSay("Phone ka Back button dabaiye.")
        return true
    }

    companion object { const val TAG = "JourneyRunner" }
}
