package com.bataokaise.app.debug

import com.bataokaise.app.intent.RuleIntentEngine
import com.bataokaise.app.voice.Normalizer

/**
 * 20 typed test phrases (spec section 15): accents, code-mixing, noise words, wrong names.
 * `expect` is Intent.describe() output.
 */
data class TestPhrase(val text: String, val expect: String, val note: String)

object TestPhrases {
    val all = listOf(
        TestPhrase("Ramesh ko do sau udhaar", "udhaar_add(Ramesh, 200)", "basic Hinglish"),
        TestPhrase("रमेश को दो सौ उधार", "udhaar_add(Ramesh, 200)", "Devanagari from hi-IN recognizer"),
        TestPhrase("arre bhai ramesh ko dhai sau rupaye udhari likh do", "udhaar_add(Ramesh, 250)", "fillers + synonym udhari"),
        TestPhrase("Suresh Yadav ko 1500 karja", "udhaar_add(Suresh Yadav, 1500)", "two-word name, karja"),
        TestPhrase("mohan ko saade teen sau baaki", "udhaar_add(Mohan, 350)", "saade + baaki"),
        TestPhrase("Ramesh ne 100 de diye", "udhaar_repay(Ramesh, 100)", "repayment"),
        TestPhrase("रमेश ने सौ रुपये लौटा दिए", "udhaar_repay(Ramesh, 100)", "Devanagari repayment, lauta"),
        TestPhrase("Kiska kitna baaki hai?", "udhaar_balance", "balance query"),
        TestPhrase("Ramesh se paisa kaise loon?", "udhaar_remind(Ramesh)", "remind"),
        TestPhrase("ramesh ko yaad dilao paise ki", "udhaar_remind(Ramesh)", "remind, yaad"),
        TestPhrase("bijli ka bill bharna hai", "bill_pay", "bill"),
        TestPhrase("light ka bil bharna h", "bill_pay", "Bhojpuri-style light/bil, SMS spelling h"),
        TestPhrase("बिजली का बिल भरना है", "bill_pay", "Devanagari bill"),
        TestPhrase("electricity bill pay karna hai", "bill_pay", "English code-mix"),
        TestPhrase("koi sarkari yojana hai kya mere liye", "schemes", "schemes"),
        TestPhrase("main atak gaya", "stuck", "stuck"),
        TestPhrase("umm", "noise", "noise, 1 word"),
        TestPhrase("haan ji", "yes", "yes"),
        TestPhrase("phir se", "cmd_repeat", "global repeat"),
        TestPhrase("mausam kaisa hai aaj", "unknown", "unknown request -> Rescue"),
    )

    data class Result(val phrase: TestPhrase, val got: String) { val pass get() = got == phrase.expect }

    fun runAll(): List<Result> {
        val e = RuleIntentEngine()
        return all.map { Result(it, e.detectSync(Normalizer.normalize(it.text)).describe()) }
    }
}
