package com.yuanbao.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.random.Random

/**
 * 核心服务：通过无障碍手势注入在指定坐标执行点击。
 * 支持多点循环、可调间隔、随机浮动、限定轮数、暂停/继续。
 */
class AutoClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickService? = null

        @Volatile
        var running: Boolean = false

        @Volatile
        var paused: Boolean = false

        /** 进度回调，用于界面展示，如 "第 3 轮 / 第 2 点" */
        var onProgress: ((String) -> Unit)? = null
        var onStateChanged: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var config: ClickConfig = ClickConfig()
    private var round = 0
    private var index = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        onStateChanged?.invoke()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        stop()
        instance = null
        super.onDestroy()
    }

    fun start(cfg: ClickConfig) {
        if (cfg.points.isEmpty()) return
        config = cfg
        round = 0
        index = 0
        running = true
        paused = false

        FloatingWindow.show(this)
        FloatingWindow.onToggle = { togglePause() }
        FloatingWindow.onStop = { stop() }

        onStateChanged?.invoke()
        step()
    }

    fun togglePause() {
        if (!running) return
        paused = !paused
        refreshFloat()
        if (!paused) step()
    }

    fun stop() {
        running = false
        paused = false
        handler.removeCallbacksAndMessages(null)
        FloatingWindow.hide()
        onProgress?.invoke("已停止")
        onStateChanged?.invoke()
    }

    private fun refreshFloat() {
        val total = if (config.repeatCount > 0) "/${config.repeatCount}" else "/∞"
        FloatingWindow.update(
            running = running,
            paused = paused,
            text = when {
                !running -> "已停止"
                paused -> "已暂停 第${round + 1}轮$total"
                else -> "运行中 第${round + 1}轮$total"
            }
        )
    }

    private fun step() {
        if (!running) return
        if (paused) {
            refreshFloat()
            handler.postDelayed({ step() }, 300)
            return
        }
        if (config.repeatCount > 0 && round >= config.repeatCount) {
            stop()
            return
        }
        val points = config.points
        if (points.isEmpty()) {
            stop()
            return
        }

        val p = points[index % points.size]
        performClick(p.x, p.y)

        val next = index + 1
        if (next >= points.size) {
            index = 0
            round++
        } else {
            index = next
        }

        val total = if (config.repeatCount > 0) "/${config.repeatCount}" else "/∞"
        onProgress?.invoke("第${if (round == 0) 1 else round}轮$total · 第${index + 1}点")
        refreshFloat()

        val delay = (config.intervalMs + randomJitter()).coerceAtLeast(30L)
        handler.postDelayed({ step() }, delay)
    }

    /** 随机浮动：在 ±jitter 之间取值，让点击节奏不机械 */
    private fun randomJitter(): Long {
        val j = abs(config.jitterMs)
        if (j == 0L) return 0L
        return Random.nextLong(-j, j + 1)
    }

    private fun performClick(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        val press = config.pressMs.coerceIn(10L, 1000L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, press))
            .build()
        try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
