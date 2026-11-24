package com.loay94an.mokhatiti3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanPolygonTriangulatorTest {
    @Test fun triangulatesConcaveLShapedRoomWithoutChangingArea() {
        val polygon = listOf(
            PlanPoint(0f, 0f), PlanPoint(4f, 0f), PlanPoint(4f, 1f),
            PlanPoint(1f, 1f), PlanPoint(1f, 4f), PlanPoint(0f, 4f)
        )
        val triangles = PlanPolygonTriangulator.triangulate(polygon)
        assertEquals(polygon.size - 2, triangles.size)
        val triangleArea = triangles.sumOf { triangle ->
            val a = triangle.first; val b = triangle.second; val c = triangle.third
            kotlin.math.abs((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)).toDouble() / 2.0
        }.toFloat()
        assertEquals(7f, triangleArea, 0.001f)
        assertTrue(triangles.isNotEmpty())
    }
}