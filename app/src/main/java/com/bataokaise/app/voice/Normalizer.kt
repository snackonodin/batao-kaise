package com.bataokaise.app.voice

/**
 * Turns any transcript (Devanagari or Latin, any spelling) into canonical Hinglish tokens.
 * Synonyms collapse to one canonical word so the intent rules stay small:
 *   udhaar = udhari, udhar, baaki, karja ...    bijli = bijuli, light ...    paisa = rupaye ...
 */
object Normalizer {
    /** canonical -> variants. Variants are compared after [squash]. */
    val synonyms: Map<String, List<String>> = mapOf(
        "udhaar" to listOf("udhaar", "udhar", "udhari", "udhaari", "udhaar", "baaki", "baki", "baakee", "karja", "karjaa", "karza", "karz", "qarz", "credit"),
        "bijli" to listOf("bijli", "bijuli", "bijlee", "bijali", "light", "lite", "electricity", "electric", "current", "bijlii"),
        "paisa" to listOf("paisa", "paise", "paisaa", "rupaye", "rupay", "rupaya", "rupee", "rupees", "rupiya", "rs", "rupaiya", "rupaiye", "rupye", "rupe", "rupayee"),
        "bill" to listOf("bill", "bil", "biil", "bila"),
        "bharna" to listOf("bharna", "bharni", "bhar", "bharu", "bharoon", "bharun", "bharen", "bharein", "pay", "payment", "jama", "chukana", "bhugtan", "bharana", "bhrna"),
        "yojana" to listOf("yojana", "yojna", "yojnaa", "scheme", "schemes", "sarkari", "sarkaari", "subsidy", "pension"),
        "padho" to listOf("padho", "padh", "padhna", "padhiye", "padhke", "read", "sunao", "bataao", "batao", "dekho"),
        "diye" to listOf("diye", "diya", "de", "di", "die", "diae", "diyae", "lauta", "lautaye", "lautaya", "wapas", "vapas", "chukaye", "chukaya", "jama", "diyea"),
        "kitna" to listOf("kitna", "kitne", "kitni", "kitnaa", "kitana", "kya"),
        "kiska" to listOf("kiska", "kiske", "kiski", "kis", "kaun", "kon", "kisne"),
        "yaad" to listOf("yaad", "yad", "reminder", "remind", "yaaddihani", "dilao", "dilaun", "dilana"),
        "kaise" to listOf("kaise", "kese", "kaisay", "how", "kaisa"),
        "loon" to listOf("loon", "lun", "lu", "loo", "lena", "lein", "len", "maangu", "mangu", "vasool", "wasool"),
        "atak" to listOf("atak", "atka", "atki", "atke", "fas", "phas", "phans", "fans", "phasa", "fasa", "stuck"),
        "haan" to listOf("haan", "ha", "han", "haa", "haanji", "hanji", "ji", "yes", "sahi", "theek", "thik", "ok", "okay", "bilkul", "hmm", "achha", "acha"),
        "nahi" to listOf("nahi", "nahin", "nai", "na", "no", "galat", "mat", "nako"),
        "ruko" to listOf("ruko", "ruk", "rukiye", "bas", "stop", "band"),
        "phirse" to listOf("phirse", "firse", "dobara", "repeat"),
        "peeche" to listOf("peeche", "piche", "pichhe", "peechhe", "back", "wapas"),
        "naya" to listOf("naya", "nayaa", "new"),
        "kaam" to listOf("kaam", "kam", "task"),
        "ho" to listOf("ho", "hoo"),
        "gaya" to listOf("gaya", "gya", "gayi", "gai", "gaye", "done"),
    )

    private val lookup: Map<String, String> = buildMap {
        // Later canonical words don't override earlier ones ("jama" stays "bharna"; "wapas" stays "diye").
        synonyms.forEach { (canon, vars) -> vars.forEach { v -> putIfAbsent(squash(v), canon) } }
    }

    /** Filler and noise words we drop: they never decide intent. */
    val fillers = setOf("umm", "um", "uh", "hmmm", "arre", "are", "arey", "yaar", "bhai", "bhaiya", "ji", "matlab", "woh", "wo", "na", "toh", "to", "please", "plz", "accha")

    /** Collapse doubled letters and common spelling swaps so "udhaar" == "udhar", "rupaiye" ~ "rupaye". */
    fun squash(w: String): String = w.lowercase()
        .replace("ee", "i").replace("oo", "u").replace("w", "v")
        .replace(Regex("(.)\\1+"), "$1")

    fun tokenize(s: String): List<String> =
        s.lowercase()
            .replace("₹", " rs ")
            .replace(Regex("(?<=\\d),(?=\\d)"), "")        // 2,000 -> 2000
            .replace(Regex("(\\d)([a-z])"), "$1 $2")       // 200rs -> 200 rs
            .split(Regex("[^\\p{L}\\p{N}.]+"))
            .map { it.trim('.') }
            .filter { it.isNotBlank() }

    /** Canonical form of one token; numbers and names pass through unchanged. */
    fun canon(tok: String): String = lookup[squash(tok)] ?: tok

    /**
     * "phir se" and "naya kaam" are two-word commands; join them first.
     * Returns canonical tokens; the original Latin tokens are kept for names.
     */
    fun normalize(raw: String): Normalized {
        val latin = Transliterator.toLatin(raw)
        val toks = tokenize(latin)
        val joined = mutableListOf<String>()
        var i = 0
        while (i < toks.size) {
            val a = toks[i]; val b = toks.getOrNull(i + 1)
            if (b != null && squash(a) in setOf("phir", "fir") && squash(b) == "se") { joined.add("phirse"); i += 2; continue }
            joined.add(a); i++
        }
        val canonTokens = joined.map { canon(it) }
        return Normalized(raw = raw, latin = latin, tokens = joined, canon = canonTokens)
    }
}

data class Normalized(
    val raw: String,
    val latin: String,
    /** Latin tokens as heard (used for names). */
    val tokens: List<String>,
    /** Canonical tokens (used for rules). Same length and order as [tokens]. */
    val canon: List<String>,
) {
    val text: String get() = canon.joinToString(" ")
    /** Count of meaningful words, ignoring fillers. Fewer than 2 means "too noisy". */
    val wordCount: Int get() = tokens.count { it !in Normalizer.fillers }
    fun has(vararg words: String) = words.all { it in canon }
    fun hasAny(vararg words: String) = words.any { it in canon }
    val amounts: List<Long> get() = NumberParser.findAll(canon)
}
