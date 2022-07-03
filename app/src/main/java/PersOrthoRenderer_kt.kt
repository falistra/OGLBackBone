package ogles.oglbackbone

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PersOrthoRenderer_kt : BasicRenderer_kt() {
    private var perspective = true

    private lateinit var VAO: IntArray
    private var shaderHandle = 0
    private var MVPloc = 0

    private var angle = 0f

    private val viewM: FloatArray
    private val orthoM: FloatArray
    private val modelM: FloatArray
    private val projM: FloatArray
    private val MVP: FloatArray
    private val temp: FloatArray

    init {
        viewM = FloatArray(16)
        orthoM = FloatArray(16)
        modelM = FloatArray(16)
        projM = FloatArray(16)
        MVP = FloatArray(16)
        temp = FloatArray(16)
        Matrix.setIdentityM(orthoM, 0)
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)
    }

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)
        this.surface!!.setOnClickListener {
            perspective = !perspective
            Log.v(
                BasicRenderer.TAG,
                "Frustum set to " + if (perspective) "projective" else "orthographic"
            )
        }
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        super.onSurfaceChanged(gl10, w, h)
        val aspect = w.toFloat() / (if (h == 0) 1 else h).toFloat()
        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f)
        Matrix.orthoM(orthoM, 0, -1f * aspect, 1f * aspect, -1f, 1f, -0.1f, 100f)
        Matrix.setLookAtM(viewM, 0, 0f, 0f, 14f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        val vertexSrc = """
             #version 300 es
             
             layout(location = 1) in vec2 vPos;
             layout(location = 2) in vec3 color;
             uniform mat4 MVP;
             out vec3 colorVarying; 
             
             void main(){
             colorVarying = color;
             gl_Position = MVP * vec4(vPos,0,1);
             }
             """.trimIndent()
        val fragmentSrc = """
             #version 300 es
             
             precision mediump float;
             
             in vec3 colorVarying;
             out vec4 fragColor;
             
             void main() {
             fragColor = vec4(colorVarying,1);
             }
             
             """.trimIndent()
        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        VAO = IntArray(1) //one VAO to bind both vpos and color

        //--1--|--2---|
        //vx,vy,r,g,b
        val verticesAndColors = floatArrayOf(
            -1f, -1f, 1f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            1f, 1f, 0f, 0f, 1f,
            -1f, 1f, 1f, 0f, 1f
        )
        val indices = intArrayOf(
            0, 1, 2,  //first triangle
            0, 2, 3 //second triangle (fixed winding order)
        )
        val vertexData = ByteBuffer.allocateDirect(verticesAndColors.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexData.put(verticesAndColors)
        vertexData.position(0)
        val indexData = ByteBuffer.allocateDirect(indices.size * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexData.put(indices)
        indexData.position(0)
        val VBO = IntArray(2) //0: vpos/color, 1: indices
        GLES20.glGenBuffers(2, VBO, 0)
        GLES30.glGenVertexArrays(1, VAO, 0)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * vertexData.capacity(),
            vertexData, GLES20.GL_STATIC_DRAW
        )
        GLES20.glVertexAttribPointer(
            1,
            2,
            GLES20.GL_FLOAT,
            false,
            5 * java.lang.Float.BYTES,
            0
        ) //vpos
        GLES20.glVertexAttribPointer(
            2,
            3,
            GLES20.GL_FLOAT,
            false,
            5 * java.lang.Float.BYTES,
            2 * java.lang.Float.BYTES
        ) //color
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBO[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
            GLES20.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
        MVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (perspective) Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0) else Matrix.multiplyMM(
            temp,
            0,
            orthoM,
            0,
            viewM,
            0
        )
        angle += 1f
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, -0.8f, 0f, -3f)
        Matrix.rotateM(modelM, 0, angle, 0f, 1f, 1f)
        Matrix.scaleM(modelM, 0, 0.5f, 1f, 0.5f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            6,
            GLES20.GL_UNSIGNED_INT,
            0
        ) //num of faces, not vertices!
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0.8f, 0f, -3f)
        Matrix.rotateM(modelM, 0, angle, 0f, 1f, 1f)
        Matrix.scaleM(modelM, 0, 0.5f, 1f, 0.5f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            6,
            GLES20.GL_UNSIGNED_INT,
            0
        ) //num of faces, not vertices!
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }




}