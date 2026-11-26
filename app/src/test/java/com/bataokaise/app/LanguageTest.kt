package com.bataokaise.app

import com.bataokaise.app.intent.Command
import com.bataokaise.app.intent.Intent
import com.bataokaise.app.intent.RuleIntentEngine
import com.bataokaise.app.voice.NumberParser
import com.bataokaise.app.voice.Normalizer
import com.bataokaise.app.voice.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberParserTest {
    private fun p(s: String) = NumberParser.parse(s)

    @Test fun hindiBasics() {
        assertEquals(200L, p("do sau"))
        assertEquals(250L, p("dhai sau"))
        assertEquals(150L, p("dedh sau"))
        assertEquals(100L, p("sau"))
        assertEquals(120L, p("ek sau bees"))
        assertEquals(50L, p("pachaas"))
        assertEquals(2500L, p("do hazaar paanch sau"))
        assertEquals(1500L, p("dedh hazaar"))
        assertEquals(350L, p("saade teen sau"))
        assertEquals(225L, p("sava do sau"))
        assertEquals(175L, p("paune do sau"))
        assertEquals(200000L, p("do lakh"))
    }

    @Test fun englishAndDigits() {
        assertEquals(200L, p("two hundred"))
        assertEquals(250L, p("two hundred and fifty"))
        assertEquals(1500L, p("fifteen hundred"))
        assertEquals(2000L, p("2,000"))
        assertEquals(2000L, p("2 hazaar"))
        assertEquals(200L, p("200rs"))
        assertEquals(1240L, p("₹1,240"))
    }

    @Test fun devanagari() {
        assertEquals(200L, p("दो सौ"))
        assertEquals(250L, p("ढाई सौ"))
        assertEquals(500L, p("५००"))
    }

    @Test fun multipleNumbers() {
        val toks = Normalizer.normalize("Ramesh ko do sau aur Suresh ko teen sau").canon
        assertEquals(listOf(200L, 300L), NumberParser.findAll(toks))
    }
}

class NormalizerTest {
    @Test fun synonymsCollapse() {
        listOf("udhaar", "udhari", "baaki", "karja", "udhar").forEach {
            assertEquals(it, "udhaar", Normalizer.canon(it))
        }
        listOf("bijli", "bijuli", "light", "bijlee").forEach { assertEquals(it, "bijli", Normalizer.canon(it)) }
        listOf("paisa", "rupaye", "rupees").forEach { assertEquals(it, "paisa", Normalizer.canon(it)) }
    }

    @Test fun transliteration() {
        assertEquals("ramesh ko do sau udhaar", Transliterator.toLatin("रमेश को दो सौ उधार"))
        assertEquals("bijli", Transliterator.toLatin("बिजली"))
        assertEquals("bil", Transliterator.toLatin("बिल"))
    }

    @Test fun phirSeJoined() {
        assertEquals(listOf("phirse"), Normalizer.normalize("phir se").canon)
    }

    @Test fun noiseWordCount() {
        assertTrue(Normalizer.normalize("umm arre").wordCount < 2)
    }
}

class IntentTest {
    private val e = RuleIntentEngine()
    private fun d(s: String) = e.detectSync(Normalizer.normalize(s))

    @Test fun udhaar() {
        assertEquals(Intent.UdhaarAdd("Ramesh", 200), d("Ramesh ko do sau udhaar"))
        assertEquals(Intent.UdhaarAdd("Ramesh", 200), d("रमेश को दो सौ उधार"))
        assertEquals(Intent.UdhaarAdd("Ramesh Kumar", 250), d("ramesh kumar ko dhai sau rupaye udhari"))
        assertEquals(Intent.UdhaarRepay("Ramesh", 100), d("Ramesh ne 100 de diye"))
        assertEquals(Intent.UdhaarBalance, d("Kiska kitna baaki hai?"))
        assertEquals(Intent.UdhaarRemind("Ramesh"), d("Ramesh se paisa kaise loon?"))
    }

    @Test fun billAndSchemes() {
        assertEquals("bill_pay", d("bijli ka bill bharna hai").key)
        assertEquals("bill_pay", d("light ka bil bharna hai").key)
        assertEquals("schemes", d("koi sarkari yojana batao").key)
    }

    @Test fun commandsAndYesNo() {
        assertEquals(Intent.Cmd(Command.STOP), d("ruko"))
        assertEquals(Intent.Cmd(Command.REPEAT), d("phir se"))
        assertEquals(Intent.Cmd(Command.BACK), d("peeche"))
        assertEquals(Intent.Cmd(Command.NEW_TASK), d("naya kaam"))
        assertEquals(Intent.Cmd(Command.DONE), d("ho gaya"))
        assertEquals(Intent.Yes, d("haan"))
        assertEquals(Intent.Yes, d("haan ji sahi"))
        assertEquals(Intent.No, d("nahi"))
        assertEquals(Intent.Stuck, d("main atak gaya"))
    }

    @Test fun noiseAndUnknown() {
        assertEquals(Intent.Noise, d("umm"))
        assertEquals(Intent.Unknown, d("mausam kaisa hai aaj"))
    }

    @Test fun readBackUnder15Words() {
        listOf(d("Ramesh ko do sau udhaar"), d("bijli ka bill"), d("yojana batao"), d("Ramesh se paisa kaise loon"))
            .forEach { assertTrue(it.readBack()!!.split(" ").size < 15) }
    }
}

class DebugPhrasesTest {
    @Test fun all20Pass() {
        val results = com.bataokaise.app.debug.TestPhrases.runAll()
        assertEquals(20, results.size)
        val failed = results.filterNot { it.pass }.map { "${it.phrase.text} -> ${it.got} (want ${it.phrase.expect})" }
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }
}
