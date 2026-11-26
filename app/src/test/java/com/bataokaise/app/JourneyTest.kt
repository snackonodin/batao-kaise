package com.bataokaise.app

import com.bataokaise.app.guide.Decision
import com.bataokaise.app.guide.GuideLogic
import com.bataokaise.app.guide.NodeData
import com.bataokaise.app.journey.Journey
import com.bataokaise.app.journey.JourneyMatcher
import com.bataokaise.app.journey.JourneyRepo
import com.bataokaise.app.voice.Normalizer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JourneyTest {
    private val journeys: List<Journey> = File("src/main/assets/journeys").listFiles()!!
        .filter { it.name.endsWith(".json") }.map { JourneyRepo.parse(it.readText()) }
    private val pay = journeys.first { it.id == "demo_pay_bijli" }
    private val pkg = "com.bataokaise.demopay"

    @Test fun allJourneysValid() {
        assertEquals(3, journeys.size)
        journeys.forEach { j ->
            assertTrue(j.status in setOf("verified", "demo"))
            assertTrue(j.reviewedBy.isNotBlank() && j.reviewDate.isNotBlank())
            assertTrue("${j.id} needs a final step", j.steps.last().final)
            j.steps.forEach { s -> assertTrue("${j.id}/${s.id} line too long", s.say.split(" ").size < 15) }
        }
    }

    @Test fun matcherByIntentAndTriggers() {
        assertEquals("demo_pay_bijli", JourneyMatcher.match(journeys, "bill_pay", null)?.id)
        assertEquals("whatsapp_udhaar_reminder", JourneyMatcher.match(journeys, "udhaar_remind", null)?.id)
        assertEquals("demo_pay_bijli", JourneyMatcher.match(journeys, null, Normalizer.normalize("mujhe light ka bil bharna hai"))?.id)
        assertEquals("demo_scheme_form", JourneyMatcher.match(journeys, null, Normalizer.normalize("yojana ka form bharna hai"))?.id)
        assertNull(JourneyMatcher.match(journeys, null, Normalizer.normalize("mausam kaisa hai")))
    }

    @Test fun fillTemplate() {
        assertEquals("number: 4-5-1-2.", JourneyMatcher.fill("number: {account_spoken}.", mapOf("account_spoken" to "4-5-1-2")))
    }

    private fun screen(marker: String, vararg extra: NodeData) =
        listOf(NodeData(viewId = marker, bounds = intArrayOf(0, 0, 1080, 2000))) + extra

    private fun node(id: String, text: String? = null, editable: Boolean = false, password: Boolean = false) =
        NodeData(viewId = id, text = text, editable = editable, password = password, bounds = intArrayOf(100, 500, 500, 700))

    @Test fun guideWalksDemoPay() {
        val v = mapOf("amount" to "1240")
        // Home: point at Bills.
        var d = GuideLogic.evaluate(pay, 0, pkg, screen("screen_home", node("bills_tile", "Bills")), v)
        assertTrue(d is Decision.Show && d.index == 0 && d.target?.shortId == "bills_tile")
        // Bills screen appears: advance automatically.
        d = GuideLogic.evaluate(pay, 0, pkg, screen("screen_bills", node("electricity_tile", "Electricity")), v)
        assertTrue(d is Decision.Show && d.index == 1 && d.target?.shortId == "electricity_tile")
        // Electricity with account typed and bill fetched: skip to Continue.
        d = GuideLogic.evaluate(pay, 2, pkg, screen("screen_electricity", node("account_field", "451277883301", editable = true),
            node("bill_details"), node("continue_btn", "Continue")), v)
        assertTrue(d is Decision.Show && d.index == 3 && d.target?.shortId == "continue_btn")
        // PIN screen is sensitive: hide.
        d = GuideLogic.evaluate(pay, 4, pkg, screen("screen_pin", node("pin_field", editable = true, password = true)), v)
        assertEquals(Decision.Sensitive(5), d)
        // Success.
        d = GuideLogic.evaluate(pay, 5, pkg, screen("screen_success"), v)
        assertEquals(Decision.Finished(6), d)
    }

    @Test fun wrongScreenAndBackwards() {
        // Wrong app entirely.
        val d = GuideLogic.evaluate(pay, 1, "com.example.other", listOf(node("x")), emptyMap())
        assertTrue(d is Decision.WrongScreen)
        // User went back to Home from Bills: regress to step 0, not "wrong".
        val back = GuideLogic.evaluate(pay, 1, pkg, screen("screen_home", node("bills_tile", "Bills")), emptyMap())
        assertTrue(back is Decision.Show && back.index == 0)
        // Keyboard or our own bubble: ignore.
        assertEquals(Decision.Ignore, GuideLogic.evaluate(pay, 1, "com.bataokaise.app", emptyList(), emptyMap()))
    }

    @Test fun targetFallbackOrder() {
        val nodes = listOf(
            NodeData(text = "Send money", bounds = intArrayOf(0, 0, 10, 10)),
            NodeData(desc = "Send", bounds = intArrayOf(0, 20, 10, 30)),
        )
        // No view id match -> falls back to content description.
        val t = GuideLogic.findTarget(com.bataokaise.app.journey.Target(viewId = "com.whatsapp:id/send", desc = "Send"), nodes)
        assertEquals("Send", t?.desc)
    }

    @Test fun otpFieldIsSecret() {
        assertTrue(GuideLogic.hasSecretField(listOf(NodeData(viewId = "otp", editable = true))))
        assertTrue(GuideLogic.hasSecretField(listOf(NodeData(hint = "Enter OTP", editable = true))))
        assertFalse(GuideLogic.hasSecretField(listOf(NodeData(viewId = "account_field", editable = true))))
    }
}
