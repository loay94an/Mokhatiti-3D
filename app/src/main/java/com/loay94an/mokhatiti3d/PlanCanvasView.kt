package com.loay94an.mokhatiti3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.round

enum class PlanCanvasTool { WALL, SELECT, ERASE }

/** Metric editor: geometry is stored in metres, independent of screen resolution. */
class PlanCanvasView(context: Context) : View(context) {
    val plan = mutableListOf<PlanWall>()
    val rooms = mutableListOf<PlanRoom>()
    val openings = mutableListOf<PlanOpening>()
    val furniture = mutableListOf<PlanFurniture>()
    var tool: PlanCanvasTool = PlanCanvasTool.WALL
        private set
    private var selectedWallId: String? = null
    private var selectedFurnitureId: String? = null
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private var selectionMoved = false
    private data class EditorSnapshot(val walls: List<PlanWall>, val openings: List<PlanOpening>, val furniture: List<PlanFurniture>)
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val prefs get() = context.getSharedPreferences("mokhatiti_native", Context.MODE_PRIVATE)
    private var pixelsPerMetre = 72f
    private var panX = 0f
    private var panY = 0f
    private var drawing = false
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pixelsPerMetre = (pixelsPerMetre * detector.scaleFactor).coerceIn(20f, 300f)
            invalidate()
            return true
        }
    })

    private val roomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(42, 22, 163, 148); style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226,233,241); strokeWidth = 1f }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(193,205,219); strokeWidth = 2f }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25,48,75); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
    private val furniturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 171, 92); style = Paint.Style.FILL }
    private val furnitureEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 86, 38); style = Paint.Style.STROKE; strokeWidth = 2.5f }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22,163,148); strokeWidth = 4f; style = Paint.Style.STROKE }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22,163,148); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(93,110,130); textSize = 25f }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14,132,122); textSize = 22f; typeface = Typeface.DEFAULT_BOLD }

    init { setBackgroundColor(Color.rgb(250,252,255)); isFocusable = true; loadPlan() }

    private fun originX() = width * .5f + panX
    private fun originY() = height * .55f + panY
    private fun screenX(m: Float) = originX() + m * pixelsPerMetre
    private fun screenY(m: Float) = originY() - m * pixelsPerMetre
    private fun worldX(px: Float) = snap((px-originX())/pixelsPerMetre)
    private fun worldY(py: Float) = snap((originY()-py)/pixelsPerMetre)
    private fun snap(v: Float) = round(v*10f)/10f

    private fun snappedPoint(x: Float, y: Float, reference: PlanPoint? = null): PlanPoint {
        var point = PlanPoint(snap(x), snap(y))
        val endpoint = plan.flatMap { listOf(it.start, it.end) }
            .minByOrNull { it.distanceTo(point) }
        if (endpoint != null && endpoint.distanceTo(point) <= 0.18f) point = endpoint
        if (reference != null) {
            val dx = point.x - reference.x
            val dy = point.y - reference.y
            if (abs(dx) <= 0.10f) point = point.copy(x = reference.x)
            else if (abs(dy) <= 0.10f) point = point.copy(y = reference.y)
        }
        return point
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGrid(canvas)
        rooms.forEach { room ->
            if (room.boundary.size >= 3) {
                val path = android.graphics.Path().apply {
                    moveTo(screenX(room.boundary[0].x), screenY(room.boundary[0].y))
                    room.boundary.drop(1).forEach { lineTo(screenX(it.x), screenY(it.y)) }
                    close()
                }
                canvas.drawPath(path, roomPaint)
                val centerX = room.boundary.map { it.x }.average().toFloat()
                val centerY = room.boundary.map { it.y }.average().toFloat()
                canvas.drawText(room.name + " • " + String.format("%.1f م²", room.areaSquareMetres), screenX(centerX), screenY(centerY), measurePaint)
            }
        }
        canvas.drawText("مخطط ثنائي الأبعاد", 22f, 34f, textPaint)
        canvas.drawText("الشبكة 10 سم  •  المقياس " + pixelsPerMetre.toInt() + " px/م", 22f, 64f, textPaint)
        furniture.forEach { item ->
            val corners = item.footprint()
            val path = android.graphics.Path().apply {
                moveTo(screenX(corners[0].x), screenY(corners[0].y))
                corners.drop(1).forEach { lineTo(screenX(it.x), screenY(it.y)) }
                close()
            }
            canvas.drawPath(path, furniturePaint)
            canvas.drawPath(path, furnitureEdgePaint)
            if (item.id == selectedFurnitureId) canvas.drawPath(path, selectedPaint)
            canvas.drawText(item.name, screenX(item.x), screenY(item.y), measurePaint)
        }
        plan.forEach { wall ->
            val x1=screenX(wall.x1); val y1=screenY(wall.y1); val x2=screenX(wall.x2); val y2=screenY(wall.y2)
            wallPaint.strokeWidth = (wall.thickness * pixelsPerMetre).coerceAtLeast(2f)
            drawWallSegments(canvas, wall, wallPaint)
            if (wall.id == selectedWallId) {
                selectedPaint.strokeWidth = wallPaint.strokeWidth + 8f
                drawWallSegments(canvas, wall, selectedPaint)
            }
            canvas.drawText(String.format("%.2f م",wall.lengthMetres),(x1+x2)/2f+8f,(y1+y2)/2f-10f,measurePaint)
        }
        if (drawing) {
            canvas.drawLine(screenX(startX),screenY(startY),screenX(endX),screenY(endY),previewPaint)
            val len=kotlin.math.sqrt((endX-startX)*(endX-startX)+(endY-startY)*(endY-startY))
            canvas.drawText(String.format("%.2f م",len),screenX(endX)+12f,screenY(endY)-12f,measurePaint)
        }
    }

    private fun drawWallSegments(canvas: Canvas, wall: PlanWall, paint: Paint) {
        val length = wall.lengthMetres
        val dx = (wall.x2 - wall.x1) / length
        val dy = (wall.y2 - wall.y1) / length
        var cursor = 0f
        openings.filter { it.wallId == wall.id }.sortedBy { it.offsetMetres }.forEach { opening ->
            val gapStart = opening.offsetMetres.coerceIn(0f, length)
            val gapEnd = (opening.offsetMetres + opening.widthMetres).coerceIn(0f, length)
            if (gapStart > cursor) {
                canvas.drawLine(
                    screenX(wall.x1 + dx * cursor), screenY(wall.y1 + dy * cursor),
                    screenX(wall.x1 + dx * gapStart), screenY(wall.y1 + dy * gapStart), paint
                )
            }
            cursor = maxOf(cursor, gapEnd)
        }
        if (cursor < length) {
            canvas.drawLine(
                screenX(wall.x1 + dx * cursor), screenY(wall.y1 + dy * cursor),
                screenX(wall.x2), screenY(wall.y2), paint
            )
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val step=pixelsPerMetre*.5f
        var x=originX()%step
        while(x<width){canvas.drawLine(x,0f,x,height.toFloat(),gridPaint);x+=step}
        var y=originY()%step
        while(y<height){canvas.drawLine(0f,y,width.toFloat(),y,gridPaint);y+=step}
        canvas.drawLine(originX(),0f,originX(),height.toFloat(),axisPaint)
        canvas.drawLine(0f,originY(),width.toFloat(),originY(),axisPaint)
    }

    fun setTool(next: PlanCanvasTool) {
        tool = next
        selectedWallId = null
        selectedFurnitureId = null
        invalidate()
    }

    fun selectedWall(): PlanWall? = plan.firstOrNull { it.id == selectedWallId }
    fun selectedFurniture(): PlanFurniture? = furniture.firstOrNull { it.id == selectedFurnitureId }

    fun addFurniture(item: PlanFurniture) {
        rememberUndo()
        furniture.add(item)
        selectedFurnitureId = item.id
        selectedWallId = null
        savePlan()
        invalidate()
    }

    fun addOpeningToSelected(type: PlanOpeningType): Boolean {
        val wall = selectedWall() ?: return false
        val width = if (type == PlanOpeningType.DOOR) 0.9f else 1.2f
        val height = if (type == PlanOpeningType.DOOR) 2.1f else 1.0f
        val sill = if (type == PlanOpeningType.DOOR) 0f else 0.9f
        if (wall.lengthMetres < width + 0.1f || sill + height > wall.height) return false
        rememberUndo()
        openings.add(
            PlanOpening(
                wallId = wall.id,
                type = type,
                offsetMetres = (wall.lengthMetres - width) / 2f,
                widthMetres = width,
                heightMetres = height,
                sillHeightMetres = sill
            )
        )
        rooms.clear()
        savePlan()
        invalidate()
        return true
    }

    fun updateSelectedWall(thicknessMetres: Float, heightMetres: Float): Boolean {
        val index = plan.indexOfFirst { it.id == selectedWallId }
        if (index < 0 || !thicknessMetres.isFinite() || !heightMetres.isFinite() ||
            thicknessMetres <= 0f || heightMetres <= 0f) return false
        if (openings.filter { it.wallId == selectedWallId }.any { it.sillHeightMetres + it.heightMetres > heightMetres }) return false
        return try {
            rememberUndo()
            plan[index] = plan[index].copy(thickness = thicknessMetres, height = heightMetres)
            rooms.clear()
            savePlan()
            invalidate()
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun distanceToSegment(px: Float, py: Float, wall: PlanWall): Float {
        val dx = wall.x2 - wall.x1
        val dy = wall.y2 - wall.y1
        val lengthSquared = dx * dx + dy * dy
        val t = (((px - wall.x1) * dx + (py - wall.y1) * dy) / lengthSquared).coerceIn(0f, 1f)
        val nearestX = wall.x1 + t * dx
        val nearestY = wall.y1 + t * dy
        return kotlin.math.hypot(px - nearestX, py - nearestY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1) {
            drawing = false
            val focusX = (event.getX(0) + event.getX(1)) / 2f
            val focusY = (event.getY(0) + event.getY(1)) / 2f
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                lastFocusX = focusX
                lastFocusY = focusY
            } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                panX += focusX - lastFocusX
                panY += focusY - lastFocusY
                lastFocusX = focusX
                lastFocusY = focusY
                invalidate()
            }
            return true
        }
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastFocusX = event.x
                lastFocusY = event.y
                val start = if (tool == PlanCanvasTool.WALL) snappedPoint(worldX(event.x), worldY(event.y)) else PlanPoint(worldX(event.x), worldY(event.y))
                startX = start.x
                startY = start.y
                endX = startX
                endY = startY
                if (tool != PlanCanvasTool.WALL) {
                    val furnitureHit = furniture.firstOrNull { item ->
                        val dx = startX - item.x
                        val dy = startY - item.y
                        val angle = Math.toRadians(-item.rotationDegrees.toDouble())
                        val localX = dx * kotlin.math.cos(angle).toFloat() - dy * kotlin.math.sin(angle).toFloat()
                        val localY = dx * kotlin.math.sin(angle).toFloat() + dy * kotlin.math.cos(angle).toFloat()
                        abs(localX) <= item.width / 2f && abs(localY) <= item.depth / 2f
                    }
                    if (furnitureHit != null) {
                        selectedFurnitureId = furnitureHit.id
                        selectedWallId = null
                        selectionStartX = startX; selectionStartY = startY; selectionMoved = false
                        if (tool == PlanCanvasTool.ERASE) {
                            rememberUndo(); furniture.removeAll { it.id == furnitureHit.id }; selectedFurnitureId = null; savePlan()
                        }
                        drawing = false; invalidate(); return true
                    }
                    selectedFurnitureId = null
                    val hit = plan.minByOrNull { distanceToSegment(startX, startY, it) }
                        ?.takeIf { distanceToSegment(startX, startY, it) <= 0.35f }
                    selectedWallId = hit?.id
                    selectionStartX = startX
                    selectionStartY = startY
                    selectionMoved = false
                    if (tool == PlanCanvasTool.ERASE && hit != null) {
                        rememberUndo()
                        plan.removeAll { it.id == hit.id }
                        openings.removeAll { it.wallId == hit.id }
                        rooms.clear()
                        selectedWallId = null
                        savePlan()
                    }
                    drawing = false
                    invalidate()
                    return true
                }
                drawing = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val current = if (tool == PlanCanvasTool.WALL) snappedPoint(worldX(event.x), worldY(event.y), PlanPoint(startX, startY)) else PlanPoint(worldX(event.x), worldY(event.y))
                endX = current.x
                endY = current.y
                if (tool == PlanCanvasTool.SELECT && (selectedWallId != null || selectedFurnitureId != null)) {
                    selectionMoved = kotlin.math.hypot(endX - selectionStartX, endY - selectionStartY) > 0.03f
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val current = if (tool == PlanCanvasTool.WALL) snappedPoint(worldX(event.x), worldY(event.y), PlanPoint(startX, startY)) else PlanPoint(worldX(event.x), worldY(event.y))
                endX = current.x
                endY = current.y
                if (tool == PlanCanvasTool.SELECT && selectedFurnitureId != null && selectionMoved) {
                    val dx = endX - selectionStartX
                    val dy = endY - selectionStartY
                    rememberUndo()
                    val index = furniture.indexOfFirst { it.id == selectedFurnitureId }
                    if (index >= 0) { furniture[index] = furniture[index].copy(x = furniture[index].x + dx, y = furniture[index].y + dy); savePlan() }
                } else if (tool == PlanCanvasTool.SELECT && selectedWallId != null && selectionMoved) {
                    val dx = endX - selectionStartX
                    val dy = endY - selectionStartY
                    rememberUndo()
                    val index = plan.indexOfFirst { it.id == selectedWallId }
                    if (index >= 0) {
                        plan[index] = plan[index].copy(
                            x1 = plan[index].x1 + dx, y1 = plan[index].y1 + dy,
                            x2 = plan[index].x2 + dx, y2 = plan[index].y2 + dy
                        )
                        rooms.clear()
                        savePlan()
                    }
                } else if (tool == PlanCanvasTool.WALL && abs(endX - startX) + abs(endY - startY) >= .2f) {
                    rememberUndo()
                    plan.add(PlanWall(startX, startY, endX, endY))
                    rooms.clear()
                    savePlan()
                }
                drawing = false
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {drawing=false;invalidate();return true}
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private fun rememberUndo() { undoStack.addLast(EditorSnapshot(plan.toList(), openings.toList(), furniture.toList())); if(undoStack.size>40) undoStack.removeFirst() }
    fun undo() {
        if(undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeLast()
            plan.clear(); plan.addAll(snapshot.walls)
            openings.clear(); openings.addAll(snapshot.openings)
            furniture.clear(); furniture.addAll(snapshot.furniture)
        } else if(plan.isNotEmpty()) {
            val removed = plan.removeAt(plan.lastIndex)
            openings.removeAll { it.wallId == removed.id }
        }
        rooms.clear()
        savePlan();invalidate()
    }
    fun clearPlan(){if(plan.isNotEmpty() || openings.isNotEmpty() || furniture.isNotEmpty())rememberUndo();plan.clear();rooms.clear();openings.clear();furniture.clear();selectedWallId=null;selectedFurnitureId=null;savePlan();invalidate()}

    fun detectRooms(): Int {
        rooms.clear()
        rooms.addAll(PlanRoomDetector.detectRooms(plan))
        savePlan()
        invalidate()
        return rooms.size
    }

    fun exportPlanJson(): String {
        val walls = JSONArray()
        plan.forEach { wall ->
            walls.put(JSONObject()
                .put("id", wall.id).put("levelId", wall.levelId)
                .put("x1", wall.x1).put("y1", wall.y1)
                .put("x2", wall.x2).put("y2", wall.y2)
                .put("thickness", wall.thickness).put("height", wall.height))
        }
        val furnitureArray = JSONArray()
        furniture.forEach { item ->
            furnitureArray.put(JSONObject().put("id", item.id).put("name", item.name).put("category", item.category)
                .put("x", item.x).put("y", item.y).put("width", item.width).put("depth", item.depth)
                .put("height", item.height).put("rotation", item.rotationDegrees).put("levelId", item.levelId))
        }
        val openingArray = JSONArray()
        openings.forEach { opening ->
            openingArray.put(JSONObject()
                .put("id", opening.id).put("wallId", opening.wallId)
                .put("type", opening.type.name).put("offset", opening.offsetMetres)
                .put("width", opening.widthMetres).put("height", opening.heightMetres)
                .put("sill", opening.sillHeightMetres))
        }
        val roomArray = JSONArray()
        rooms.forEach { room ->
            val boundary = JSONArray()
            room.boundary.forEach { point ->
                boundary.put(JSONObject().put("x", point.x).put("y", point.y))
            }
            roomArray.put(JSONObject()
                .put("id", room.id).put("name", room.name)
                .put("levelId", room.levelId).put("boundary", boundary))
        }
        return JSONObject()
            .put("format", "mokhatiti-native")
            .put("version", 5)
            .put("unit", "metre")
            .put("walls", walls)
            .put("rooms", roomArray)
            .put("openings", openingArray)
            .put("furniture", furnitureArray)
            .toString(2)
    }

    fun savePlan() {
        prefs.edit().putString("plan_json", exportPlanJson()).apply()
    }

    fun importPlanJson(raw: String): Boolean {
        return try {
            val root = if (raw.trimStart().startsWith("{")) JSONObject(raw) else null
            val walls = root?.optJSONArray("walls") ?: JSONArray(raw)
            val loadedWalls = mutableListOf<PlanWall>()
            val loadedRooms = mutableListOf<PlanRoom>()
            val loadedOpenings = mutableListOf<PlanOpening>()
            val loadedFurniture = mutableListOf<PlanFurniture>()
            for (i in 0 until walls.length()) {
                val wall = walls.getJSONObject(i)
                loadedWalls.add(
                    PlanWall(
                        wall.getDouble("x1").toFloat(), wall.getDouble("y1").toFloat(),
                        wall.getDouble("x2").toFloat(), wall.getDouble("y2").toFloat(),
                        wall.optDouble("thickness", PlanWall.DEFAULT_WALL_THICKNESS_METRES.toDouble()).toFloat(),
                        wall.optDouble("height", PlanWall.DEFAULT_WALL_HEIGHT_METRES.toDouble()).toFloat(),
                        wall.optString("id", java.util.UUID.randomUUID().toString()),
                        wall.optString("levelId", PlanWall.DEFAULT_LEVEL_ID)
                    )
                )
            }
            val savedRooms = root?.optJSONArray("rooms") ?: JSONArray()
            for (i in 0 until savedRooms.length()) {
                val room = savedRooms.getJSONObject(i)
                val boundaryJson = room.getJSONArray("boundary")
                val boundary = (0 until boundaryJson.length()).map { index ->
                    val point = boundaryJson.getJSONObject(index)
                    PlanPoint(point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
                }
                if (boundary.size >= 3) {
                    loadedRooms.add(
                        PlanRoom(
                            room.optString("id", "room-$i"),
                            room.optString("name", "غرفة ${i + 1}"),
                            boundary,
                            room.optString("levelId", PlanWall.DEFAULT_LEVEL_ID)
                        )
                    )
                }
            }
            val savedOpenings = root?.optJSONArray("openings") ?: JSONArray()
            for (i in 0 until savedOpenings.length()) {
                val opening = savedOpenings.getJSONObject(i)
                loadedOpenings.add(
                    PlanOpening(
                        id = opening.optString("id", java.util.UUID.randomUUID().toString()),
                        wallId = opening.getString("wallId"),
                        type = PlanOpeningType.valueOf(opening.getString("type")),
                        offsetMetres = opening.getDouble("offset").toFloat(),
                        widthMetres = opening.getDouble("width").toFloat(),
                        heightMetres = opening.getDouble("height").toFloat(),
                        sillHeightMetres = opening.optDouble("sill", 0.0).toFloat()
                    )
                )
            }
            val savedFurniture = root?.optJSONArray("furniture") ?: JSONArray()
            for (i in 0 until savedFurniture.length()) {
                val item = savedFurniture.getJSONObject(i)
                loadedFurniture.add(
                    PlanFurniture(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        name = item.getString("name"),
                        category = item.optString("category", "عام"),
                        x = item.getDouble("x").toFloat(),
                        y = item.getDouble("y").toFloat(),
                        width = item.getDouble("width").toFloat(),
                        depth = item.getDouble("depth").toFloat(),
                        height = item.getDouble("height").toFloat(),
                        rotationDegrees = item.optDouble("rotation", 0.0).toFloat(),
                        levelId = item.optString("levelId", PlanWall.DEFAULT_LEVEL_ID)
                    )
                )
            }
            PlanProject(walls = loadedWalls, rooms = loadedRooms, openings = loadedOpenings, furniture = loadedFurniture)
            plan.clear()
            plan.addAll(loadedWalls)
            selectedWallId = null
            rooms.clear()
            rooms.addAll(loadedRooms)
            openings.clear()
            openings.addAll(loadedOpenings)
            furniture.clear()
            furniture.addAll(loadedFurniture)
            selectedWallId = null
            selectedFurnitureId = null
            savePlan()
            invalidate()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadPlan() {
        val raw = prefs.getString("plan_json", null) ?: return
        importPlanJson(raw)
    }
}
