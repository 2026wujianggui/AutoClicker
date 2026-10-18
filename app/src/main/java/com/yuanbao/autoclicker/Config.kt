package com.yuanbao.autoclicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClickPoint(var x: Int, var y: Int)

data class ClickConfig(
    var points: MutableList<ClickPoint> = mutableListOf(),
    var intervalMs: Long = 1000L,   // 每次点击之间的基准间隔
    var jitterMs: Long = 200L,      // 随机浮动 ±，防检测
    var repeatCount: Int = 0,       // 完整轮数，0 表示无限循环
    var pressMs: Long = 60L         // 单次按下时长
)

object Prefs {
    private const val FILE = "autoclicker"
    private const val KEY = "config"

    fun save(context: Context, cfg: ClickConfig) {
        val arr = JSONArray()
        cfg.points.forEach { p ->
            arr.put(JSONObject().apply {
                put("x", p.x)
                put("y", p.y)
            })
        }
        val obj = JSONObject().apply {
            put("interval", cfg.intervalMs)
            put("jitter", cfg.jitterMs)
            put("repeat", cfg.repeatCount)
            put("press", cfg.pressMs)
            put("points", arr)
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, obj.toString()).apply()
    }

    fun load(context: Context): ClickConfig {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return ClickConfig()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("points") ?: JSONArray()
            val points = mutableListOf<ClickPoint>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                points.add(ClickPoint(p.optInt("x"), p.optInt("y")))
            }
            ClickConfig(
                points = points,
                intervalMs = obj.optLong("interval", 1000L),
                jitterMs = obj.optLong("jitter", 200L),
                repeatCount = obj.optInt("repeat", 0),
                pressMs = obj.optLong("press", 60L)
            )
        } catch (e: Exception) {
            ClickConfig()
        }
    }
}
