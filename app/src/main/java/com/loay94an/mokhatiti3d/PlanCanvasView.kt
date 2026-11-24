package com.loay94an.mokhatiti3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.round

/** Metric editor: geometry is stored in metres, independent of screen resolution. */
class PlanCanvasView(context: Context) : View(context) {
    val plan = mutableListOf<PlanWall>()
    private val undoStack = ArrayDeque<List<PlanWall>>()
    private val prefs get() = context.getSharedPreferences("mokhatiti_native", Context.MODE_PRIVATE)
    private var pixelsPerMetre = 72f
    private var panX = 0f
    private var panY = 0f
    private var drawing = false
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226,233,241); strokeWidth = 1f }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(193,205,219); strokeWidth = 2f }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25,48,75); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22,163,148); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(93,110,130); textSize = 25f }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14,132,122); textSize = 22f; typeface = Typeface.DEFAULT_BOLD }

    init { setBackgroundColor(Color.rgb(250,252,255)); isFocusable = true; loadPlan() }

    private fun originX() = width * .5f + panX
    private fun originY() = height * .55f + panY
    private fun screenX(m: Float) = originX() + m * pixelsPerMetre
    private fun screenY(m: Float) = originY() - m * pixelsPerMetre
    private fun worldX(px: Float) = snap((px-originX())/pixelsPerMetre)
    private fun worldY(py: Float) = snap((originY()-py)/pixelsPerMetre)
    private fun snap(v: Float) = round(v*10f)/10f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGrid(canvas)
        canvas.drawText("مخطط ثنائي الأبعاد", 22f, 34f, textPaint)
        canvas.drawText("الشبكة 10 سم  •  المقياس " + pixelsPerMetre.toInt() + " px/م", 22f, 64f, textPaint)
        plan.forEach { wall ->
            val x1=screenX(wall.x1); val y1=screenY(wall.y1); val x2=screenX(wall.x2); val y2=screenY(wall.y2)
            canvas.drawLine(x1,y1,x2,y2,wallPaint)
            canvas.drawText(String.format("%.2f م",wall.lengthMetres),(x1+x2)/2f+8f,(y1+y2)/2f-10f,measurePaint)
        }
        if (drawing) {
            canvas.drawLine(screenX(startX),screenY(startY),screenX(endX),screenY(endY),previewPaint)
            val len=kotlin.math.sqrt((endX-startX)*(endX-startX)+(endY-startY)*(endY-startY))
            canvas.drawText(String.format("%.2f م",len),screenX(endX)+12f,screenY(endY)-12f,measurePaint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val step=pixelsPerMetre*.5f
        var x=originX()%step
        while(x<width){canvas.drawLine(x,0f,x,height.toFloat(),gridPaint);x+=step}
        var y=originY()%step
        while(y<height){canvas.drawLine(0f,y,width.toFloat(),y,gridPaint);y+=step}
        canvas.drawLine(originX(),0f,originX(),height.toFloat(),axisPaint)
        canvas.drawLine(0f,originY(),width.toFloat(),originY(),axisPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=worldX(event.x);startY=worldY(event.y);endX=startX;endY=startY;drawing=true;invalidate();return true }
            MotionEvent.ACTION_MOVE -> { endX=worldX(event.x);endY=worldY(event.y);invalidate();return true }
            MotionEvent.ACTION_UP -> {
                endX=worldX(event.x);endY=worldY(event.y)
                if(abs(endX-startX)+abs(endY-startY)>=.2f){rememberUndo();plan.add(PlanWall(
                    w.getDouble("x1").toFloat(), w.getDouble("y1").toFloat(),
                    w.getDouble("x2").toFloat(), w.getDouble("y2").toFloat(),
                    w.optDouble("thickness", PlanWall.DEFAULT_WALL_THICKNESS_METRES.toDouble()).toFloat(),
                    w.optDouble("height", PlanWall.DEFAULT_WALL_HEIGHT_METRES.toDouble()).toFloat(),
                    w.optString("id", java.util.UUID.randomUUID().toString()),
                    w.optString("levelId", PlanWall.DEFAULT_LEVEL_ID)
                )).toFloat(),
                    w.optDouble("height", PlanWall.DEFAULT_WALL_HEIGHT_METRES.toDouble()).toFloat(),
                    w.optString("id", java.util.UUID.randomUUID().toString()),
                    w.optString("levelId", PlanWall.DEFAULT_LEVEL_ID)
                ));savePlan()}
                drawing=false;invalidate();performClick();return true
            }
            MotionEvent.ACTION_CANCEL -> {drawing=false;invalidate();return true}
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private fun rememberUndo() { undoStack.addLast(plan.toList()); if(undoStack.size>40) undoStack.removeFirst() }
    fun undo() {
        if(undoStack.isNotEmpty()){plan.clear();plan.addAll(undoStack.removeLast())}
        else if(plan.isNotEmpty()) plan.removeAt(plan.lastIndex)
        savePlan();invalidate()
    }
    fun clearPlan(){if(plan.isNotEmpty())rememberUndo();plan.clear();savePlan();invalidate()}

    fun savePlan() {
        val walls=JSONArray()
        plan.forEach { w -> walls.put(JSONObject().put("id",w.id).put("levelId",w.levelId).put("x1",w.x1).put("y1",w.y1).put("x2",w.x2).put("y2",w.y2).put("thickness",w.thickness).put("height",w.height)) }
        val project=JSONObject().put("format","mokhatiti-native").put("version",3).put("unit","metre").put("walls",walls)
        prefs.edit().putString("plan_json",project.toString()).apply()
    }
    fun loadPlan() {
        val raw=prefs.getString("plan_json",null)?:return
        try {
            val root=JSONObject(raw)
            val walls=if(root.has("walls"))root.getJSONArray("walls") else JSONArray(raw)
            plan.clear()
            for (i in 0 until walls.length()) {
                val w = walls.getJSONObject(i)
                plan.add(
                    PlanWall(
                        w.getDouble("x1").toFloat(),
                        w.getDouble("y1").toFloat(),
                        w.getDouble("x2").toFloat(),
                        w.getDouble("y2").toFloat(),
                        w.optDouble("thickness", PlanWall.DEFAULT_WALL_THICKNESS_METRES.toDouble()).toFloat(),
                        w.optDouble("height", PlanWall.DEFAULT_WALL_HEIGHT_METRES.toDouble()).toFloat(),
                        w.optString("id", java.util.UUID.randomUUID().toString()),
                        w.optString("levelId", PlanWall.DEFAULT_LEVEL_ID)
                    )
                )
            }
            invalidate()
        } catch (_:Exception) { /* preserve editor availability if local data is invalid */ }
    }
}
