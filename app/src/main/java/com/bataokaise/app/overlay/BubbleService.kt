package com.bataokaise.app.overlay

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bataokaise.app.BataoApp
import com.bataokaise.app.R
import com.bataokaise.app.core.Phase
import com.bataokaise.app.guide.GuideBus
import com.bataokaise.app.ui.BataoTheme
import com.bataokaise.app.ui.C
import com.bataokaise.app.ui.MainActivity

/**
 * Floating mic + Hinglish bubble that stays above any app (TYPE_APPLICATION_OVERLAY).
 * It never covers the button being highlighted: it moves to the other half of the screen.
 */
class BubbleService : LifecycleService(), SavedStateRegistryOwner {
    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
    private var view: ComposeView? = null
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams

    override fun onCreate() {
        ssrc.performAttach(); ssrc.performRestore(null)
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        startAsForeground()
        addView()
    }

    private fun startAsForeground() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, BataoApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Batao Kaise")
            .setContentText("Madad ke liye taiyaar. Bubble dabaiye.")
            .setContentIntent(open).setOngoing(true).build()
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or (if (micOk) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } else if (Build.VERSION.SDK_INT >= 30 && micOk) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, 7, n, type) }
            .onFailure { ServiceCompat.startForeground(this, 7, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) }
    }

    private fun addView() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 24; y = 160 }
        val cv = ComposeView(this)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent { BataoTheme { Floating() } }
        view = cv
        wm.addView(cv, lp)
    }

    @Composable
    private fun Floating() {
        val app = BataoApp.instance
        val s by app.assistant.state.collectAsState()
        val target by GuideBus.target.collectAsState()
        val screenH = resources.displayMetrics.heightPixels
        // Keep clear of the highlighted target.
        LaunchedEffect(target) {
            val t = target ?: return@LaunchedEffect
            val wantTop = t.centerY() > screenH / 2
            val newGravity = (if (wantTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.START
            if (lp.gravity != newGravity) { lp.gravity = newGravity; lp.y = if (wantTop) 120 else 160; view?.let { wm.updateViewLayout(it, lp) } }
        }
        val showBubble = s.phase != Phase.IDLE || s.showYesNo || s.showDone
        Column(Modifier.widthIn(max = 340.dp).pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                lp.x = (lp.x + drag.x).toInt().coerceAtLeast(0)
                lp.y = (lp.y + if (lp.gravity and Gravity.BOTTOM == Gravity.BOTTOM) -drag.y else drag.y).toInt().coerceAtLeast(0)
                view?.let { wm.updateViewLayout(it, lp) }
            }
        }) {
            if (showBubble) {
                Surface(color = C.Surface, shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp,
                    modifier = Modifier.border(2.dp, C.Green, RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(14.dp)) {
                        Text(s.bubble, style = MaterialTheme.typography.titleLarge)
                        if (s.live.isNotBlank() && s.phase == Phase.LISTENING) Text("“${s.live}”", color = C.Green, style = MaterialTheme.typography.bodyMedium)
                        if (s.showYesNo) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SmallAction("✅ Haan", Modifier.weight(1f)) { app.assistant.yes() }
                            SmallAction("❌ Nahi", Modifier.weight(1f)) { app.assistant.no() }
                        } else if (s.showDone) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SmallAction("👍 Ho gaya", Modifier.weight(1f)) { app.assistant.done() }
                            SmallAction("🔁 Phir se", Modifier.weight(1f)) { app.assistant.repeatLast() }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val listening = s.phase == Phase.LISTENING
            Box(Modifier.size(72.dp).background(if (listening) C.Danger else C.Green, CircleShape)
                .border(3.dp, C.Surface, CircleShape).clickable { app.assistant.micTapped() },
                contentAlignment = Alignment.Center) { Text("🎤", fontSize = 30.sp) }
        }
    }

    @Composable
    private fun SmallAction(text: String, modifier: Modifier, onClick: () -> Unit) {
        Box(modifier.heightIn(min = 56.dp).background(C.GreenSoft, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleMedium, color = C.Green) }
    }

    override fun onDestroy() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    companion object {
        fun show(ctx: Context) {
            if (!Settings.canDrawOverlays(ctx)) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, BubbleService::class.java)) }
        }
        fun hide(ctx: Context) { ctx.stopService(Intent(ctx, BubbleService::class.java)) }
    }
}
