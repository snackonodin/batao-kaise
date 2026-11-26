package com.bataokaise.app.voice

import kotlin.math.roundToLong

/**
 * Spoken amounts in Hindi, Hinglish and English to numbers.
 * "do sau" = 200, "dhai sau" = 250, "saade teen sau" = 350, "do hazaar paanch sau" = 2500,
 * "two hundred" = 200, "fifteen hundred" = 1500, "2,000" = 2000, "2 hazaar" = 2000.
 * Input should already be lowercase Latin (see [Transliterator]).
 */
object NumberParser {
    private fun sq(w: String) = Normalizer.squash(w)
    private fun <V> squashKeys(m: Map<String, V>): Map<String, V> = buildMap { m.forEach { (k, v) -> putIfAbsent(sq(k), v) } }
    private fun squashSet(s: Set<String>) = s.map(::sq).toSet()

    private val units: Map<String, Int> = squashKeys(buildMap {
        val hindi = listOf(
            "ek", "do", "teen", "char", "paanch", "chhah", "saat", "aath", "nau", "das",
            "gyarah", "barah", "terah", "chaudah", "pandrah", "solah", "satrah", "atharah", "unnis", "bees",
            "ikkis", "baais", "teis", "chaubis", "pachchis", "chhabbis", "sattais", "atthais", "untis", "tees",
            "ikattis", "battis", "taintis", "chauntis", "paintis", "chhattis", "saintis", "adtis", "untalis", "chalis",
            "iktalis", "bayalis", "taintalis", "chavalis", "paintalis", "chhiyalis", "saintalis", "adtalis", "unchas", "pachas",
            "ikyavan", "bavan", "tirpan", "chauvan", "pachpan", "chhappan", "sattavan", "atthavan", "unsath", "saath",
            "iksath", "basath", "tirsath", "chausath", "painsath", "chhiyasath", "sadsath", "adsath", "unhattar", "sattar",
            "ikhattar", "bahattar", "tihattar", "chauhattar", "pachhattar", "chhihattar", "satattar", "athattar", "unasi", "assi",
            "ikyasi", "bayasi", "tirasi", "chaurasi", "pachasi", "chhiyasi", "sattasi", "athasi", "navasi", "nabbe",
            "ikyanve", "banve", "tiranve", "chauranve", "pachanve", "chhiyanve", "sattanve", "atthanve", "ninyanve"
        )
        hindi.forEachIndexed { i, w -> put(w, i + 1) }
        // Common spelling variants from recognizers and typing.
        mapOf(
            "teen" to 3, "tin" to 3, "chaar" to 4, "panch" to 5, "paanch" to 5, "chhe" to 6, "chhah" to 6, "che" to 6, "chha" to 6,
            "sat" to 7, "saath" to 60, "ath" to 8, "aath" to 8, "nao" to 9, "dus" to 10,
            "gyaarah" to 11, "baarah" to 12, "pandrah" to 15, "pandra" to 15, "solah" to 16, "sola" to 16,
            "unees" to 19, "bis" to 20, "pachees" to 25, "pachis" to 25, "tis" to 30, "chaalis" to 40, "chalees" to 40,
            "pachaas" to 50, "pachaas" to 50, "pachaas" to 50, "sath" to 60, "sattar" to 70, "assee" to 80, "nabbe" to 90,
            "nabbay" to 90, "nabe" to 90
        ).forEach { (k, v) -> put(k, v) }
        val en = listOf(
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
        )
        en.forEachIndexed { i, w -> put(w, i + 1) }
        listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
            .forEachIndexed { i, w -> put(w, (i + 2) * 10) }
    })
    private val hundred = squashSet(setOf("sau", "so", "sow", "hundred"))
    private val thousand = squashSet(setOf("hazaar", "hazar", "hajar", "hajaar", "hazaar", "thousand", "hazaro"))
    private val lakh = squashSet(setOf("lakh", "laakh", "lac", "lakhs"))
    // "dedh sau" = 150, "dhai sau" = 250: these stand for a whole multiplier count.
    private val fractions = squashKeys(mapOf("dedh" to 1.5, "dhai" to 2.5, "dhaai" to 2.5, "adhai" to 2.5, "dhaee" to 2.5, "dhaaee" to 2.5))
    // "saade teen sau" = 3.5 x 100, "sava do sau" = 2.25 x 100, "paune do sau" = 1.75 x 100.
    private val modifiers = squashKeys(mapOf("saade" to 0.5, "sade" to 0.5, "saadhe" to 0.5, "sava" to 0.25, "sawa" to 0.25, "paune" to -0.25))

    private fun numeric(tok: String): Double? = tok.replace(",", "").toDoubleOrNull()

    fun isNumberWord(raw: String): Boolean { val tok = sq(raw); return tok in units || tok in hundred || tok in thousand || tok in lakh ||
        tok in fractions || tok in modifiers || numeric(tok) != null || tok == "and" || tok == "aur" }

    /** All number expressions in the token list, in order. */
    fun findAll(tokens: List<String>): List<Long> {
        val results = mutableListOf<Long>()
        var total = 0.0
        var current = 0.0
        var pendingMod = 0.0
        var active = false
        fun close() {
            if (active) { results.add((total + current).roundToLong()); }
            total = 0.0; current = 0.0; pendingMod = 0.0; active = false
        }
        for ((idx, rawTok) in tokens.withIndex()) {
            val n = numeric(rawTok)
            val tok = sq(rawTok)
            when {
                // "and"/"aur" continue a number only between number words ("two hundred and fifty").
                (tok == "and" || tok == "aur") && active && tokens.getOrNull(idx + 1)?.let { isNumberWord(it) && it != "and" && it != "aur" } == true -> {}
                tok in modifiers -> { if (active && current != 0.0) close(); pendingMod = modifiers.getValue(tok); active = true }
                tok in fractions -> { current += fractions.getValue(tok); active = true }
                n != null -> { current += n + if (pendingMod != 0.0) pendingMod.also { pendingMod = 0.0 } else 0.0; active = true }
                tok in units -> {
                    val v = units.getValue(tok)
                    // "do teen" style ranges, or "do sau do" (202): add if current is a multiple of 100 or zero.
                    if (current != 0.0 && current % 100.0 != 0.0 && current < 100) close()
                    current += v + if (pendingMod != 0.0) pendingMod.also { pendingMod = 0.0 } else 0.0
                    active = true
                }
                tok in hundred && active -> { current = (if (current == 0.0) 1.0 else current) * 100 }
                tok in hundred && !active -> { current = 100.0; active = true }
                tok in thousand -> { total += (if (current == 0.0) 1.0 else current) * 1000; current = 0.0; active = true }
                tok in lakh -> { total += (if (current == 0.0) 1.0 else current) * 100000; current = 0.0; active = true }
                else -> close()
            }
        }
        close()
        return results
    }

    fun first(tokens: List<String>): Long? = findAll(tokens).firstOrNull()

    fun parse(text: String): Long? = first(Normalizer.tokenize(Transliterator.toLatin(text).lowercase()))
}
