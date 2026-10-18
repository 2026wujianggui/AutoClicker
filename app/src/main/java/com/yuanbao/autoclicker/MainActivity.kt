package com.yuanbao.autoclicker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Activity

class MainActivity : Activity() {

    private lateinit var cfg: ClickConfig

    private lateinit var tvStatus: TextView
    private lateinit var pointList: LinearLayout
    private lateinit var etInterval: EditText
    private lateinit var etJitter: EditText
    private lateinit var etRepeat: EditText
    private lateinit var etPress: EditText
    private lateinit var etX: EditText
    private lateinit var etY: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cfg = Prefs.load(this)

        tvStatus = findViewById(R.id.tvStatus)
        pointList = findViewById(R.id.pointList)
        etInterval = findViewById(R.id.etInterval)
        etJitter = findViewById(R.id.etJitter)
        etRepeat = findViewById(R.id.etRepeat)
        etPress = findViewById(R.id.etDuration)
        etX = findViewById(R.id.etX)
        etY = findViewById(R.id.etY)

        etInterval.setText(cfg.intervalMs.toString())
        etJitter.setText(cfg.jitterMs.toString())
        etRepeat.setText(cfg.repeatCount.toString())
        etPress.setText(cfg.pressMs.toString())

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnRecord).setOnClickListener { recordPoint() }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            cfg.points.clear()
            Prefs.save(this, cfg)
            renderPoints()
        }
        findViewById<Button>(R.id.btnAddManual).setOnClickListener {
            val x = etX.text.toString().trim().toIntOrNull()
            val y = etY.text.toString().trim().toIntOrNull()
            if (x == null || y == null) {
                toast("请输入有效的 X / Y 坐标")
            } else {
                cfg.points.add(ClickPoint(x, y))
                Prefs.save(this, cfg)
                renderPoints()
            }
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { doStart() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            AutoClickService.instance?.stop()
            updateStatus()
        }

        renderPoints()
    }

    override fun onResume() {
        super.onResume()
        AutoClickService.onProgress = { text ->
            runOnUiThread { tvStatus.text = "状态：$text" }
        }
        AutoClickService.onStateChanged = { runOnUiThread { updateStatus() } }
        updateStatus()
    }

    override fun onPause() {
        collect()
        super.onPause()
    }

    private fun doStart() {
        if (!accessibilityEnabled()) {
            toast("请先开启无障碍服务")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val service = AutoClickService.instance
        if (service == null) {
            toast("服务尚未就绪，请在无障碍设置中开启后重试")
            return
        }
        collect()
        if (cfg.points.isEmpty()) {
            toast("请先添加至少一个点击点")
            return
        }
        Prefs.save(this, cfg)
        service.start(cfg)
        updateStatus()
    }

    private fun recordPoint() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授权悬浮窗权限")
            requestOverlay()
            return
        }
        val service = AutoClickService.instance
        if (service == null) {
            toast("请先开启无障碍服务，再录制坐标")
            return
        }
        OverlayCapture.show(service) { x, y ->
            cfg.points.add(ClickPoint(x, y))
            Prefs.save(this, cfg)
            renderPoints()
            toast("已记录坐标 ($x, $y)")
        }
    }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            toast("悬浮窗权限已授权")
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun collect() {
        cfg.intervalMs = etInterval.text.toString().trim().toLongOrNull() ?: 1000L
        cfg.jitterMs = etJitter.text.toString().trim().toLongOrNull() ?: 0L
        cfg.repeatCount = etRepeat.text.toString().trim().toIntOrNull() ?: 0
        cfg.pressMs = (etPress.text.toString().trim().toLongOrNull() ?: 60L).coerceIn(10L, 1000L)
    }

    private fun accessibilityEnabled(): Boolean {
        val expected = "$packageName/${AutoClickService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabled.isNullOrEmpty()) return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun renderPoints() {
        pointList.removeAllViews()
        if (cfg.points.isEmpty()) {
            pointList.addView(TextView(this).apply {
                text = "还没有点击点，点「录制坐标」后在屏幕上点一下即可添加"
                setTextColor(0xFF9E9E9E.toInt())
                textSize = 13f
                setPadding(4, 8, 4, 8)
            })
            return
        }
        cfg.points.forEachIndexed { i, p ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                text = "${i + 1}.  (${p.x}, ${p.y})"
                textSize = 14f
                setTextColor(Color.DKGRAY)
            }
            row.addView(
                label,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            val del = Button(this).apply {
                text = "删除"
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
                setOnClickListener {
                    if (i < cfg.points.size) {
                        cfg.points.removeAt(i)
                        Prefs.save(this@MainActivity, cfg)
                        renderPoints()
                    }
                }
            }
            row.addView(del)
            pointList.addView(row)
        }
    }

    private fun updateStatus() {
        val acc = if (accessibilityEnabled()) "已开启" else "未开启"
        val ov = if (Settings.canDrawOverlays(this)) "已授权" else "未授权"
        val run = when {
            !AutoClickService.running -> "空闲"
            AutoClickService.paused -> "已暂停"
            else -> "运行中"
        }
        tvStatus.text = "无障碍：$acc    悬浮窗：$ov\n运行状态：$run"
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
