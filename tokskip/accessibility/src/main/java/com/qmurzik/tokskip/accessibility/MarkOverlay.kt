package com.qmurzik.tokskip.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating "Начало рекламы / Конец рекламы" panel for manual markup. It is an accessibility
 * overlay window, so it needs no "draw over other apps" permission. Drag it by the label.
 */
class MarkOverlay(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
    private val onDump: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private lateinit var label: TextView
    private lateinit var startBtn: Button

    val isShown get() = root != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (root != null) return
        val dp = context.resources.displayMetrics.density
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 14 * dp; setColor(0xCC202124.toInt()) }
        }
        label = TextView(context).apply { setTextColor(Color.WHITE); textSize = 12f; text = "TokSkip ⠿" }
        startBtn = button("▶ Начало рекламы") { onStart() }
        panel.addView(label)
        panel.addView(startBtn)
        panel.addView(button("■ Конец рекламы") { onEnd() })
        panel.addView(button("⎙ Дамп UI") { onDump() })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (8 * dp).toInt(); y = (160 * dp).toInt() }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        label.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt(); lp.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(panel, lp) }
                }
            }
            true
        }
        runCatching { wm.addView(panel, lp); root = panel }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    fun status(text: String, armed: Boolean) {
        if (root == null) return
        label.text = "TokSkip ⠿  $text"
        startBtn.alpha = if (armed) 0.5f else 1f
    }

    private fun button(text: String, onClick: () -> Unit) = Button(context).apply {
        this.text = text
        textSize = 12f
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        setOnClickListener { onClick() }
    }
}
