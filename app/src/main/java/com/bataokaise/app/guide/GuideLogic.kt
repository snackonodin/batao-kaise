package com.bataokaise.app.guide

import com.bataokaise.app.journey.Journey
import com.bataokaise.app.journey.JourneyMatcher
import com.bataokaise.app.journey.ScreenMarker
import com.bataokaise.app.journey.Step
import com.bataokaise.app.journey.Target

/** A flattened, Android-free copy of one accessibility node. */
data class NodeData(
    val viewId: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val hint: String? = null,
    val password: Boolean = false,
    val editable: Boolean = false,
    val visible: Boolean = true,
    val clickable: Boolean = false,
    /** left, top, right, bottom in screen pixels. */
    val bounds: IntArray = IntArray(4),
) {
    val shortId: String? get() = viewId?.substringAfter(":id/")
}

sealed class Decision {
    /** Point at [target] (null = not found, speak only) for step [index]. */
    data class Show(val index: Int, val target: NodeData?) : Decision()
    /** OTP / PIN / password on screen: hide overlay, the user types it. */
    data class Sensitive(val index: Int) : Decision()
    /** User is somewhere the journey does not expect. [back] = a Back button to point at, if any. */
    data class WrongScreen(val index: Int, val back: NodeData?) : Decision()
    data class Finished(val index: Int) : Decision()
    /** Transient system window (keyboard, share sheet, our own bubble): keep state, change nothing. */
    data object Ignore : Decision()
}

object GuideLogic {
    /** Packages that may appear mid-journey and must not count as "wrong screen". */
    val transientPackages = setOf(
        "com.bataokaise.app", "android", "com.android.systemui", "com.android.intentresolver",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard", "com.android.chrome.hub"
    )

    private val secretWords = Regex("otp|one.?time|\\bpin\\b|upi pin|password|passcode|cvv|mpin|पिन|पासवर्ड", RegexOption.IGNORE_CASE)

    fun screenMatches(m: ScreenMarker, pkg: String, nodes: List<NodeData>): Boolean {
        if (pkg != m.`package`) return false
        m.marker?.let { id -> if (nodes.none { it.shortId == id || it.viewId == id }) return false }
        if (m.markerTextAny.isNotEmpty() && m.markerTextAny.none { t -> nodes.any { it.text?.contains(t, ignoreCase = true) == true } }) return false
        if (m.markerDescAny.isNotEmpty() && m.markerDescAny.none { d -> nodes.any { it.desc?.contains(d, ignoreCase = true) == true } }) return false
        return true
    }

    /** View id, then exact text, then text contains, then hint, then content description. */
    fun findTarget(t: Target?, nodes: List<NodeData>, vars: Map<String, String> = emptyMap()): NodeData? {
        if (t == null) return null
        val vis = nodes.filter { it.visible && it.bounds[2] > it.bounds[0] && it.bounds[3] > it.bounds[1] }
        val f = { s: String? -> s?.let { JourneyMatcher.fill(it, vars) } }
        f(t.viewId)?.let { id -> vis.firstOrNull { it.viewId == id || it.shortId == id || it.shortId == id.substringAfter(":id/") }?.let { return it } }
        f(t.text)?.let { tx ->
            vis.firstOrNull { it.text.equals(tx, ignoreCase = true) }?.let { return it }
            vis.firstOrNull { it.text?.contains(tx, ignoreCase = true) == true }?.let { return it }
        }
        f(t.hint)?.let { h -> vis.firstOrNull { it.hint?.contains(h, ignoreCase = true) == true }?.let { return it } }
        f(t.desc)?.let { d ->
            vis.firstOrNull { it.desc.equals(d, ignoreCase = true) }?.let { return it }
            vis.firstOrNull { it.desc?.contains(d, ignoreCase = true) == true }?.let { return it }
        }
        return null
    }

    fun hasSecretField(nodes: List<NodeData>): Boolean = nodes.any { n ->
        n.visible && (n.password || (n.editable && listOfNotNull(n.viewId, n.hint, n.desc, n.text).any { secretWords.containsMatchIn(it) }))
    }

    fun findBack(nodes: List<NodeData>): NodeData? = nodes.firstOrNull { n ->
        n.visible && (n.shortId == "back_btn" || n.desc in setOf("Navigate up", "Back", "पीछे", "Go back") ||
            n.text?.contains("Back", ignoreCase = true) == true && n.text.length < 12)
    }

    private fun isFilled(n: NodeData?): Boolean {
        if (n == null) return false
        val t = n.text?.trim().orEmpty()
        return t.isNotEmpty() && t != n.hint?.trim()
    }

    /** Decide which step applies to the screen now showing, starting from step [current]. */
    fun evaluate(j: Journey, current: Int, pkg: String, nodes: List<NodeData>, vars: Map<String, String>): Decision {
        if (pkg in transientPackages) return Decision.Ignore
        val steps = j.steps
        var idx = when {
            screenMatches(steps[current].expectScreen, pkg, nodes) -> current
            else -> (current + 1 until steps.size).firstOrNull { screenMatches(steps[it].expectScreen, pkg, nodes) }
                ?: (current - 1 downTo 0).firstOrNull { screenMatches(steps[it].expectScreen, pkg, nodes) }
                ?: return Decision.WrongScreen(current, findBack(nodes))
        }
        // Advance within one screen (several steps can share a screen).
        while (idx + 1 < steps.size && advanceWithinScreen(steps[idx], nodes, vars) &&
            screenMatches(steps[idx + 1].expectScreen, pkg, nodes)) idx++
        val step = steps[idx]
        if (step.final) return Decision.Finished(idx)
        if (step.sensitive || hasSecretField(nodes)) return Decision.Sensitive(idx)
        return Decision.Show(idx, findTarget(step.target, nodes, vars))
    }

    private fun advanceWithinScreen(s: Step, nodes: List<NodeData>, vars: Map<String, String>): Boolean = when {
        s.doneWhen != null -> findTarget(s.doneWhen, nodes, vars) != null
        s.doneWhenFilled -> isFilled(findTarget(s.target, nodes, vars))
        s.advanceOnMissingTarget -> s.target != null && findTarget(s.target, nodes, vars) == null
        else -> false
    }
}
