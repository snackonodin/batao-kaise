package com.bataokaise.app.core

import com.bataokaise.app.BataoApp
import com.bataokaise.app.Route
import com.bataokaise.app.intent.Intent
import com.bataokaise.app.journey.JourneyRepo
import com.bataokaise.app.journey.JourneyRunner
import com.bataokaise.app.journey.JourneyState
import com.bataokaise.app.voice.Speaker

/**
 * Runs confirmed intents. The loop (Assistant) only ever calls this after "Haan".
 */
class Tasks(private val app: BataoApp, private val a: Assistant) {
    val runner = JourneyRunner(app, a)
    /** Last stuck state, read by the Rescue Circle screen. */
    var lastStuck: JourneyState? = null
    var lastStuckReason: String = ""

    fun attach() {
        a.onTask = ::run
        a.onRescue = { reason -> rescue(reason, runner.state()) }
        runner.onRescue = { st -> rescue("step_failed_twice", st) }
        runner.onFinished = { }
    }

    fun run(intent: Intent) {
        when (intent) {
            is Intent.BillPay -> {
                app.open(Route.BILL)
                a.say("Bill ki photo lijiye, ya demo bill chuniye.")
            }
            is Intent.UdhaarAdd, is Intent.UdhaarRepay, Intent.UdhaarBalance, is Intent.UdhaarRemind -> {
                app.open(Route.LEDGER)
                a.say("Udhaar khata khol raha hoon.")
            }
            Intent.Schemes -> { app.open(Route.SCHEMES); a.say("Yojana dhoondhte hain.") }
            Intent.Stuck -> rescue("atak_gaya", runner.state())
            else -> a.startTask()
        }
    }

    /** Demo Pay bill journey. [account] digits only. */
    fun startBillJourney(account: String, amount: Long) {
        val j = JourneyRepo.byId(app, "demo_pay_bijli") ?: return a.say("Ye kaam abhi nahi seekha.")
        runner.start(j, mapOf(
            "account" to account,
            "account_spoken" to Speaker.digits(account),
            "amount" to amount.toString(),
        ))
    }

    fun rescue(reason: String, state: JourneyState?) {
        lastStuck = state
        lastStuckReason = reason
        runner.stop(silent = true)
        app.open(Route.RESCUE)
    }
}
