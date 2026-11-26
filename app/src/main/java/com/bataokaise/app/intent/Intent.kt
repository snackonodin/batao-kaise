package com.bataokaise.app.intent

import com.bataokaise.app.voice.NumberParser
import com.bataokaise.app.voice.Normalized
import com.bataokaise.app.voice.Normalizer

enum class Command { STOP, REPEAT, BACK, NEW_TASK, DONE }

/** What the user wants. The LLM engine (optional) can only produce one of these, never steps. */
sealed class Intent(val key: String) {
    data class BillPay(val kind: String = "bijli") : Intent("bill_pay")
    data class UdhaarAdd(val name: String?, val amount: Long?) : Intent("udhaar_add")
    data class UdhaarRepay(val name: String?, val amount: Long?) : Intent("udhaar_repay")
    data object UdhaarBalance : Intent("udhaar_balance")
    data class UdhaarRemind(val name: String?) : Intent("udhaar_remind")
    data object Schemes : Intent("schemes")
    data object Stuck : Intent("stuck")
    data object Yes : Intent("yes")
    data object No : Intent("no")
    data class Cmd(val command: Command) : Intent("cmd_" + command.name.lowercase())
    data object Noise : Intent("noise")
    data object Unknown : Intent("unknown")

    /** The read-back line, under 15 words. Null for intents that need no confirmation. */
    fun readBack(): String? = when (this) {
        is BillPay -> "Aap bijli ka bill bharna chahte hain. Sahi?"
        is UdhaarAdd -> if (name != null && amount != null) "$name, $amount rupaye udhaar. Sahi?" else null
        is UdhaarRepay -> if (name != null && amount != null) "$name ne $amount rupaye diye. Sahi?" else null
        UdhaarBalance -> "Aap sabka baaki hisaab sunna chahte hain. Sahi?"
        is UdhaarRemind -> if (name != null) "Aap $name ko paise ki yaad dilana chahte hain. Sahi?" else null
        Schemes -> "Aap sarkari yojana dhoondhna chahte hain. Sahi?"
        Stuck -> "Aap atak gaye hain. Kisi apne se madad lein?"
        else -> null
    }

    /** Short English label for the debug screen and logs. */
    fun describe(): String = when (this) {
        is UdhaarAdd -> "udhaar_add(${name ?: "?"}, ${amount ?: "?"})"
        is UdhaarRepay -> "udhaar_repay(${name ?: "?"}, ${amount ?: "?"})"
        is UdhaarRemind -> "udhaar_remind(${name ?: "?"})"
        else -> key
    }
}

interface IntentEngine {
    val label: String
    suspend fun detect(n: Normalized): Intent
}

/** Default engine: keyword and regex rules. Works offline and needs no keys. */
class RuleIntentEngine : IntentEngine {
    override val label = "Rules (demo)"
    override suspend fun detect(n: Normalized): Intent = detectSync(n)

    fun detectSync(n: Normalized): Intent {
        if (n.tokens.isEmpty()) return Intent.Noise
        val c = n.canon
        // Global commands and yes/no are only taken from short utterances,
        // so "nahi pata Ramesh ko kitna diya" is not read as a plain "Nahi".
        if (c.size <= 3) {
            when {
                "ruko" in c -> return Intent.Cmd(Command.STOP)
                "phirse" in c -> return Intent.Cmd(Command.REPEAT)
                "peeche" in c -> return Intent.Cmd(Command.BACK)
                n.has("naya", "kaam") -> return Intent.Cmd(Command.NEW_TASK)
                n.has("ho", "gaya") -> return Intent.Cmd(Command.DONE)
                "nahi" in c -> return Intent.No
                "haan" in c -> return Intent.Yes
            }
        }
        if ("atak" in c || n.hasAny("madad", "help") && !n.hasAny("udhaar", "bill", "yojana")) return Intent.Stuck
        if (n.wordCount < 2) return Intent.Noise

        val udhaarish = "udhaar" in c || ("paisa" in c && n.hasAny("ko", "ne", "se") && extractName(n) != null)
        if (udhaarish || "yaad" in c) {
            val remind = "yaad" in c || (n.has("kaise") && n.hasAny("loon", "maangu")) || "loon" in c
            if (remind) return Intent.UdhaarRemind(extractName(n, "se") ?: extractName(n, "ko") ?: extractName(n))
            if (n.hasAny("kiska", "kitna") || n.hasAny("hisaab", "hisab", "list", "sab", "sabka")) {
                if (extractName(n) == null || n.hasAny("kiska", "sabka", "sab")) return Intent.UdhaarBalance
            }
            if ("ne" in c && "diye" in c) return Intent.UdhaarRepay(extractName(n, "ne"), firstAmount(n))
            return Intent.UdhaarAdd(extractName(n, "ko") ?: extractName(n), firstAmount(n))
        }
        // "Ramesh ne 100 de diye" without the word udhaar is still a repayment.
        if ("ne" in c && "diye" in c && extractName(n, "ne") != null && firstAmount(n) != null)
            return Intent.UdhaarRepay(extractName(n, "ne"), firstAmount(n))
        if (n.hasAny("bill", "bijli")) return Intent.BillPay()
        if ("yojana" in c) return Intent.Schemes
        return Intent.Unknown
    }

    private fun firstAmount(n: Normalized): Long? = n.amounts.firstOrNull { it in 1..10_000_000 }

    companion object {
        private val stop = setOf(
            "mera", "mere", "meri", "maine", "main", "mai", "mujhe", "hum", "humne", "aap", "dost", "wala", "wale", "wali",
            "ko", "ne", "se", "ka", "ki", "ke", "hai", "hain", "tha", "aaj", "kal", "abhi", "ek", "is", "us", "uska", "unka",
            "dukaan", "the", "a", "to", "from", "gave", "give", "remind", "likho", "likh", "likhna", "daal", "daalo", "add",
            "kar", "karo", "karna", "do", "dena", "liye", "liya", "lo"
        )

        private fun isNameToken(tok: String, canonTok: String): Boolean =
            tok !in stop && canonTok == tok && tok !in Normalizer.fillers && !NumberParser.isNumberWord(tok) &&
                tok.length >= 2 && tok.all { it.isLetter() } && canonTok !in Normalizer.synonyms.keys

        /** Name = up to two name-like tokens right before [marker] ("ko"/"ne"/"se"), or the first name-like run. */
        fun extractName(n: Normalized, marker: String? = null): String? {
            val toks = n.tokens.map { it.lowercase() }
            val idxs = if (marker != null) toks.indices.filter { toks[it] == marker } else listOf()
            for (m in idxs) {
                val parts = mutableListOf<String>()
                var k = m - 1
                while (k >= 0 && parts.size < 2 && isNameToken(toks[k], n.canon[k])) { parts.add(0, toks[k]); k-- }
                if (parts.isNotEmpty()) return parts.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            }
            if (marker != null) return null
            val start = toks.indices.firstOrNull { isNameToken(toks[it], n.canon[it]) } ?: return null
            val parts = mutableListOf(toks[start])
            if (start + 1 < toks.size && isNameToken(toks[start + 1], n.canon[start + 1])) parts.add(toks[start + 1])
            return parts.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }
    }
}
