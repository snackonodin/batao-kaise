package com.bataokaise.app.journey

import android.content.Context
import com.bataokaise.app.voice.Normalized
import com.bataokaise.app.voice.Normalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Journeys are versioned JSON (assets/journeys/). They are the single source of every
 * instruction the app speaks while guiding. The LLM never writes them.
 */
@Serializable
data class Journey(
    val id: String,
    val version: Int,
    val title: String,
    val intent: String? = null,
    val triggers: List<String> = emptyList(),
    @SerialName("app_package") val appPackage: String,
    @SerialName("reviewed_by") val reviewedBy: String,
    @SerialName("review_date") val reviewDate: String,
    /** "verified" (reviewed by a person against the real app) or "demo". */
    val status: String,
    val steps: List<Step>,
)

@Serializable
data class Step(
    val id: String,
    val say: String,
    @SerialName("expect_screen") val expectScreen: ScreenMarker,
    val target: Target? = null,
    @SerialName("action_hint") val actionHint: String? = null,
    @SerialName("on_fail") val onFail: String = "",
    /** Hide the overlay: the user types secrets (OTP, PIN, password) by themselves. */
    val sensitive: Boolean = false,
    val final: Boolean = false,
    /** Advance within the same screen once this element appears. */
    @SerialName("done_when") val doneWhen: Target? = null,
    /** Advance once the target field has text in it. */
    @SerialName("done_when_filled") val doneWhenFilled: Boolean = false,
    /** If the target vanishes from the same screen (e.g. after Send), go to the next step. */
    @SerialName("advance_on_missing_target") val advanceOnMissingTarget: Boolean = false,
)

@Serializable
data class ScreenMarker(
    val `package`: String,
    /** View resource id that must be present. */
    val marker: String? = null,
    @SerialName("marker_text_any") val markerTextAny: List<String> = emptyList(),
    @SerialName("marker_desc_any") val markerDescAny: List<String> = emptyList(),
)

/** Target element: view id first, then text, then hint, then content description. */
@Serializable
data class Target(
    @SerialName("view_id") val viewId: String? = null,
    val text: String? = null,
    val hint: String? = null,
    val desc: String? = null,
)

object JourneyRepo {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: List<Journey>? = null

    fun parse(text: String): Journey = json.decodeFromString(Journey.serializer(), text)

    fun all(context: Context): List<Journey> = cache ?: synchronized(this) {
        cache ?: context.assets.list("journeys").orEmpty().filter { it.endsWith(".json") }
            .map { f -> parse(context.assets.open("journeys/$f").bufferedReader().use { it.readText() }) }
            .also { cache = it }
    }

    fun byId(context: Context, id: String) = all(context).firstOrNull { it.id == id }
}

object JourneyMatcher {
    /**
     * Pick the journey for an intent key, else the best trigger-phrase match.
     * Trigger match = share of a trigger's canonical tokens present in the utterance (>= 0.67).
     */
    fun match(journeys: List<Journey>, intentKey: String?, n: Normalized?): Journey? {
        if (intentKey != null) journeys.firstOrNull { it.intent == intentKey }?.let { return it }
        if (n == null) return null
        val heard = n.canon.toSet()
        var best: Journey? = null
        var bestScore = 0.0
        for (j in journeys) for (t in j.triggers) {
            val toks = Normalizer.normalize(t).canon
            if (toks.isEmpty()) continue
            val score = toks.count { it in heard }.toDouble() / toks.size
            if (score > bestScore) { bestScore = score; best = j }
        }
        return if (bestScore >= 0.67) best else null
    }

    /** Fill {placeholders} in spoken lines and targets. */
    fun fill(template: String, vars: Map<String, String>): String =
        Regex("\\{(\\w+)\\}").replace(template) { m -> vars[m.groupValues[1]] ?: m.value }
}
