package com.bataokaise.app

import com.bataokaise.app.rescue.Redactor
import org.junit.Assert.*
import org.junit.Test

/** Fake numbers only. None of these are real OTPs, Aadhaar, phone or account numbers. */
class RedactorTest {
    private fun r(s: String) = Redactor.clean(s)

    @Test fun otpPinPassword() {
        assertEquals("OTP [OTP HATAYA] aaya", r("OTP 482913 aaya"))
        assertEquals("otp hai [OTP HATAYA]", r("otp hai 000111"))
        assertEquals("UPI PIN: [PIN HATAYA]", r("UPI PIN: 1234"))
        assertEquals("password is [PASSWORD HATAYA]", r("password is Fake@123"))
        assertFalse(r("मेरा ओटीपी 556677 है").contains("556677"))
    }

    @Test fun aadhaarAccountPhoneUpi() {
        assertFalse(r("Aadhaar 1234 5678 9012").contains("9012"))
        assertFalse(r("account 451277883301").contains("883301"))
        assertFalse(r("A/c 00011122233344").contains("2233"))
        assertEquals("call [PHONE HATAYA]", r("call 9876543210"))
        assertEquals("call [PHONE HATAYA]", r("call +91 98765 43210"))
        assertEquals("pay to [UPI HATAYA] now", r("pay to sunita.kirana@demoupi now"))
        assertFalse(r("card 4111 1111 1111 1111").contains("1111"))
    }

    @Test fun keepsNormalNumbers() {
        assertEquals("Rakam 1240 rupaye, 2 baar koshish", r("Rakam 1240 rupaye, 2 baar koshish"))
        assertEquals("Due date 15/12/2026", r("Due date 15/12/2026"))
    }

    @Test fun redactAgainAfterEdit() {
        val first = r("Atak gaya at Pay screen")
        val edited = "$first. Mera number 9123456780 hai, OTP 112233"
        val again = r(edited)
        assertFalse(again.contains("9123456780")); assertFalse(again.contains("112233"))
        assertEquals(again, r(again))   // idempotent
    }
}

class NameMatchTest {
    private val names = listOf("Ramesh Kumar", "Ramesh Yadav", "Suresh Pal", "Geeta Devi")
    @Test fun resolve() {
        assertEquals(com.bataokaise.app.udhaar.NameMatch.R.Ambiguous(listOf("Ramesh Kumar", "Ramesh Yadav")), com.bataokaise.app.udhaar.NameMatch.resolve("Ramesh", names))
        assertEquals(com.bataokaise.app.udhaar.NameMatch.R.Exact("Suresh Pal"), com.bataokaise.app.udhaar.NameMatch.resolve("suresh", names))
        assertEquals(com.bataokaise.app.udhaar.NameMatch.R.Exact("Ramesh Yadav"), com.bataokaise.app.udhaar.NameMatch.resolve("Ramesh Yadav", names))
        assertEquals(com.bataokaise.app.udhaar.NameMatch.R.New("Rakesh"), com.bataokaise.app.udhaar.NameMatch.resolve("rakesh", names))
    }
    @Test fun pick() {
        val opts = listOf("Ramesh Kumar", "Ramesh Yadav")
        assertEquals("Ramesh Yadav", com.bataokaise.app.udhaar.NameMatch.pick(listOf("yadav", "wala"), opts))
        assertEquals("Ramesh Kumar", com.bataokaise.app.udhaar.NameMatch.pick(listOf("pehla"), opts))
        assertNull(com.bataokaise.app.udhaar.NameMatch.pick(listOf("pata", "nahi"), opts))
    }
}
