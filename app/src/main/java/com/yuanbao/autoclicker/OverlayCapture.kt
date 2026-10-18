package com.yuanbao.autoclicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 录制模式：铺一层半透明全屏遮罩，用户点哪儿就把哪儿的坐标记下来。
 */
object OverlayCapture {

    private var wm: WindowManager? = null
    private var view: View? = null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show(context: Context, onPicked: (Int, Int) -> Unit) {
        hide()
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val c = context.applicationContext
        val density = c.resources.displayMetrics.density

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x66000000)
        }

        val tip = TextView(c).apply {
            text = "点击屏幕任意位置即可记录坐标\n（点击这里取消录制）"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, (120 * density).toInt(), 0, 0)
        }
        root.addView(tip)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                hide()
                onPicked(x, y)
            }
            true
        }

        try {
            manager.addView(root, lp)
            wm = manager
            view = root
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        try {
            view?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        view = null
        wm = null
    }
}
