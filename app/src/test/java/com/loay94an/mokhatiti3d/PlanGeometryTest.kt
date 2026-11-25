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
        assertEquals(kotlin.math.sqrt(41f), project.totalWallLengthMetres, 0.0001f)
        assertNotNull(project.bounds)
        assertEquals(1f, project.bounds!!.minX, 0.0001f)
        assertThrows(IllegalArgumentException::class.java) {
            PlanProject(walls = listOf(wall.copy(levelId = "missing")))
        }
    }
    @Test
    fun validatesDoorAndWindowOpeningsAgainstHostWall() {
        val wall = PlanWall(0f, 0f, 4f, 0f, height = 2.8f)
        val door = PlanOpening(wallId = wall.id, type = PlanOpeningType.DOOR, offsetMetres = 1f, widthMetres = 0.9f, heightMetres = 2.1f)
        val window = PlanOpening(wallId = wall.id, type = PlanOpeningType.WINDOW, offsetMetres = 2f, widthMetres = 1.2f, heightMetres = 1f, sillHeightMetres = 0.9f)
        PlanProject(walls = listOf(wall), openings = listOf(door, window))
        assertThrows(IllegalArgumentException::class.java) {
            PlanProject(walls = listOf(wall), openings = listOf(door.copy(offsetMetres = 3.5f)))
        }
    }

    @Test
    fun rejectsOverlappingOpeningsOnTheSameWall() {
        val wall = PlanWall(0f, 0f, 5f, 0f)
        val first = PlanOpening(wallId = wall.id, type = PlanOpeningType.DOOR, offsetMetres = 1f, widthMetres = 1f, heightMetres = 2f)
        val second = PlanOpening(wallId = wall.id, type = PlanOpeningType.WINDOW, offsetMetres = 1.5f, widthMetres = 1f, heightMetres = 1f, sillHeightMetres = 1f)
        assertThrows(IllegalArgumentException::class.java) {
            PlanProject(walls = listOf(wall), openings = listOf(first, second))
        }
    }

    @Test
    fun rejectsZeroAreaRooms() {
        assertThrows(IllegalArgumentException::class.java) {
            PlanRoom("flat", "غرفة", listOf(PlanPoint(0f, 0f), PlanPoint(1f, 0f), PlanPoint(2f, 0f)))
        }
    }
    @Test
    fun furnitureFootprintRotatesAroundItsCenter() {
        val item = PlanFurniture(name = "طاولة", category = "طاولات", x = 3f, y = 4f, width = 2f, depth = 1f, height = 0.8f, rotationDegrees = 90f)
        val points = item.footprint()
        assertEquals(1f, points.maxOf { it.x } - points.minOf { it.x }, 0.001f)
        assertEquals(2f, points.maxOf { it.y } - points.minOf { it.y }, 0.001f)
    }

    @Test
    fun validatesFurnitureLevelReference() {
        val item = PlanFurniture(name = "كرسي", category = "جلوس", x = 0f, y = 0f, width = 0.5f, depth = 0.5f, height = 0.9f, levelId = "missing")
        assertThrows(IllegalArgumentException::class.java) { PlanProject(furniture = listOf(item)) }
    }
    @Test
    fun rejectsOpeningWithoutHostWall() {
        val opening = PlanOpening(wallId = "missing-wall", type = PlanOpeningType.DOOR, offsetMetres = 0f, widthMetres = 0.9f, heightMetres = 2.1f)
        assertThrows(IllegalArgumentException::class.java) {
            PlanProject(openings = listOf(opening))
        }
    }

    @Test
    fun rejectsDuplicateLevelIdentifiers() {
        val duplicate = PlanLevel(PlanWall.DEFAULT_LEVEL_ID, "طابق مكرر")
        assertThrows(IllegalArgumentException::class.java) {
            PlanProject(levels = listOf(PlanLevel(PlanWall.DEFAULT_LEVEL_ID, "الأرضي"), duplicate))
        }
    }

    @Test
    fun validatesWallsAndFurnitureAcrossMultipleLevels() {
        val ground = PlanLevel(PlanWall.DEFAULT_LEVEL_ID, "الأرضي", elevationMetres = 0f)
        val upper = PlanLevel("level-upper", "الأول", elevationMetres = 3f)
        val upperWall = PlanWall(0f, 0f, 4f, 0f, levelId = upper.id)
        val chair = PlanFurniture(name = "كرسي", category = "جلوس", x = 1f, y = 1f, width = 0.5f, depth = 0.5f, height = 0.9f, levelId = upper.id)
        val project = PlanProject(walls = listOf(upperWall), furniture = listOf(chair), levels = listOf(ground, upper))
        assertEquals(2, project.levels.size)
        assertEquals(upper.id, project.walls.single().levelId)
    }
}
