package com.bataokaise.app.voice

/**
 * Devanagari to loose Hinglish (Latin) transliteration.
 *
 * Android's hi-IN recognizer usually returns Devanagari ("रमेश को दो सौ उधार"), while users
 * and our rules think in Hinglish ("ramesh ko do sau udhaar"). We convert to Latin so one
 * rule set covers both scripts. It is deliberately simple: implicit schwa plus a common
 * schwa-deletion heuristic. Good enough for keyword matching and for reading names back.
 */
object Transliterator {
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e"
    )
    private val matras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e"
    )
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l"
    )
    private val nuktaForms = mapOf('क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "d", 'ढ' to "dh", 'फ' to "f")
    private const val HALANT = '्'
    private const val NUKTA = '़'

    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    fun toLatin(input: String): String {
        if (!hasDevanagari(input)) return input
        return input.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { word ->
            if (hasDevanagari(word)) translitWord(word) else word
        }
    }

    // Each unit: consonant (+ vowel or schwa) or standalone vowel. Then delete schwas.
    private data class Unit(val cons: String?, var vowel: String, val schwa: Boolean)

    private fun translitWord(word: String): String {
        val units = mutableListOf<Unit>()
        val out = StringBuilder()
        var i = 0
        fun flush() {
            if (units.isEmpty()) return
            // Final schwa is silent: "ramesha" -> "ramesh".
            val last = units.last(); if (last.schwa && units.size > 1) last.vowel = ""
            // Word-final long i is written "i" in Hinglish: bijli, nahi, paani.
            if (last.vowel == "ee" && units.size > 1) last.vowel = "i"
            // Medial schwa deletion: V C a C V -> drop a (e.g. bijali -> bijli).
            for (k in units.size - 2 downTo 1) {
                val u = units[k]
                if (u.schwa && u.cons != null && units[k - 1].vowel.isNotEmpty() &&
                    units[k + 1].cons != null && units[k + 1].vowel.isNotEmpty()) u.vowel = ""
            }
            units.forEach { out.append(it.cons ?: "").append(it.vowel) }
            units.clear()
        }
        while (i < word.length) {
            val c = word[i]
            val next = word.getOrNull(i + 1)
            when {
                c in consonants -> {
                    var cons = consonants.getValue(c)
                    var j = i + 1
                    if (next == NUKTA) { cons = nuktaForms[c] ?: cons; j++ }
                    val after = word.getOrNull(j)
                    when {
                        after == HALANT -> { units.add(Unit(cons, "", false)); j++ }
                        after != null && after in matras -> { units.add(Unit(cons, matras.getValue(after), false)); j++ }
                        else -> units.add(Unit(cons, "a", true))
                    }
                    i = j
                }
                c in vowels -> { units.add(Unit(null, vowels.getValue(c), false)); i++ }
                c == 'ं' || c == 'ँ' -> { units.lastOrNull()?.let { it.vowel += "n" } ?: out.append("n"); i++ }
                c == 'ः' -> { units.lastOrNull()?.let { it.vowel += "h" }; i++ }
                c in '०'..'९' -> { flush(); out.append('0' + (c - '०')); i++ }
                c == '।' -> { flush(); out.append('.'); i++ }
                else -> { flush(); out.append(c); i++ }
            }
        }
        flush()
        return out.toString()
    }
}
