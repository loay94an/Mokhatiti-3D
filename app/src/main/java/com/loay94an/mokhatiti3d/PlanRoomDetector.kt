package com.loay94an.mokhatiti3d

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.round

/** Extract bounded faces from wall center-lines using a planar half-edge graph. */
object PlanRoomDetector {
    fun detectRooms(
        walls: List<PlanWall>,
        endpointToleranceMetres: Float = 0.04f,
        minimumAreaSquareMetres: Float = 0.25f
    ): List<PlanRoom> {
        require(endpointToleranceMetres > 0f)
        require(minimumAreaSquareMetres >= 0f)
        if (walls.size < 3) return emptyList()

        data class VertexKey(val x: Long, val y: Long)
        data class DirectedEdge(val from: Int, val to: Int)
        val points = mutableListOf<PlanPoint>()
        val indices = linkedMapOf<VertexKey, Int>()
        fun vertex(point: PlanPoint): Int {
            val key = VertexKey(
                round(point.x / endpointToleranceMetres).toLong(),
                round(point.y / endpointToleranceMetres).toLong()
            )
            return indices.getOrPut(key) { points.add(point); points.lastIndex }
        }

        val segments = walls.map { vertex(it.start) to vertex(it.end) }.filter { it.first != it.second }

        // Insert proper interior intersections before splitting segments. Endpoint-on-segment
        // T junctions are handled by the projection pass below; this pass handles X crossings.
        fun cross(ax: Float, ay: Float, bx: Float, by: Float): Double =
            ax.toDouble() * by - ay.toDouble() * bx
        for (i in segments.indices) {
            val (aIndex, bIndex) = segments[i]
            val a = points[aIndex]
            val b = points[bIndex]
            val rx = b.x - a.x
            val ry = b.y - a.y
            for (j in i + 1 until segments.size) {
                val (cIndex, dIndex) = segments[j]
                val c = points[cIndex]
                val d = points[dIndex]
                val sx = d.x - c.x
                val sy = d.y - c.y
                val denominator = cross(rx, ry, sx, sy)
                if (kotlin.math.abs(denominator) <= 1e-9) continue // parallel or collinear
                val qpx = c.x - a.x
                val qpy = c.y - a.y
                val t = cross(qpx, qpy, sx, sy) / denominator
                val u = cross(qpx, qpy, rx, ry) / denominator
                if (t > 1e-6 && t < 1.0 - 1e-6 && u > 1e-6 && u < 1.0 - 1e-6) {
                    vertex(PlanPoint((a.x + t.toFloat() * rx), (a.y + t.toFloat() * ry)))
                }
            }
        }

        // Split each source segment at every known endpoint or inserted intersection on it.
        val edges = linkedSetOf<Pair<Int, Int>>()
        segments.forEach { (a, b) ->
            val start = points[a]
            val end = points[b]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 1e-12f) return@forEach
            val onSegment = points.indices.mapNotNull { candidate ->
                val point = points[candidate]
                val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
                if (t < -0.0001f || t > 1.0001f) return@mapNotNull null
                val projectedX = start.x + t * dx
                val projectedY = start.y + t * dy
                val distance = hypot(point.x - projectedX, point.y - projectedY)
                if (distance <= endpointToleranceMetres) t to candidate else null
            }.sortedBy { it.first }
            onSegment.zipWithNext().forEach { (left, right) ->
                if (left.second != right.second) {
                    val edge = minOf(left.second, right.second) to maxOf(left.second, right.second)
                    edges.add(edge)
                }
            }
        }

        val adjacency = MutableList(points.size) { linkedSetOf<Int>() }
        edges.forEach { (a, b) -> adjacency[a].add(b); adjacency[b].add(a) }
        val ordered = adjacency.mapIndexed { index, neighbours ->
            neighbours.sortedBy { other ->
                atan2((points[other].y - points[index].y).toDouble(), (points[other].x - points[index].x).toDouble())
            }
        }
        val visited = hashSetOf<DirectedEdge>()
        val rooms = mutableListOf<PlanRoom>()
        val cycles = hashSetOf<String>()

        for (start in ordered.indices) {
            for (firstNext in ordered[start]) {
                val first = DirectedEdge(start, firstNext)
                if (first in visited) continue
                var from = start
                var to = firstNext
                val cycle = mutableListOf<Int>()
                var closed = false
                var steps = 0
                val maxSteps = edges.size * 2 + 2
                while (steps++ < maxSteps) {
                    val edge = DirectedEdge(from, to)
                    if (edge in visited) {
                        closed = from == start && to == firstNext
                        break
                    }
                    visited.add(edge)
                    cycle.add(from)
                    val neighbours = ordered[to]
                    val reverse = neighbours.indexOf(from)
                    if (reverse < 0 || neighbours.isEmpty()) break
                    val next = neighbours[(reverse - 1 + neighbours.size) % neighbours.size]
                    from = to
                    to = next
                    if (from == start && to == firstNext) {
                        closed = true
                        break
                    }
                }
                if (!closed || cycle.size < 3) continue
                val polygon = cycle.map { points[it] }
                var twiceArea = 0.0
                polygon.indices.forEach { index ->
                    val a = polygon[index]
                    val b = polygon[(index + 1) % polygon.size]
                    twiceArea += a.x.toDouble() * b.y - b.x.toDouble() * a.y
                }
                val area = twiceArea / 2.0
                if (area < minimumAreaSquareMetres) continue
                val rotations = cycle.indices.map { offset ->
                    (cycle.drop(offset) + cycle.take(offset)).joinToString(",")
                }
                val canonical = rotations.minOrNull() ?: continue
                if (!cycles.add(canonical)) continue
                rooms.add(PlanRoom("room-$canonical", "غرفة ${rooms.size + 1}", polygon))
            }
        }
        return rooms
    }
}
