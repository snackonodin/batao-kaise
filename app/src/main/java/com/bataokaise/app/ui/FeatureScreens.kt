package com.bataokaise.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bataokaise.app.BataoApp
import com.bataokaise.app.Route

/** Feature screens (bill, ledger, schemes, rescue, onboarding). Filled in stage by stage. */
object FeatureScreens {
    @Composable
    fun Render(route: Route, activity: MainActivity) {
        val app = BataoApp.instance
        when (route) {
            Route.BILL -> { com.bataokaise.app.bill.BillScreen(); return }
            else -> {}
        }
        val title = when (route) {
            Route.BILL -> "Bill padho"
            Route.LEDGER -> "Udhaar khata"
            Route.SCHEMES -> "Yojana"
            Route.RESCUE -> "Rescue Circle"
            Route.ONBOARDING -> "Shuruaat"
            else -> ""
        }
        ScreenHeader(title, onBack = { app.nav.go(Route.HOME) })
        Column(Modifier.padding(18.dp)) { Placeholder("Ye hissa agle stage mein ban raha hai.") }
    }
}
