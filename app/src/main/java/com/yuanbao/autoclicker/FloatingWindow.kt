package com.yuanbao.autoclicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮控制面板：开始点击后显示，可在任意界面暂停 / 继续 / 停止，支持拖动。
 */
object FloatingWindow {

    private var wm: WindowManager? = null
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var tvState: TextView? = null
    private var btnToggle: Button? = null

    var onToggle: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(context: Context, v: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v,
            context.resources.displayMetrics
        ).toInt()

    private fun bg(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
        }

    fun show(context: Context) {
        if (root != null) return
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val c = context.applicationContext

        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 10f), dp(c, 10f), dp(c, 10f), dp(c, 10f))
            background = bg(0xDD2B2B2B.toInt(), 14f)
        }

        val title = TextView(c).apply {
            text = "自动点击器"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        container.addView(title)

        val state = TextView(c).apply {
            text = "运行中"
            setTextColor(0xFF9CCC65.toInt())
            textSize = 12f
            setPadding(0, dp(c, 4f), 0, dp(c, 8f))
        }
        tvState = state
        container.addView(state)

        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }

        val toggle = Button(c).apply {
            text = "暂停"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(c, 10f), 0, dp(c, 10f), 0)
            setOnClickListener { onToggle?.invoke() }
        }
        btnToggle = toggle
        row.addView(toggle)

        val stop = Button(c).apply {
            text = "停止"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = bg(0xFFE53935.toInt(), 10f)
            setPadding(dp(c, 10f), 0, dp(c, 10f), 0)
            setOnClickListener { onStop?.invoke() }
        }
        row.addView(stop, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(c, 8f) })

        container.addView(row)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }

        attachDrag(container, lp, manager)

        try {
            manager.addView(container, lp)
            wm = manager
            root = container
            params = lp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(view: View, lp: WindowManager.LayoutParams, manager: WindowManager) {
        var startX = 0f
        var startY = 0f
        var originX = 0
        var originY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    originX = lp.x
                    originY = lp.y
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    if (!moved && kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        lp.x = originX + dx
                        lp.y = originY + dy
                        try {
                            manager.updateViewLayout(view, lp)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    moved
                }
                else -> false
            }
        }
    }

    fun update(running: Boolean, paused: Boolean, text: String) {
        tvState?.text = text
        tvState?.setTextColor(
            if (!running) 0xFFB0BEC5.toInt()
            else if (paused) 0xFFFFCA28.toInt()
            else 0xFF9CCC65.toInt()
        )
        btnToggle?.text = if (paused) "继续" else "暂停"
    }

    fun hide() {
        try {
            root?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        root = null
        wm = null
        params = null
        tvState = null
        btnToggle = null
    }
}
