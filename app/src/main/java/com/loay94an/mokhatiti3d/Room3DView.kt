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
class Room3DView(context: Context, private val sourceWalls: List<PlanWall>, private val sourceRooms: List<PlanRoom>) : GLSurfaceView(context) {
    private val sceneRenderer = PlanGlRenderer(sourceWalls, sourceRooms)
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
        if (!cameraTouched) {
            val points = wallSnapshot.flatMap { listOf(it.start, it.end) } + roomSnapshot.flatMap { it.boundary }
            if (points.isNotEmpty()) {
                val span = maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
                distance = (span * 1.8f + 5f).coerceIn(6f, 150f)
            }
        }
        sceneRenderer.setCamera(yaw, pitch, distance)
        queueEvent { sceneRenderer.updateScene(wallSnapshot, roomSnapshot) }
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

private class PlanGlRenderer(walls: List<PlanWall>, rooms: List<PlanRoom>) : GLSurfaceView.Renderer {
    @Volatile var currentWalls: List<PlanWall> = walls.toList()
        private set
    @Volatile var currentRooms: List<PlanRoom> = rooms.toList()
        private set
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

    fun updateScene(walls: List<PlanWall>, rooms: List<PlanRoom>) {
        currentWalls = walls.toList(); currentRooms = rooms.toList()
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
        val all=currentWalls.flatMap{listOf(it.start,it.end)}+currentRooms.flatMap{it.boundary}
        if(all.isEmpty()){vertexBuffer=null;vertexCount=0;return}
        val minX=all.minOf{it.x};val maxX=all.maxOf{it.x};val minY=all.minOf{it.y};val maxY=all.maxOf{it.y}
        centerX=(minX+maxX)/2f;centerY=(minY+maxY)/2f;centerZ=(currentWalls.maxOfOrNull{it.height}?:2.8f)/2f
        val ground=floatArrayOf(0.78f,0.82f,0.86f);val roomColor=floatArrayOf(0.64f,0.79f,0.71f)
        val groundZ=0f
        quad(floatArrayOf(minX-0.5f,minY-0.5f,groundZ),floatArrayOf(maxX+0.5f,minY-0.5f,groundZ),floatArrayOf(maxX+0.5f,maxY+0.5f,groundZ),floatArrayOf(minX-0.5f,maxY+0.5f,groundZ),ground)
        currentRooms.forEach { room ->
            PlanPolygonTriangulator.triangulate(room.boundary).forEach { tri ->
                triangle(
                    floatArrayOf(tri.first.x, tri.first.y, 0.012f),
                    floatArrayOf(tri.second.x, tri.second.y, 0.012f),
                    floatArrayOf(tri.third.x, tri.third.y, 0.012f),
                    roomColor
                )
            }
        }
        currentWalls.forEachIndexed{index,wall->
            val footprint=wall.footprint();val bottom=footprint.map{floatArrayOf(it.x,it.y,0f)};val top=footprint.map{floatArrayOf(it.x,it.y,wall.height)}
            val color=when(index%3){0->floatArrayOf(0.69f,0.78f,0.86f);1->floatArrayOf(0.58f,0.69f,0.79f);else->floatArrayOf(0.78f,0.84f,0.89f)}
            for(i in 0..3){val n=(i+1)%4;quad(bottom[i],bottom[n],top[n],top[i],color)}
            quad(top[0],top[1],top[2],top[3],floatArrayOf(0.82f,0.87f,0.92f))
        }
        val floats=FloatArray(data.size){data[it]};vertexCount=floats.size/7
        vertexBuffer=ByteBuffer.allocateDirect(floats.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floats);position(0)}
    }
}