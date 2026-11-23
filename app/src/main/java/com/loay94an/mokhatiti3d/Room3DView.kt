package com.loay94an.mokhatiti3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** Native Canvas-based axonometric room preview; geometry is derived from the same plan model. */
class Room3DView(context: Context, private val walls: List<PlanWall>) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(55, 72, 92) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 82, 102); textSize = 25f }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(244, 248, 252))
        canvas.drawText("المعاينة المجسمة الأولية", 24f, 42f, label)
        val cx = width * .5f
        val cy = height * .56f
        val sx = max(1f, width * .28f)
        val sy = sx * .42f
        val h = max(70f, height * .34f)
        fun poly(points: Array<FloatArray>, color: Int) {
            val p = Path()
            p.moveTo(points[0][0], points[0][1])
            for (i in 1 until points.size) p.lineTo(points[i][0], points[i][1])
            p.close(); fill.color = color; canvas.drawPath(p, fill); canvas.drawPath(p, stroke)
        }
        val a = floatArrayOf(cx, cy - sy)
        val b = floatArrayOf(cx + sx, cy)
        val c = floatArrayOf(cx, cy + sy)
        val d = floatArrayOf(cx - sx, cy)
        poly(arrayOf(a,b,c,d), Color.rgb(224, 232, 239))
        poly(arrayOf(d,c,floatArrayOf(c[0],c[1]-h),floatArrayOf(d[0],d[1]-h)), Color.rgb(202, 218, 231))
        poly(arrayOf(b,c,floatArrayOf(c[0],c[1]-h),floatArrayOf(b[0],b[1]-h)), Color.rgb(178, 199, 216))
        poly(arrayOf(a,b,floatArrayOf(b[0],b[1]-h),floatArrayOf(a[0],a[1]-h)), Color.rgb(235, 241, 246))
        poly(arrayOf(a,d,floatArrayOf(d[0],d[1]-h),floatArrayOf(a[0],a[1]-h)), Color.rgb(216, 229, 239))
        if (walls.isNotEmpty()) {
            val count = walls.size
            canvas.drawText("عدد الجدران في النموذج: $count", 24f, height - 28f, label)
        } else {
            canvas.drawText("أضف الجدران من وضع المخطط 2D لبدء التصميم", 24f, height - 28f, label)
        }
    }
}
