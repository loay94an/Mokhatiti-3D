package com.loay94an.mokhatiti3d

/** Ear-clipping triangulation for simple concave room polygons. Returns empty for invalid polygons. */
object PlanPolygonTriangulator {
    fun triangulate(polygon: List<PlanPoint>): List<Triple<PlanPoint, PlanPoint, PlanPoint>> {
        if (polygon.size < 3) return emptyList()
        val area = signedArea(polygon)
        if (kotlin.math.abs(area) < 1e-7) return emptyList()
        val orientation = if (area > 0.0) 1.0 else -1.0
        val remaining = polygon.indices.toMutableList()
        val triangles = mutableListOf<Triple<PlanPoint, PlanPoint, PlanPoint>>()
        var attempts = 0
        while (remaining.size > 3 && attempts < polygon.size * polygon.size) {
            var clipped = false
            for (i in remaining.indices) {
                val previous = remaining[(i - 1 + remaining.size) % remaining.size]
                val current = remaining[i]
                val next = remaining[(i + 1) % remaining.size]
                val a = polygon[previous]; val b = polygon[current]; val c = polygon[next]
                if (cross(a, b, c) * orientation <= 1e-7) continue
                val containsVertex = remaining.any { candidate ->
                    candidate != previous && candidate != current && candidate != next &&
                        insideTriangle(polygon[candidate], a, b, c, orientation)
                }
                if (containsVertex) continue
                triangles.add(Triple(a, b, c))
                remaining.removeAt(i)
                clipped = true
                break
            }
            if (!clipped) return emptyList()
            attempts++
        }
        if (remaining.size == 3) triangles.add(Triple(polygon[remaining[0]], polygon[remaining[1]], polygon[remaining[2]]))
        return triangles
    }

    private fun signedArea(points: List<PlanPoint>): Double {
        var twice = 0.0
        points.indices.forEach { i ->
            val a = points[i]; val b = points[(i + 1) % points.size]
            twice += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return twice / 2.0
    }

    private fun cross(a: PlanPoint, b: PlanPoint, c: PlanPoint): Double =
        (b.x - a.x).toDouble() * (c.y - b.y) - (b.y - a.y).toDouble() * (c.x - b.x)

    private fun insideTriangle(p: PlanPoint, a: PlanPoint, b: PlanPoint, c: PlanPoint, orientation: Double): Boolean {
        val ab = cross(a, b, p) * orientation
        val bc = cross(b, c, p) * orientation
        val ca = cross(c, a, p) * orientation
        return ab >= -1e-7 && bc >= -1e-7 && ca >= -1e-7
    }
}