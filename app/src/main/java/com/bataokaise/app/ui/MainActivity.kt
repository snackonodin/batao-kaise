package com.bataokaise.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bataokaise.app.BataoApp
import com.bataokaise.app.Route
import com.bataokaise.app.VoiceEngine
import com.bataokaise.app.debug.TestPhrases
import com.bataokaise.app.overlay.BubbleService

class MainActivity : ComponentActivity() {
    private val app get() = application as BataoApp

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.assistant.micTapped()
        else app.assistant.say("Mic ki ijazat nahi mili. Neeche likh kar bataiye.")
    }

    fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun onMic() { if (hasMic() || app.settings.engine == VoiceEngine.FLOW_DICTATION) app.assistant.micTapped() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            BataoTheme {
                val route by app.nav.route.collectAsState()
                Surface(color = C.Bg, modifier = Modifier.fillMaxSize()) {
                    BackHandler(enabled = route != Route.HOME) { app.nav.go(Route.HOME) }
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        when (route) {
                            Route.HOME -> HomeScreen()
                            Route.SETTINGS -> SettingsScreen()
                            Route.DEBUG -> DebugScreen()
                            else -> FeatureHost(route)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra("route")?.let { r -> runCatching { app.nav.go(Route.valueOf(r)) } }
        if (i?.getBooleanExtra("mic", false) == true) onMic()
    }

    override fun onResume() {
        super.onResume()
        // The floating mic is shown while our UI is in the background (if overlay permission is granted).
        BubbleService.hide(this)
    }

    override fun onPause() {
        super.onPause()
        if (AndroidSettings.canDrawOverlays(this)) BubbleService.show(this)
    }

    @Composable
    fun HomeScreen() {
        val s by app.assistant.state.collectAsState()
        ScreenHeader("Batao Kaise", onBack = null) {
            if (app.settings.demoMode) DemoChip("DEMO MODE")
            TextButton(onClick = { app.nav.go(Route.SETTINGS) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("⚙️ Settings", style = MaterialTheme.typography.titleMedium, color = C.Green)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
            VoicePanel(
                s, onMic = ::onMic, onYes = app.assistant::yes, onNo = app.assistant::no,
                onPick = app.assistant::picture, onDone = app.assistant::done, onType = app.assistant::submitText
            )
            Spacer(Modifier.height(20.dp))
            Text("Seedhe kholiye", style = MaterialTheme.typography.titleMedium, color = C.Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Bill", Modifier.weight(1f), icon = "🧾", secondary = true) { app.nav.go(Route.BILL) }
                BigButton("Udhaar", Modifier.weight(1f), icon = "📒", secondary = true) { app.nav.go(Route.LEDGER) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Yojana", Modifier.weight(1f), icon = "🏛️", secondary = true) { app.nav.go(Route.SCHEMES) }
                BigButton("Madad", Modifier.weight(1f), icon = "🤝", secondary = true) { app.nav.go(Route.RESCUE) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    fun FeatureHost(route: Route) {
        FeatureScreens.Render(route, this)
    }

    @Composable
    fun SettingsScreen() {
        val st = app.settings
        var engine by remember { mutableStateOf(st.engine) }
        var upi by remember { mutableStateOf(st.upiId) }
        var trusted by remember { mutableStateOf(st.trustedName) }
        val s by app.assistant.state.collectAsState()
        ScreenHeader("Settings", onBack = { app.nav.go(Route.HOME) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoCard("Abhi chal raha hai", "${s.voiceLabel}\nIntent: ${s.intentLabel}\n" +
                (if (st.demoMode) "Demo Mode: koi key nahi, sab offline rules se." else "Backend: ${st.backendUrl}") +
                "\nHindi awaaz (TTS): " + (if (app.speaker.hindiAvailable) "haan" else "nahi mili, English-India use ho rahi"))
            Text("Awaaz ka engine", style = MaterialTheme.typography.titleLarge)
            EngineOption("Android (demo, not Wispr)", "Phone ka apna speech recognizer, hi-IN.", engine == VoiceEngine.ANDROID) { engine = VoiceEngine.ANDROID }
            EngineOption("Wispr Flow app dictation", "Text box mein Wispr Flow app se boliye.", engine == VoiceEngine.FLOW_DICTATION) { engine = VoiceEngine.FLOW_DICTATION }
            EngineOption("Wispr Flow API", if (st.demoMode) "Backend aur API key chahiye (abhi band)." else "Backend se token, Hinglish streaming.",
                engine == VoiceEngine.WISPR_API, enabled = !st.demoMode) { engine = VoiceEngine.WISPR_API }
            OutlinedTextField(upi, { upi = it }, label = { Text("Aapki UPI ID (QR ke liye)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(trusted, { trusted = it }, label = { Text("Bharose ka insaan (Rescue Circle)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            BigButton("Save karein", Modifier.fillMaxWidth(), icon = "💾") {
                st.engine = engine; st.upiId = upi.trim(); st.trustedName = trusted.trim()
                app.applyVoice(); app.applyIntentEngine()
                app.assistant.say("Save ho gaya.")
            }
            Text("Ijazat (permissions)", style = MaterialTheme.typography.titleLarge)
            PermissionRow("Mic", hasMic()) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            PermissionRow("Doosre apps ke upar bubble", AndroidSettings.canDrawOverlays(this@MainActivity)) {
                startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            PermissionRow("Screen guide (Accessibility)", com.bataokaise.app.guide.GuideAccessibilityService.isEnabled(this@MainActivity)) {
                startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            if (Build.VERSION.SDK_INT >= 33) PermissionRow("Notifications",
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
            BigButton("Onboarding phir se", Modifier.fillMaxWidth(), secondary = true) { app.nav.go(Route.ONBOARDING) }
            BigButton("Debug: 20 test phrases", Modifier.fillMaxWidth(), secondary = true, icon = "🧪") { app.nav.go(Route.DEBUG) }
            Text("Koi analytics nahi. Awaaz record karke save nahi hoti. Data phone se bahar jaane se pehle aapse poocha jaata hai.",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun DebugScreen() {
        val results = remember { TestPhrases.runAll() }
        val pass = results.count { it.pass }
        ScreenHeader("Debug tests", onBack = { app.nav.go(Route.SETTINGS) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$pass / ${results.size} pass", style = MaterialTheme.typography.headlineMedium, color = if (pass == results.size) C.Green else C.Danger)
            results.forEach { r ->
                Surface(color = if (r.pass) C.GreenSoft else Color(0xFFF7DAD6), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text((if (r.pass) "PASS  " else "FAIL  ") + r.phrase.text, style = MaterialTheme.typography.titleMedium)
                        Text("got ${r.got}  ·  want ${r.phrase.expect}  ·  ${r.phrase.note}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    Surface(color = C.GreenSoft, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = C.Green)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EngineOption(title: String, sub: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        color = if (selected) C.GreenSoft else C.Surface, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(sub, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun PermissionRow(name: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(C.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text((if (granted) "✅ " else "⚪ ") + name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!granted) TextButton(onClick = onFix) { Text("Chalu karein", color = C.Green, style = MaterialTheme.typography.titleMedium) }
    }
}
