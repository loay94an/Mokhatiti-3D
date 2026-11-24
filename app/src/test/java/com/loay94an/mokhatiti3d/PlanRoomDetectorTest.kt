package com.loay94an.mokhatiti3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRoomDetectorTest {
    @Test fun detectsOneClosedRectangularRoom() {
        val walls = listOf(
            PlanWall(0f, 0f, 4f, 0f),
            PlanWall(4f, 0f, 4f, 3f),
            PlanWall(4f, 3f, 0f, 3f),
            PlanWall(0f, 3f, 0f, 0f)
        )
        val rooms = PlanRoomDetector.detectRooms(walls)
        assertEquals(1, rooms.size)
        assertEquals(12f, rooms.single().areaSquareMetres, 0.001f)
    }

    @Test fun detectsTwoRoomsSharingAnInternalWall() {
        val walls = listOf(
            PlanWall(0f, 0f, 4f, 0f),
            PlanWall(4f, 0f, 4f, 3f),
            PlanWall(4f, 3f, 0f, 3f),
            PlanWall(0f, 3f, 0f, 0f),
            PlanWall(2f, 0f, 2f, 3f)
        )
        val rooms = PlanRoomDetector.detectRooms(walls)
        assertEquals(2, rooms.size)
        assertEquals(6f, rooms.sumOf { it.areaSquareMetres.toDouble() }.toFloat(), 0.001f)
        assertTrue(rooms.all { it.areaSquareMetres > 0f })
    }
    @Test fun joinsEndpointsWithinTolerance() {
        val walls = listOf(
            PlanWall(0f, 0f, 4f, 0f),
            PlanWall(4.02f, 0f, 4.02f, 3f),
            PlanWall(4.02f, 3f, 0f, 3f),
            PlanWall(0f, 3f, 0f, 0f)
        )
        val rooms = PlanRoomDetector.detectRooms(walls, endpointToleranceMetres = 0.05f)
        assertEquals(1, rooms.size)
        assertTrue(rooms.single().areaSquareMetres > 11.9f)
    }

}