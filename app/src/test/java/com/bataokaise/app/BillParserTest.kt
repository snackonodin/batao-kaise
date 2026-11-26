package com.bataokaise.app

import com.bataokaise.app.bill.BillParser
import com.bataokaise.app.bill.FieldKind
import com.bataokaise.app.bill.OcrLine
import org.junit.Assert.*
import org.junit.Test

class BillParserTest {
    private val bill = listOf(
        OcrLine("DEMO BILL, NOT REAL", 300, 40, 780, 90),
        OcrLine("Demo Vidyut Vitran Nigam", 60, 120, 700, 170),
        OcrLine("Consumer No / उपभोक्ता संख्या", 600, 220, 1020, 260),
        OcrLine("4512 7788 3301", 640, 270, 1000, 320),
        OcrLine("Name: Sunita Devi", 60, 380, 500, 420),
        OcrLine("Bill Date: 01/12/2026", 60, 440, 500, 480),
        OcrLine("Amount Payable / देय राशि", 60, 900, 520, 950),
        OcrLine("₹ 1,240.00", 600, 905, 900, 960),
        OcrLine("Due Date / अंतिम तिथि: 15/12/2026", 60, 1000, 800, 1050),
    )

    @Test fun findsAllThree() {
        val r = BillParser.parse(bill, 1080, 1440)
        assertEquals("451277883301", r.account)
        assertEquals(1240L, r.amount)
        assertEquals("15/12/2026", r[FieldKind.DUE]?.value)
        assertEquals("Upar daayein", BillParser.position(r[FieldKind.ACCOUNT]!!.box!!, 1080, 1440))
    }

    @Test fun unreadableIsNotGuessed() {
        val smudged = bill.map { if (it.text.startsWith("₹")) it.copy(text = "₹ ~~,~~") else it }
        val r = BillParser.parse(smudged, 1080, 1440)
        assertNull(r[FieldKind.AMOUNT]?.value)
        assertNotNull(r[FieldKind.AMOUNT]?.box)   // grey "Saaf nahi" box over the label
        assertEquals("451277883301", r.account)
    }

    @Test fun noLabelsNoFields() {
        val r = BillParser.parse(listOf(OcrLine("hello world 12345678", 0, 0, 100, 20)), 100, 100)
        assertNull(r.account); assertNull(r.amount)
    }

    @Test fun spokenDate() {
        assertEquals("15 December 2026", BillParser.spokenDate("15/12/2026"))
    }
}
