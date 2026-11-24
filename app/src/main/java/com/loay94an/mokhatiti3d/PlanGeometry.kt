package com.loay94an.mokhatiti3d

import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Android-safe architectural domain model.
 *
 * Coordinates are metres in a world coordinate system. This module deliberately
 * contains no android.graphics, java.awt, Swing, WebView or renderer dependencies.
 * It is a native reimplementation of the transferable plan concepts, not a
 * verbatim copy of Sweet Home 3D's AWT-dependent classes.
 */
data class PlanPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "Point coordinates must be finite" }
    }

    fun distanceTo(other: PlanPoint): Float = hypot(other.x - x, other.y - y)
}

data class PlanBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

data class PlanWall(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val thickness: Float = DEFAULT_WALL_THICKNESS_METRES,
    val height: Float = DEFAULT_WALL_HEIGHT_METRES,
    val id: String = UUID.randomUUID().toString(),
    val levelId: String = DEFAULT_LEVEL_ID
) {
    init {
        require(listOf(x1, y1, x2, y2, thickness, height).all(Float::isFinite)) {
            "Wall geometry must contain finite values"
        }
        require(lengthMetres > MIN_WALL_LENGTH_METRES) { "Wall is too short" }
        require(thickness > 0f) { "Wall thickness must be positive" }
        require(height > 0f) { "Wall height must be positive" }
        require(id.isNotBlank() && levelId.isNotBlank()) { "Wall and level IDs are required" }
    }

    val start: PlanPoint get() = PlanPoint(x1, y1)
    val end: PlanPoint get() = PlanPoint(x2, y2)
    val lengthMetres: Float get() = start.distanceTo(end)
    val footprintAreaSquareMetres: Float get() = lengthMetres * thickness

    /** Four footprint corners, ordered around the wall in world coordinates. */
    fun footprint(): List<PlanPoint> {
        val dx = x2 - x1
        val dy = y2 - y1
        val half = thickness / 2f
        val nx = -dy / lengthMetres * half
        val ny = dx / lengthMetres * half
        return listOf(
            PlanPoint(x1 + nx, y1 + ny),
            PlanPoint(x2 + nx, y2 + ny),
            PlanPoint(x2 - nx, y2 - ny),
            PlanPoint(x1 - nx, y1 - ny)
        )
    }

    companion object {
        const val DEFAULT_WALL_THICKNESS_METRES = 0.15f
        const val DEFAULT_WALL_HEIGHT_METRES = 2.8f
        const val DEFAULT_LEVEL_ID = "level-ground"
        const val MIN_WALL_LENGTH_METRES = 0.05f
    }
}

data class PlanRoom(
    val id: String,
    val name: String,
    val boundary: List<PlanPoint>,
    val levelId: String = PlanWall.DEFAULT_LEVEL_ID
) {
    init {
        require(id.isNotBlank() && levelId.isNotBlank())
        require(boundary.size >= 3) { "A room needs at least three boundary points" }
    }

    /** Shoelace area; the result is always non-negative and measured in m². */
    val areaSquareMetres: Float
        get() {
            var twiceArea = 0.0
            boundary.indices.forEach { index ->
                val current = boundary[index]
                val next = boundary[(index + 1) % boundary.size]
                twiceArea += current.x.toDouble() * next.y - next.x.toDouble() * current.y
            }
            return (abs(twiceArea) / 2.0).toFloat()
        }

    val bounds: PlanBounds
        get() = PlanBounds(
            boundary.minOf { it.x }, boundary.minOf { it.y },
            boundary.maxOf { it.x }, boundary.maxOf { it.y }
        )
}

enum class PlanOpeningType { DOOR, WINDOW }

data class PlanOpening(
    val id: String = UUID.randomUUID().toString(),
    val wallId: String,
    val type: PlanOpeningType,
    val offsetMetres: Float,
    val widthMetres: Float,
    val heightMetres: Float,
    val sillHeightMetres: Float = 0f
) {
    init {
        require(id.isNotBlank() && wallId.isNotBlank())
        require(listOf(offsetMetres, widthMetres, heightMetres, sillHeightMetres).all(Float::isFinite))
        require(offsetMetres >= 0f && widthMetres > 0f && heightMetres > 0f && sillHeightMetres >= 0f)
    }
}
data class PlanLevel(
    val id: String,
    val name: String,
    val elevationMetres: Float = 0f,
    val floorThicknessMetres: Float = 0.2f,
    val heightMetres: Float = 2.8f,
    val visible: Boolean = true
) {
    init {
        require(id.isNotBlank())
        require(listOf(elevationMetres, floorThicknessMetres, heightMetres).all(Float::isFinite))
        require(floorThicknessMetres >= 0f && heightMetres > 0f)
    }
}

data class PlanProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "مشروع جديد",
    val walls: List<PlanWall> = emptyList(),
    val rooms: List<PlanRoom> = emptyList(),
    val openings: List<PlanOpening> = emptyList(),
    val levels: List<PlanLevel> = listOf(PlanLevel(PlanWall.DEFAULT_LEVEL_ID, "الطابق الأرضي"))
) {
    init {
        require(id.isNotBlank())
        require(levels.isNotEmpty()) { "A project must contain at least one level" }
        val levelIds = levels.map { it.id }.toSet()
        require(levelIds.size == levels.size) { "Level IDs must be unique" }
        require(walls.all { it.levelId in levelIds }) { "Every wall must reference an existing level" }
        require(rooms.all { it.levelId in levelIds }) { "Every room must reference an existing level" }
        val wallById = walls.associateBy { it.id }
        require(openings.all { opening ->
            val wall = wallById[opening.wallId] ?: return@all false
            opening.offsetMetres + opening.widthMetres <= wall.lengthMetres + 0.0001f &&
                opening.sillHeightMetres + opening.heightMetres <= wall.height + 0.0001f
        }) { "Every opening must fit inside its host wall" }
    }

    val bounds: PlanBounds?
        get() {
            if (walls.isEmpty()) return null
            val points = walls.flatMap { listOf(it.start, it.end) }
            return PlanBounds(
                points.minOf { it.x }, points.minOf { it.y },
                points.maxOf { it.x }, points.maxOf { it.y }
            )
        }

    val totalWallLengthMetres: Float get() = walls.sumOf { it.lengthMetres.toDouble() }.toFloat()
}
