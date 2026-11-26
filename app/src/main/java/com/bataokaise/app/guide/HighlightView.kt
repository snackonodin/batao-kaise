package com.bataokaise.app.guide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Draws a pulsing outline around the target, plus an arrow pointing at it.
 * Purely visual: the window is FLAG_NOT_TOUCHABLE, so every touch goes to the app below.
 */
class HighlightView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private var target: RectF? = null
    private var label: String? = null
    private var pulse = 0f
    private val green = Color.parseColor("#1F5E3B")
    private val amber = Color.parseColor("#F2B33D")
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5 * d; color = amber }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = amber }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = green }
    private val arrowEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.WHITE }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20 * d; typeface = Typeface.DEFAULT_BOLD }
    private val loc = IntArray(2)

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { pulse = it.animatedValue as Float; if (target != null) invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    /** [screen] is in screen coordinates; we convert to this view's coordinates. */
    fun update(screen: Rect?, label: String?, arrowOnly: Boolean) {
        getLocationOnScreen(loc)
        target = screen?.let { RectF(it).apply { offset(-loc[0].toFloat(), -loc[1].toFloat()); inset(-8 * d, -8 * d) } }
        this.label = label
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val t = target ?: return
        val r = 16 * d
        // Pulsing outer glow + solid ring.
        glow.strokeWidth = (6 + 10 * pulse) * d
        glow.alpha = (150 * (1 - pulse)).toInt() + 40
        val grow = 4 * d * pulse
        c.drawRoundRect(RectF(t.left - grow, t.top - grow, t.right + grow, t.bottom + grow), r, r, glow)
        c.drawRoundRect(t, r, r, ring)

        // Arrow from the roomier side, bobbing with the pulse.
        val above = t.top > height - t.bottom
        val cx = t.centerX()
        val bob = 10 * d * pulse
        val tipY = if (above) t.top - 10 * d - bob else t.bottom + 10 * d + bob
        val dir = if (above) -1 else 1
        val head = 26 * d
        val path = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx - head, tipY + dir * head)
            lineTo(cx - head * 0.4f, tipY + dir * head)
            lineTo(cx - head * 0.4f, tipY + dir * head * 2.6f)
            lineTo(cx + head * 0.4f, tipY + dir * head * 2.6f)
            lineTo(cx + head * 0.4f, tipY + dir * head)
            lineTo(cx + head, tipY + dir * head)
            close()
        }
        c.drawPath(path, arrowPaint)
        c.drawPath(path, arrowEdge)

        // Fallback label, used only when the floating bubble is not available.
        label?.let { text ->
            val pad = 12 * d
            val w = labelText.measureText(text).coerceAtMost(width - 4 * pad)
            val ly = if (above) tipY + dir * head * 2.6f - pad - 20 * d else tipY + dir * head * 2.6f + pad
            val lx = (cx - w / 2).coerceIn(pad, width - w - pad)
            c.drawRoundRect(RectF(lx - pad, ly - pad, lx + w + pad, ly + 24 * d + pad), 14 * d, 14 * d, labelBg)
            c.drawText(text, 0, labelText.breakText(text, true, w, null), lx, ly + 20 * d, labelText)
        }
    }
}
