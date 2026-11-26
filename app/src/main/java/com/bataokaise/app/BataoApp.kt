package com.bataokaise.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bataokaise.app.core.Assistant
import com.bataokaise.app.core.Tasks
import com.bataokaise.app.intent.LlmIntentEngine
import com.bataokaise.app.intent.RuleIntentEngine
import com.bataokaise.app.ui.MainActivity
import com.bataokaise.app.voice.AndroidSpeechInput
import com.bataokaise.app.voice.SpeechInput
import com.bataokaise.app.voice.Speaker
import com.bataokaise.app.voice.TextBoxInput
import com.bataokaise.app.voice.WisprFlowInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Route { HOME, BILL, LEDGER, SCHEMES, RESCUE, SETTINGS, DEBUG, ONBOARDING }

enum class VoiceEngine { ANDROID, FLOW_DICTATION, WISPR_API }

class Settings(context: Context) {
    private val p = context.getSharedPreferences("batao_settings", Context.MODE_PRIVATE)
    var onboarded: Boolean get() = p.getBoolean("onboarded", false); set(v) = p.edit().putBoolean("onboarded", v).apply()
    var engine: VoiceEngine
        get() = runCatching { VoiceEngine.valueOf(p.getString("engine", null) ?: "") }.getOrDefault(VoiceEngine.ANDROID)
        set(v) = p.edit().putString("engine", v.name).apply()
    var upiId: String get() = p.getString("upi", "sunita.kirana@demoupi")!!; set(v) = p.edit().putString("upi", v).apply()
    var shopName: String get() = p.getString("shop", "Sunita Kirana Store")!!; set(v) = p.edit().putString("shop", v).apply()
    var trustedName: String get() = p.getString("trusted", "Beta (Rahul)")!!; set(v) = p.edit().putString("trusted", v).apply()
    var useLlm: Boolean get() = p.getBoolean("llm", false); set(v) = p.edit().putBoolean("llm", v).apply()
    /** Consent before any data leaves the phone (LLM intent, Wispr Flow, sharing). */
    var cloudConsent: Boolean get() = p.getBoolean("cloud_consent", false); set(v) = p.edit().putBoolean("cloud_consent", v).apply()
    var a11yRefused: Boolean get() = p.getBoolean("a11y_refused", false); set(v) = p.edit().putBoolean("a11y_refused", v).apply()
    val backendUrl: String get() = BuildConfig.BACKEND_URL
    /** Demo Mode = no backend configured. Every demo feature works offline in this mode. */
    val demoMode: Boolean get() = backendUrl.isBlank()
}

class Nav {
    private val _route = MutableStateFlow(Route.HOME)
    val route: StateFlow<Route> = _route
    fun go(r: Route) { _route.value = r }
}

class BataoApp : Application() {
    lateinit var settings: Settings
    lateinit var speaker: Speaker
    lateinit var assistant: Assistant
    lateinit var tasks: Tasks
    val nav = Nav()
    /** Shared text box: "type instead" and Wispr Flow app dictation both feed it. */
    val textBox = TextBoxInput("Voice: Wispr Flow app dictation (type box)", isWispr = true)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        speaker = Speaker(this)
        assistant = Assistant(speaker, buildVoice())
        applyIntentEngine()
        tasks = Tasks(this, assistant)
        tasks.attach()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
    }

    fun buildVoice(): SpeechInput = when (settings.engine) {
        VoiceEngine.ANDROID -> AndroidSpeechInput(this)
        VoiceEngine.FLOW_DICTATION -> textBox
        VoiceEngine.WISPR_API ->
            if (!settings.demoMode && settings.cloudConsent) WisprFlowInput(this, settings.backendUrl) else AndroidSpeechInput(this)
    }

    fun applyVoice() { assistant.voice = buildVoice() }

    fun applyIntentEngine() {
        assistant.intentEngine =
            if (settings.useLlm && !settings.demoMode && settings.cloudConsent) LlmIntentEngine(settings.backendUrl)
            else RuleIntentEngine()
    }

    fun online(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Bring our own UI to the front on a given screen. */
    fun open(route: Route) {
        nav.go(route)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    companion object {
        const val CHANNEL = "batao_helper"
        lateinit var instance: BataoApp
    }
}
