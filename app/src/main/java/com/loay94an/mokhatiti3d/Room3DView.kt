package com.loay94an.mokhatiti3d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Native interactive OpenGL ES 2.0 renderer; no WebView, Canvas projection or Java 3D. */
class Room3DView(context: Context, private val sourceWalls: List<PlanWall>, private val sourceRooms: List<PlanRoom>, private val sourceOpenings: List<PlanOpening>, private val sourceFurniture: List<PlanFurniture>, private val sourceLevels: List<PlanLevel>) : GLSurfaceView(context) {
    private val sceneRenderer = PlanGlRenderer(sourceWalls, sourceRooms, sourceOpenings, sourceFurniture, sourceLevels)
    private var yaw = -0.72f
    private var pitch = 0.58f
    private var distance = 12f
    private var lastX = 0f
    private var lastY = 0f
    private var cameraTouched = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cameraTouched = true
            distance = (distance / detector.scaleFactor).coerceIn(2f, 150f)
            sceneRenderer.setCamera(yaw, pitch, distance)
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setRenderer(sceneRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        sceneRenderer.setCamera(yaw, pitch, distance)
    }

    fun refresh() {
        val wallSnapshot = sourceWalls.toList()
        val roomSnapshot = sourceRooms.toList()
        val openingSnapshot = sourceOpenings.toList()
        val furnitureSnapshot = sourceFurniture.toList()
        val levelSnapshot = sourceLevels.toList()
        if (!cameraTouched) {
            val points = wallSnapshot.flatMap { listOf(it.start, it.end) } + roomSnapshot.flatMap { it.boundary } + furnitureSnapshot.flatMap { it.footprint() }
            if (points.isNotEmpty()) {
                val span = maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
                distance = (span * 1.8f + 5f).coerceIn(6f, 150f)
            }
        }
        sceneRenderer.setCamera(yaw, pitch, distance)
        queueEvent { sceneRenderer.updateScene(wallSnapshot, roomSnapshot, openingSnapshot, furnitureSnapshot, levelSnapshot) }
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; return true }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                cameraTouched = true
                yaw += (event.x - lastX) * 0.008f
                pitch = (pitch + (event.y - lastY) * 0.006f).coerceIn(0.12f, 1.35f)
                lastX = event.x; lastY = event.y
                sceneRenderer.setCamera(yaw, pitch, distance)
                requestRender()
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

private class PlanGlRenderer(walls: List<PlanWall>, rooms: List<PlanRoom>, openings: List<PlanOpening>, furniture: List<PlanFurniture>, levels: List<PlanLevel>) : GLSurfaceView.Renderer {
    @Volatile var currentWalls: List<PlanWall> = walls.toList()
        private set
    @Volatile var currentRooms: List<PlanRoom> = rooms.toList()
        private set
    @Volatile private var currentOpenings: List<PlanOpening> = openings.toList()
    @Volatile private var currentFurniture: List<PlanFurniture> = furniture.toList()
    @Volatile private var currentLevels: List<PlanLevel> = levels.toList()
    @Volatile private var cameraYaw = -0.72f
    @Volatile private var cameraPitch = 0.58f
    @Volatile private var cameraDistance = 12f
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f

    fun setCamera(yaw: Float, pitch: Float, distance: Float) { cameraYaw=yaw; cameraPitch=pitch; cameraDistance=distance }

    fun updateScene(walls: List<PlanWall>, rooms: List<PlanRoom>, openings: List<PlanOpening>, furniture: List<PlanFurniture>, levels: List<PlanLevel>) {
        currentWalls = walls.toList(); currentRooms = rooms.toList(); currentOpenings = openings.toList(); currentFurniture = furniture.toList(); currentLevels = levels.toList()
        if (program != 0) rebuildMesh()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.95f,0.97f,0.99f,1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        val vertexShader=compileShader(GLES20.GL_VERTEX_SHADER,"attribute vec3 aPosition; attribute vec4 aColor; uniform mat4 uMVP; varying vec4 vColor; void main(){ gl_Position=uMVP*vec4(aPosition,1.0); vColor=aColor; }")
        val fragmentShader=compileShader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec4 vColor; void main(){ gl_FragColor=vColor; }")
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertexShader); GLES20.glAttachShader(program,fragmentShader); GLES20.glLinkProgram(program)
        val status=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        if(status[0]==0){ val log=GLES20.glGetProgramInfoLog(program); GLES20.glDeleteProgram(program); program=0; throw IllegalStateException("OpenGL link failed: $log") }
        GLES20.glDeleteShader(vertexShader); GLES20.glDeleteShader(fragmentShader)
        rebuildMesh()
    }

    private fun compileShader(type:Int,source:String):Int {
        val shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader)
        val status=IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
        if(status[0]==0){ val log=GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); throw IllegalStateException("OpenGL shader compile failed: $log") }
        return shader
    }

    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int) {
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,42f,width.toFloat()/max(1,height),0.1f,500f)
    }

    override fun onDrawFrame(gl:GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if(program==0 || vertexCount==0)return
        val cp=cos(cameraPitch)
        val eyeX=centerX+cameraDistance*cp*cos(cameraYaw)
        val eyeY=centerY+cameraDistance*cp*sin(cameraYaw)
        val eyeZ=centerZ+cameraDistance*sin(cameraPitch)
        Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,centerX,centerY,centerZ,0f,0f,1f)
        Matrix.multiplyMM(viewProjection,0,projection,0,view,0)
        GLES20.glUseProgram(program)
        val position=GLES20.glGetAttribLocation(program,"aPosition"); val color=GLES20.glGetAttribLocation(program,"aColor"); val mvp=GLES20.glGetUniformLocation(program,"uMVP")
        GLES20.glUniformMatrix4fv(mvp,1,false,viewProjection,0)
        val buffer=vertexBuffer?:return; buffer.position(0)
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(color)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,28,buffer)
        buffer.position(3); GLES20.glVertexAttribPointer(color,4,GLES20.GL_FLOAT,false,28,buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertexCount)
        GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(color)
    }

    private fun rebuildMesh() {
        val data=ArrayList<Float>()
        fun vertex(x:Float,y:Float,z:Float,c:FloatArray){ data.add(x);data.add(y);data.add(z);data.add(c[0]);data.add(c[1]);data.add(c[2]);data.add(1f) }
        fun triangle(a:FloatArray,b:FloatArray,c:FloatArray,color:FloatArray){vertex(a[0],a[1],a[2],color);vertex(b[0],b[1],b[2],color);vertex(c[0],c[1],c[2],color)}
        fun quad(a:FloatArray,b:FloatArray,c:FloatArray,d:FloatArray,color:FloatArray){triangle(a,b,c,color);triangle(a,c,d,color)}
        val visibleLevels = currentLevels.filter { it.visible }
        val levelById = visibleLevels.associateBy { it.id }
        val visibleWalls = currentWalls.filter { it.levelId in levelById }
        val visibleRooms = currentRooms.filter { it.levelId in levelById }
        val visibleFurniture = currentFurniture.filter { it.levelId in levelById }
        val all = visibleWalls.flatMap { listOf(it.start,it.end) } + visibleRooms.flatMap { it.boundary } + visibleFurniture.flatMap { it.footprint() }
        if (all.isEmpty()) { vertexBuffer=null; vertexCount=0; return }
        val minX=all.minOf{it.x}; val maxX=all.maxOf{it.x}; val minY=all.minOf{it.y}; val maxY=all.maxOf{it.y}
        val minElevation = visibleLevels.minOfOrNull { it.elevationMetres } ?: 0f
        val maxElevation = visibleLevels.maxOfOrNull { level -> level.elevationMetres + level.heightMetres } ?: 2.8f
        centerX=(minX+maxX)/2f; centerY=(minY+maxY)/2f; centerZ=(minElevation+maxElevation)/2f
        val ground=floatArrayOf(0.78f,0.82f,0.86f); val roomColor=floatArrayOf(0.64f,0.79f,0.71f)
        visibleLevels.forEachIndexed { index, level ->
            val z = level.elevationMetres
            quad(floatArrayOf(minX-0.5f,minY-0.5f,z),floatArrayOf(maxX+0.5f,minY-0.5f,z),floatArrayOf(maxX+0.5f,maxY+0.5f,z),floatArrayOf(minX-0.5f,maxY+0.5f,z),if(index%2==0) ground else floatArrayOf(0.82f,0.85f,0.88f))
        }
        visibleRooms.forEach { room ->
            val elevation = levelById[room.levelId]?.elevationMetres ?: 0f
            PlanPolygonTriangulator.triangulate(room.boundary).forEach { tri ->
                triangle(
                    floatArrayOf(tri.first.x, tri.first.y, elevation + 0.012f),
                    floatArrayOf(tri.second.x, tri.second.y, elevation + 0.012f),
                    floatArrayOf(tri.third.x, tri.third.y, elevation + 0.012f),
                    roomColor
                )
            }
        }
        visibleFurniture.forEachIndexed { index, item ->
            val elevation = levelById[item.levelId]?.elevationMetres ?: 0f
            val footprint = item.footprint()
            val bottom = footprint.map { floatArrayOf(it.x, it.y, elevation) }
            val top = footprint.map { floatArrayOf(it.x, it.y, elevation + item.height) }
            val color = when (item.category) {
                "جلوس" -> floatArrayOf(0.72f,0.55f,0.42f)
                "نوم" -> floatArrayOf(0.72f,0.68f,0.82f)
                "طاولات" -> floatArrayOf(0.62f,0.48f,0.32f)
                else -> floatArrayOf(0.66f,0.70f,0.74f)
            }
            for (i in 0..3) {
                val next = (i + 1) % 4
                quad(bottom[i], bottom[next], top[next], top[i], color)
            }
            quad(top[0],top[1],top[2],top[3],floatArrayOf(0.84f,0.79f,0.70f))
        }
        visibleWalls.forEachIndexed { index, wall ->
            val elevation = levelById[wall.levelId]?.elevationMetres ?: 0f
            val length = wall.lengthMetres
            val ux = (wall.x2 - wall.x1) / length
            val uy = (wall.y2 - wall.y1) / length
            val nx = -uy
            val ny = ux
            val wallOpenings = currentOpenings.filter { it.wallId == wall.id }
            val tCuts = (listOf(0f, length) + wallOpenings.flatMap { listOf(it.offsetMetres, it.offsetMetres + it.widthMetres) })
                .map { it.coerceIn(0f, length) }.distinct().sorted()
            val zCuts = (listOf(0f, wall.height) + wallOpenings.flatMap { listOf(it.sillHeightMetres, it.sillHeightMetres + it.heightMetres) })
                .map { it.coerceIn(0f, wall.height) }.distinct().sorted()
            val color = when(index % 3) {
                0 -> floatArrayOf(0.69f,0.78f,0.86f)
                1 -> floatArrayOf(0.58f,0.69f,0.79f)
                else -> floatArrayOf(0.78f,0.84f,0.89f)
            }
            fun point(t: Float, side: Float, z: Float) = floatArrayOf(
                wall.x1 + ux * t + nx * side,
                wall.y1 + uy * t + ny * side,
                elevation + z
            )
            for (side in listOf(-wall.thickness / 2f, wall.thickness / 2f)) {
                for (ti in 0 until tCuts.lastIndex) {
                    val t0 = tCuts[ti]; val t1 = tCuts[ti + 1]; val tm = (t0 + t1) / 2f
                    for (zi in 0 until zCuts.lastIndex) {
                        val z0 = zCuts[zi]; val z1 = zCuts[zi + 1]; val zm = (z0 + z1) / 2f
                        val insideOpening = wallOpenings.any {
                            tm > it.offsetMetres && tm < it.offsetMetres + it.widthMetres &&
                                zm > it.sillHeightMetres && zm < it.sillHeightMetres + it.heightMetres
                        }
                        if (!insideOpening) quad(point(t0,side,z0),point(t1,side,z0),point(t1,side,z1),point(t0,side,z1),color)
                    }
                }
            }
            val revealColor = floatArrayOf(0.42f,0.52f,0.62f)
            wallOpenings.forEach { opening ->
                val a = opening.offsetMetres.coerceIn(0f,length)
                val b = (opening.offsetMetres + opening.widthMetres).coerceIn(0f,length)
                val bottom = opening.sillHeightMetres
                val top = (opening.sillHeightMetres + opening.heightMetres).coerceIn(0f,wall.height)
                val negative = -wall.thickness / 2f
                val positive = wall.thickness / 2f
                quad(point(a,negative,bottom),point(a,positive,bottom),point(a,positive,top),point(a,negative,top),revealColor)
                quad(point(b,positive,bottom),point(b,negative,bottom),point(b,negative,top),point(b,positive,top),revealColor)
                quad(point(a,negative,top),point(b,negative,top),point(b,positive,top),point(a,positive,top),revealColor)
                if (bottom > 0f) quad(point(a,positive,bottom),point(b,positive,bottom),point(b,negative,bottom),point(a,negative,bottom),revealColor)
            }
            val footprint = wall.footprint()
            val top = footprint.map { floatArrayOf(it.x,it.y,elevation + wall.height) }
            quad(top[0],top[1],top[2],top[3],floatArrayOf(0.82f,0.87f,0.92f))
        }
        val floats=FloatArray(data.size){data[it]};vertexCount=floats.size/7
        vertexBuffer=ByteBuffer.allocateDirect(floats.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floats);position(0)}
    }
}