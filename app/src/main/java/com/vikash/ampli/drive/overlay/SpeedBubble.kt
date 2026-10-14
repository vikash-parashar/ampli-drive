package com.vikash.ampli.drive.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The small floating speed bubble shown over Google Maps and other apps: current speed, the
 * limit of the camera ahead and its distance. Red when over the limit. Tap returns to Car Hub;
 * drag moves it, and the spot is remembered.
 *
 * A plain Android View in a TYPE_APPLICATION_OVERLAY window (needs "Display over other apps").
 * CarMonitorService owns it: shown only while the launcher is hidden and the switch is on.
 */
class SpeedBubble(private val app: DriveApp) {
    private val wm: WindowManager? = app.getSystemService(WindowManager::class.java)
    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var job: Job? = null
    private var captionJob: Job? = null
    private var caption: TextView? = null
    private val scope = MainScope()

    val isShowing: Boolean get() = view != null

    fun canShow(): Boolean = Settings.canDrawOverlays(app)

    fun show() {
        if (view != null || wm == null || !canShow()) return
        val density = app.resources.displayMetrics.density
        val size = (96 * density).toInt()
        val v = BubbleView(app)
        val p = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = app.resources.displayMetrics
            val (x0, y0) = BubbleLogic.clamp(app.prefs.bubbleX, app.prefs.bubbleY, size, size, dm.widthPixels, dm.heightPixels)
            x = x0
            y = y0
        }
        v.setOnTouchListener(DragListener(p))
        v.contentDescription = app.getString(R.string.bubble_open_hint)
        try {
            wm.addView(v, p)
        } catch (_: Exception) {
            // Permission withdrawn between the check and here.
            return
        }
        view = v
        params = p
        job = scope.launch {
            combine(app.speedTracker.state, app.cameraAlerts.speedLimitKmh, app.cameraAlerts.current) { gps, limit, cam ->
                BubbleLogic.model(gps.speedKmh, limit ?: cam?.limitKmh, cam?.distanceMeters)
            }.collect { v.model = it }
        }
        // Haryanvi navigation prompts: the spoken line, under the bubble, for a few seconds.
        captionJob = scope.launch {
            app.navPrompter.caption.collect { text -> if (text == null) hideCaption() else showCaption(text) }
        }
    }

    private fun showCaption(text: String) {
        val bubble = params ?: return
        val dm = app.resources.displayMetrics
        val density = dm.density
        val tv = caption ?: TextView(app).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            val pad = (10 * density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            maxLines = 3
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.argb(0xE6, 0x16, 0x18, 0x1D))
            }
            setOnClickListener { openLauncher() }
        }
        tv.text = text
        val maxW = (dm.widthPixels * 0.45f).toInt()
        tv.maxWidth = maxW
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST),
        )
        val w = tv.measuredWidth
        val h = tv.measuredHeight
        // Below the bubble, or above it near the bottom edge; never off screen.
        val below = bubble.y + bubble.height + (4 * density).toInt()
        val y = if (below + h <= dm.heightPixels) below else (bubble.y - h - (4 * density).toInt()).coerceAtLeast(0)
        val x = (bubble.x + bubble.width / 2 - w / 2).coerceIn(0, (dm.widthPixels - w).coerceAtLeast(0))
        val p = (tv.layoutParams as? WindowManager.LayoutParams) ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        p.x = x
        p.y = y
        try {
            if (caption == null) {
                wm?.addView(tv, p)
                caption = tv
            } else {
                wm?.updateViewLayout(tv, p)
            }
        } catch (_: Exception) {
        }
    }

    private fun hideCaption() {
        caption?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {
            }
        }
        caption = null
    }

    fun hide() {
        job?.cancel()
        job = null
        captionJob?.cancel()
        captionJob = null
        hideCaption()
        view?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {
            }
        }
        view = null
        params = null
    }

    private fun openLauncher() {
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (_: Exception) {
        }
    }

    private inner class DragListener(private val p: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && !BubbleLogic.isTap(dx, dy)) dragging = true
                    if (dragging) {
                        val dm = app.resources.displayMetrics
                        val (x, y) = BubbleLogic.clamp(startX + dx.toInt(), startY + dy.toInt(), p.width, p.height, dm.widthPixels, dm.heightPixels)
                        p.x = x
                        p.y = y
                        try {
                            wm?.updateViewLayout(v, p)
                        } catch (_: Exception) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        app.prefs.bubbleX = p.x
                        app.prefs.bubbleY = p.y
                    } else {
                        v.performClick()
                        openLauncher()
                    }
                }
            }
            return true
        }
    }
}

/** Draws the bubble: a round badge with the speed, and a strip with limit and camera distance. */
private class BubbleView(context: Context) : View(context) {
    var model: BubbleModel = BubbleModel(null, null, null, false)
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val m = model
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2f
        fill.color = if (m.over) RED else DARK
        canvas.drawCircle(w / 2, h / 2, r * 0.96f, fill)
        ring.strokeWidth = r * 0.08f
        ring.color = if (m.limit != null) (if (m.over) Color.WHITE else RED) else Color.argb(90, 255, 255, 255)
        oval.set(r * 0.08f, r * 0.08f, w - r * 0.08f, h - r * 0.08f)
        canvas.drawOval(oval, ring)

        big.textSize = r * 0.72f
        canvas.drawText(m.speed?.toString() ?: "--", w / 2, h / 2 + r * 0.12f, big)
        small.textSize = r * 0.24f
        val line = buildString {
            if (m.limit != null) append(m.limit)
            if (m.cameraM != null) {
                if (isNotEmpty()) append(" · ")
                append(BubbleLogic.distanceText(m.cameraM))
            }
            if (isEmpty()) append("km/h")
        }
        canvas.drawText(line, w / 2, h / 2 + r * 0.55f, small)
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        private val RED = Color.rgb(0xDC, 0x26, 0x26)
        private val DARK = Color.argb(0xE6, 0x16, 0x18, 0x1D)
    }
}
