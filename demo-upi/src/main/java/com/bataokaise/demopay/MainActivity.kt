package com.bataokaise.demopay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Demo Pay: a dummy UPI app used only so Batao Kaise has a stable app to guide.
 * No network, no real money. Every element the guide targets has a stable testTag,
 * exposed to Accessibility as its view resource id (testTagsAsResourceId).
 */
enum class Screen(val tag: String) {
    Home("screen_home"), Bills("screen_bills"), Electricity("screen_electricity"),
    Pay("screen_pay"), Pin("screen_pin"), Success("screen_success")
}

const val DEMO_ACCOUNT = "451277883301"

private val Blue = Color(0xFF1E4FA0)
private val Bg = Color(0xFFF4F6FA)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) {
                Box(Modifier.fillMaxSize().background(Bg).semantics { testTagsAsResourceId = true }) {
                    DemoPayApp()
                }
            }
        }
    }
}

@Composable
fun DemoPayApp() {
    var stack by remember { mutableStateOf(listOf(Screen.Home)) }
    var account by remember { mutableStateOf("") }
    val screen = stack.last()
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { back() }

    Column(Modifier.fillMaxSize().testTag(screen.tag)) {
        DemoBanner()
        TopBar(title = when (screen) {
            Screen.Home -> "Demo Pay"
            Screen.Bills -> "Bills & Recharge"
            Screen.Electricity -> "Electricity Bill"
            Screen.Pay -> "Confirm Payment"
            Screen.Pin -> "Enter UPI PIN"
            Screen.Success -> "Payment Status"
        }, showBack = stack.size > 1, onBack = ::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (screen) {
                Screen.Home -> HomeScreen { go(Screen.Bills) }
                Screen.Bills -> BillsScreen { go(Screen.Electricity) }
                Screen.Electricity -> ElectricityScreen(account, { account = it }) { go(Screen.Pay) }
                Screen.Pay -> PayScreen(account) { go(Screen.Pin) }
                Screen.Pin -> PinScreen { stack = listOf(Screen.Home, Screen.Success) }
                Screen.Success -> SuccessScreen { stack = listOf(Screen.Home); account = "" }
            }
        }
    }
}

@Composable
fun DemoBanner() {
    Text(
        "DEMO, no real money",
        Modifier.fillMaxWidth().background(Color(0xFFFFE08A)).padding(vertical = 6.dp).testTag("demo_banner"),
        color = Color(0xFF5A4300), fontWeight = FontWeight.Bold, fontSize = 14.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
fun TopBar(title: String, showBack: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Blue).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            Text("←  Back", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.testTag("back_btn").clickable(onClick = onBack).padding(end = 16.dp, top = 4.dp, bottom = 4.dp))
        }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Tile(label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        modifier.height(96.dp).testTag(tag).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Blue)
        }
    }
}

@Composable
fun TileGrid(items: List<Triple<String, String, () -> Unit>>) {
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (l, t, c) -> Tile(l, t, Modifier.weight(1f), c) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun HomeScreen(onBills: () -> Unit) {
    Text("Namaste, Sunita", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Balance: ₹ 5,000 (demo)", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
    TileGrid(listOf(
        Triple("Send Money", "send_tile", {}),
        Triple("Scan QR", "scan_tile", {}),
        Triple("Bills", "bills_tile", onBills),
        Triple("History", "history_tile", {}),
    ))
}

@Composable
fun BillsScreen(onElectricity: () -> Unit) {
    Text("Choose a bill", fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
    TileGrid(listOf(
        Triple("Mobile Recharge", "mobile_tile", {}),
        Triple("Water", "water_tile", {}),
        Triple("Gas Cylinder", "gas_tile", {}),
        Triple("Electricity", "electricity_tile", onElectricity),
        Triple("DTH", "dth_tile", {}),
        Triple("Broadband", "broadband_tile", {}),
    ))
}

@Composable
fun ElectricityScreen(account: String, onAccount: (String) -> Unit, onContinue: () -> Unit) {
    Text("Board", fontSize = 15.sp, color = Color.Gray)
    Text("Demo Vidyut Vitran Nigam", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
    OutlinedTextField(
        value = account, onValueChange = { v -> onAccount(v.filter(Char::isDigit).take(12)) },
        label = { Text("Account / Consumer number") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("account_field")
    )
    val known = account == DEMO_ACCOUNT
    if (account.length == 12 && !known) {
        Text("Account not found (demo). Try 4512 7788 3301.", color = Color(0xFFB00020), modifier = Modifier.padding(top = 8.dp).testTag("account_error"))
    }
    if (known) {
        Card(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("bill_details")) {
            Column(Modifier.padding(16.dp)) {
                Text("Sunita Devi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Amount due: ₹1,240", fontSize = 18.sp)
                Text("Due date: 15/12/2026", fontSize = 16.sp, color = Color.Gray)
            }
        }
    }
    Button(
        onClick = onContinue, enabled = known,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp).testTag("continue_btn")
    ) { Text("Continue", fontSize = 18.sp) }
}

@Composable
fun PayScreen(account: String, onPay: () -> Unit) {
    Text("Paying to", color = Color.Gray)
    Text("Demo Vidyut Vitran Nigam", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Account: $account", fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
    Text("₹1,240", fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp).testTag("pay_amount"))
    Button(onClick = onPay, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("pay_btn")) {
        Text("Pay ₹1,240", fontSize = 18.sp)
    }
}

@Composable
fun PinScreen(onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Text("Enter your 4-digit UPI PIN", fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
    Text("(Demo: any 4 digits. Never share a real PIN.)", color = Color.Gray, fontSize = 14.sp)
    OutlinedTextField(
        value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4) },
        label = { Text("UPI PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("pin_field")
    )
    Button(onClick = onDone, enabled = pin.length == 4,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp).testTag("pin_submit")) {
        Text("Submit", fontSize = 18.sp)
    }
}

@Composable
fun SuccessScreen(onHome: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓", fontSize = 72.sp, color = Color(0xFF1B7F3B))
        Text("Demo payment done", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("success_text"))
        Text("No real money moved.", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("home_btn")) { Text("Home", fontSize = 18.sp) }
    }
}
