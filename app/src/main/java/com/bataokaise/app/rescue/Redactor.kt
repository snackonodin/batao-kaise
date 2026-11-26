package com.bataokaise.app.rescue

/**
 * Removes secrets and identifiers before anything is shared:
 * OTPs, PINs, passwords, and account, Aadhaar, card, phone and UPI numbers.
 * Runs on every summary, and again after every edit.
 */
object Redactor {
    data class Out(val text: String, val removed: Map<String, Int>)

    private val rules: List<Pair<String, Regex>> = listOf(
        // Secrets introduced by a keyword: "OTP 482913", "pin: 1234", "password is abc@123".
        "OTP" to Regex("(?i)(otp|one[- ]?time|code|ओटीपी|कोड)(\\s*(?:hai|is|:|=|-|h)?\\s*)([0-9]{3,8})"),
        "PIN" to Regex("(?i)(\\bm?pin\\b|upi pin|पिन)(\\s*(?:hai|is|:|=|-|h|number)?\\s*)([0-9]{3,8})"),
        "PASSWORD" to Regex("(?i)(password|passcode|pass word|पासवर्ड)(\\s*(?:hai|is|:|=|-|h)?\\s*)(\\S+)"),
        // UPI IDs (name@bank) and emails.
        "UPI" to Regex("(?i)\\b[\\w.\\-]{2,}@[a-z][a-z0-9.\\-]{1,}\\b"),
        // Phone: +91 / 0 prefix optional, 10 digits starting 6-9, spaces/dashes allowed.
        "PHONE" to Regex("(?<![\\d])(?:\\+?91[\\s-]?|0)?[6-9]\\d{4}[\\s-]?\\d{5}(?![\\d])"),
        // Aadhaar-like 12 digits (4-4-4 groups) and card numbers (16 digits).
        "AADHAAR/ACCOUNT" to Regex("(?<![\\d])\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?:[\\s-]?\\d{4})?(?![\\d])"),
        // Any other long number (bank account, consumer number): 8+ digits.
        "ACCOUNT" to Regex("(?<![\\d])\\d(?:[\\s-]?\\d){7,17}(?![\\d])"),
    )

    fun redact(input: String): Out {
        var s = input
        val counts = mutableMapOf<String, Int>()
        for ((kind, re) in rules) {
            s = re.replace(s) { m ->
                counts[kind] = (counts[kind] ?: 0) + 1
                val tag = "[${kind.substringBefore('/')} HATAYA]".let { if (kind == "AADHAAR/ACCOUNT") "[NUMBER HATAYA]" else it }
                // Keep the keyword for secret rules ("OTP [OTP HATAYA]") so the reader knows what was there.
                if (kind in setOf("OTP", "PIN", "PASSWORD")) m.groupValues[1] + m.groupValues[2] + tag
                else tag
            }
        }
        return Out(s, counts)
    }

    fun clean(input: String) = redact(input).text
}
