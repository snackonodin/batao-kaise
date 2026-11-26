package com.bataokaise.app.guide

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shared state between the guide service, the journey runner and the bubble. */
object GuideBus {
    /** Screen rect of the highlighted element, or null. The bubble moves away from it. */
    val target = MutableStateFlow<Rect?>(null)
    /** True only while a journey is running: the service ignores every event otherwise. */
    @Volatile var active = false
    @Volatile var service: GuideAccessibilityService? = null
    /** Receives screen snapshots while active. */
    var listener: ((pkg: String, nodes: List<NodeData>) -> Unit)? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    internal fun setConnected(v: Boolean) { _connected.value = v }
}

/**
 * Screen guide (Dikhao). Reads the current app's view tree ONLY while a journey runs,
 * and draws a non-touchable TYPE_ACCESSIBILITY_OVERLAY highlight. It never performs
 * clicks, typing, swipes or global actions for the user.
 */
class GuideAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var overlay: HighlightView? = null
    private lateinit var wm: WindowManager
    private val snapshot = Runnable { takeSnapshot() }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        GuideBus.service = this
        GuideBus.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!GuideBus.active || event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Debounce bursts of content changes; recompute the highlight after scrolls and window changes.
                main.removeCallbacks(snapshot)
                main.postDelayed(snapshot, 120)
            }
        }
    }

    fun requestSnapshot() { main.removeCallbacks(snapshot); main.postDelayed(snapshot, 60) }

    private fun takeSnapshot() {
        if (!GuideBus.active) return
        val root = pickAppRoot() ?: return
        val pkg = root.packageName?.toString() ?: return
        val out = ArrayList<NodeData>(256)
        flatten(root, out, 0)
        GuideBus.listener?.invoke(pkg, out)
    }

    /** The active app window, skipping our own overlays and the keyboard. */
    private fun pickAppRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName != packageName) return active
        return runCatching {
            windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { it.layer }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName != packageName }
        }.getOrNull() ?: active
    }

    private fun flatten(n: AccessibilityNodeInfo, out: MutableList<NodeData>, depth: Int) {
        if (depth > 60 || out.size > 1500) return
        val r = Rect(); n.getBoundsInScreen(r)
        out.add(NodeData(
            viewId = n.viewIdResourceName,
            text = n.text?.toString(),
            desc = n.contentDescription?.toString(),
            hint = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString() else null,
            password = n.isPassword,
            editable = n.isEditable,
            visible = n.isVisibleToUser,
            clickable = n.isClickable,
            bounds = intArrayOf(r.left, r.top, r.right, r.bottom),
        ))
        for (i in 0 until n.childCount) n.getChild(i)?.let { flatten(it, out, depth + 1) }
    }

    // ---------- Overlay ----------

    fun showHighlight(bounds: Rect?, label: String?, arrowOnly: Boolean = false) {
        main.post {
            val v = overlay ?: createOverlay() ?: return@post
            v.update(bounds, label, arrowOnly)
            GuideBus.target.value = bounds
        }
    }

    fun hideHighlight() {
        main.post { overlay?.update(null, null, false); GuideBus.target.value = null }
    }

    fun removeOverlay() {
        main.post {
            overlay?.let { runCatching { wm.removeView(it) } }
            overlay = null
            GuideBus.target.value = null
        }
    }

    private fun createOverlay(): HighlightView? = runCatching {
        val v = HighlightView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(v, lp)
        overlay = v
        v
    }.getOrNull()

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeOverlay()
        GuideBus.service = null
        GuideBus.setConnected(false)
        return super.onUnbind(intent)
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, GuideAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
