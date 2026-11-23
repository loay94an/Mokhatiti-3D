package com.loay94an.mokhatiti3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight native axonometric renderer for the metric wall model.
 * Each plan segment is projected as a vertical wall prism, not a decorative placeholder.
 */
class Room3DView(context: Context, private val walls: List<PlanWall>) : View(context) {
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; color = Color.rgb(55,72,92) }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65,82,102); textSize = 25f }
    private val wallHeight = 2.8f

    private fun project(x: Float, y: Float, z: Float, cx: Float, cy: Float, scale: Float): Pair<Float,Float> {
        return Pair(cx + (x-y)*scale, cy + (x+y)*scale*.43f - z*scale*.92f)
    }

    private fun polygon(canvas: Canvas, points: List<Pair<Float,Float>>, color: Int) {
        if(points.size<3)return
        val p=Path().apply { moveTo(points[0].first,points[0].second); points.drop(1).forEach { lineTo(it.first,it.second) }; close() }
        face.color=color; canvas.drawPath(p,face); canvas.drawPath(p,edge)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(244,248,252))
        canvas.drawText("المعاينة المجسمة — ارتفاع الجدار 2.8 م", 22f, 38f, caption)
        if(walls.isEmpty()) {
            canvas.drawText("أضف الجدران من وضع المخطط 2D لعرض المجسم",22f,height-30f,caption)
            return
        }
        val minX=walls.minOf { min(it.x1,it.x2) }; val maxX=walls.maxOf { max(it.x1,it.x2) }
        val minY=walls.minOf { min(it.y1,it.y2) }; val maxY=walls.maxOf { max(it.y1,it.y2) }
        val span=max(2f,max(maxX-minX,maxY-minY))
        val scale=min(width*.34f/span,height*.48f/span).coerceAtLeast(12f)
        val cx=width*.5f
        val cy=height*.52f
        val pad=.25f
        val floor=listOf(
            project(minX-pad,minY-pad,0f,cx,cy,scale),
            project(maxX+pad,minY-pad,0f,cx,cy,scale),
            project(maxX+pad,maxY+pad,0f,cx,cy,scale),
            project(minX-pad,maxY+pad,0f,cx,cy,scale)
        )
        polygon(canvas,floor,Color.rgb(224,232,239))
        walls.forEach { w ->
            val a=project(w.x1,w.y1,0f,cx,cy,scale)
            val b=project(w.x2,w.y2,0f,cx,cy,scale)
            val at=project(w.x1,w.y1,wallHeight,cx,cy,scale)
            val bt=project(w.x2,w.y2,wallHeight,cx,cy,scale)
            polygon(canvas,listOf(a,b,bt,at),Color.rgb(190,211,226))
        }
        canvas.drawText("الجدران: ${walls.size}  •  الارتفاع: 2.8 م",22f,height-26f,caption)
    }
}
