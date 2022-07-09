package ogles.oglbackbone

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import ogles.oglbackbone.utils.ShaderCompiler
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.system.exitProcess

class VBOVAORenderer_kt : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private lateinit var VBO: IntArray


    private var shaderHandle = 0
    private var attrPos = 0
    private var attrColor = 0
    private val USE_VAO = false

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        val isV: InputStream
        val isF: InputStream
        try {
            isV = context!!.assets.open("passthrough.glslv")
            isF = context!!.assets.open("passthrough.glslf")
            shaderHandle = ShaderCompiler.createProgram(isV, isF)
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(-1)
        }
        if (shaderHandle == -1) exitProcess(-1)



        val vertices = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            0f, 1f
        )
        val colors = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f)
        val vertexData = ByteBuffer.allocateDirect(vertices.size * java.lang.Float.BYTES).order(
            ByteOrder.nativeOrder()
        )
            .asFloatBuffer()
        vertexData.put(vertices)
        vertexData.position(0)
        val colorData = ByteBuffer.allocateDirect(colors.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        colorData.put(colors)
        colorData.position(0)

        attrPos = GLES20.glGetAttribLocation(shaderHandle, "vPos")
        attrColor = GLES20.glGetAttribLocation(shaderHandle, "aColor")

        VBO = IntArray(2)
        GLES20.glGenBuffers(2, VBO, 0)
        if (USE_VAO) {
            Log.v(BasicRenderer.TAG, "Using VAO")
            VAO = IntArray(1)
            GLES30.glGenVertexArrays(1, VAO, 0)
            GLES30.glBindVertexArray(VAO[0])

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[0])
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * vertexData.capacity(),
                vertexData, GLES20.GL_STATIC_DRAW
            )
            GLES20.glVertexAttribPointer(attrPos, 2, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glEnableVertexAttribArray(attrPos)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[1])
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * colorData.capacity(),
                colorData, GLES20.GL_STATIC_DRAW
            )
            GLES20.glVertexAttribPointer(attrColor, 3, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glEnableVertexAttribArray(attrColor)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES30.glBindVertexArray(0)
        } else {
            Log.v(BasicRenderer.TAG, "Using VBOs")
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[0])
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * vertexData.capacity(),
                vertexData, GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[1])
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * colorData.capacity(),
                colorData, GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        }
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (USE_VAO) {
            GLES20.glUseProgram(shaderHandle)
            GLES30.glBindVertexArray(VAO[0])
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
            GLES30.glBindVertexArray(0)
            GLES20.glUseProgram(0)
        } else {
            GLES20.glUseProgram(shaderHandle) //bind to the program (shaders)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[0]) //bind the VBO (positions)
            GLES20.glVertexAttribPointer(attrPos, 2, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glEnableVertexAttribArray(attrPos) //enable attributes
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[1]) //bind the VBO (colors)
            GLES20.glVertexAttribPointer(attrColor, 3, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glEnableVertexAttribArray(attrColor)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3) //drawcall!
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glUseProgram(0)
        }
    }

}