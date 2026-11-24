package com.loay94an.mokhatiti3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanGeometryTest {
    @Test
    fun wallLengthAndFootprintUseMetres() {
        val wall = PlanWall(0f, 0f, 3f, 4f, thickness = 0.2f)
        assertEquals(5f, wall.lengthMetres, 0.0001f)
        assertEquals(1f, wall.footprintAreaSquareMetres, 0.0001f)
        assertEquals(4, wall.footprint().size)
    }

    @Test
    fun rejectsNonFiniteAndDegenerateWalls() {
        assertThrows(IllegalArgumentException::class.java) {
            PlanWall(Float.NaN, 0f, 1f, 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlanWall(0f, 0f, 0.01f, 0f)
        }
    }

    @Test
    fun roomAreaIsIndependentOfPolygonWinding() {
        val clockwise = listOf(
            PlanPoint(0f, 0f), PlanPoint(4f, 0f),
            PlanPoint(4f, 3f), PlanPoint(0f, 3f)
        )
        val counterClockwise = clockwise.reversed()
        assertEquals(12f, PlanRoom("r1", "غرفة", clockwise).areaSquareMetres, 0.0001f)
        assertEquals(12f, PlanRoom("r2", "غرفة", counterClockwise).areaSquareMetres, 0.0001f)
    }

    @Test
    fun projectValidatesLevelReferencesAndCalculatesBounds() {
        val wall = PlanWall(1f, 2f, 5f, 7f)
        val project = PlanProject(walls = listOf(wall))
        assertEquals(5f, project.totalWallLengthMetres, 0.0001f)
        assertNotNull(project.bounds)
        assertEquals(1f, project.bounds!!.minX, 0.0001f)
        assertThrows(IllegalArgumentException::class.java) {
            PlanProject(walls = listOf(wall.copy(levelId = "missing")))
        }
    }
}
