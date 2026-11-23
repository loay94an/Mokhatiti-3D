package com.loay94an.mokhatiti3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PlanWall(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

class PlanCanvasView(context: Context) : View(context) {
    val plan = mutableListOf<PlanWall>()
    private val history = mutableListOf<PlanWall>()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 48, 75); strokeWidth = 9f; strokeCap = Paint.Cap.ROUND }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 163, 148); strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(116, 130, 148); textSize = 26f }
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var drawing = false
    private val prefs get() = context.getSharedPreferences("mokhatiti_native", Context.MODE_PRIVATE)

    init { setBackgroundColor(Color.rgb(250, 252, 255)); isFocusable = true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = 36f
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
        canvas.drawText("مساحة العمل — مخطط ثنائي الأبعاد", 24f, 38f, textPaint)
        plan.forEach { canvas.drawLine(it.x1, it.y1, it.x2, it.y2, wallPaint) }
        if (drawing) canvas.drawLine(startX, startY, endX, endY, previewPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; endX = event.x; endY = event.y
                drawing = true; invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x; endY = event.y; invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                endX = event.x; endY = event.y
                if (abs(endX - startX) + abs(endY - startY) > 18f) {
                    history.addAll(plan)
                    plan.add(PlanWall(startX, startY, endX, endY))
                }
                drawing = false; invalidate(); return true
            }
        }
        return true
    }

    fun undo() {
        if (plan.isNotEmpty()) { plan.removeAt(plan.lastIndex); invalidate() }
    }
    fun clearPlan() { history.addAll(plan); plan.clear(); invalidate() }
    fun savePlan() {
        val array = JSONArray()
        plan.forEach { w -> array.put(JSONObject().put("x1", w.x1).put("y1", w.y1).put("x2", w.x2).put("y2", w.y2)) }
        prefs.edit().putString("plan_json", array.toString()).apply()
    }
    fun loadPlan() {
        val raw = prefs.getString("plan_json", null) ?: return
        try {
            val array = JSONArray(raw); plan.clear()
            for (i in 0 until array.length()) {
                val w = array.getJSONObject(i)
                plan.add(PlanWall(w.getDouble("x1").toFloat(), w.getDouble("y1").toFloat(), w.getDouble("x2").toFloat(), w.getDouble("y2").toFloat()))
            }
            invalidate()
        } catch (_: Exception) { }
    }
}
