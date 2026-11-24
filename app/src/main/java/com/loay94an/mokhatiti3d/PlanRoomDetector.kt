package com.loay94an.mokhatiti3d

import kotlin.math.atan2
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
            val key = VertexKey(round(point.x / endpointToleranceMetres).toLong(), round(point.y / endpointToleranceMetres).toLong())
            return indices.getOrPut(key) { points.add(point); points.lastIndex }
        }

        val edges = walls.map { vertex(it.start) to vertex(it.end) }.filter { it.first != it.second }
        val adjacency = MutableList(points.size) { linkedSetOf<Int>() }
        edges.forEach { (a, b) -> adjacency[a].add(b); adjacency[b].add(a) }
        val ordered = adjacency.mapIndexed { index, neighbours ->
            neighbours.sortedBy { other -> atan2((points[other].y - points[index].y).toDouble(), (points[other].x - points[index].x).toDouble()) }
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
                    if (edge in visited) { closed = from == start && to == firstNext; break }
                    visited.add(edge)
                    cycle.add(from)
                    val neighbours = ordered[to]
                    val reverse = neighbours.indexOf(from)
                    if (reverse < 0 || neighbours.isEmpty()) break
                    val next = neighbours[(reverse - 1 + neighbours.size) % neighbours.size]
                    from = to
                    to = next
                    if (from == start && to == firstNext) { closed = true; break }
                }
                if (!closed || cycle.size < 3) continue
                val polygon = cycle.map { points[it] }
                var twiceArea = 0.0
                polygon.indices.forEach { i ->
                    val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
                    twiceArea += a.x.toDouble() * b.y - b.x.toDouble() * a.y
                }
                val area = twiceArea / 2.0
                if (area < minimumAreaSquareMetres) continue
                val rotations = cycle.indices.map { offset -> (cycle.drop(offset) + cycle.take(offset)).joinToString(",") }
                val canonical = rotations.minOrNull() ?: continue
                if (!cycles.add(canonical)) continue
                rooms.add(PlanRoom("room-$canonical", "غرفة ${rooms.size + 1}", polygon))
            }
        }
        return rooms
    }
}