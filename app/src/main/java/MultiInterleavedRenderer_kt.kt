package ogles.oglbackbone

import android.opengl.GLES20
import android.opengl.GLES30
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MultiInterleavedRenderer_kt : BasicRenderer_kt() {

    private lateinit var VAO: IntArray
    private var shaderHandle = 0
    private val INTERLEAVED = true

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        val vertexSrc = """
              #version 300 es
              
              layout(location = 1) in vec2 vPos;
              layout(location = 2) in vec3 color;
              out vec3 colorVarying; 
              
              void main(){
              colorVarying = color;
              gl_Position = vec4(vPos,0,1);
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
        if (INTERLEAVED) {

            //--1--|--2---|
            //vx,vy,r,g,b
            val verticesAndColors = floatArrayOf(
                -1f, -1f, 1f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                0f, 1f, 0f, 0f, 1f
            )
            val vertexData =
                ByteBuffer.allocateDirect(verticesAndColors.size * java.lang.Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            vertexData.put(verticesAndColors)
            vertexData.position(0)
            val VBO = IntArray(1) //0: vpos/color
            GLES20.glGenBuffers(1, VBO, 0)
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
            GLES30.glBindVertexArray(0)
        } else {

            // first all the vertices pos, then all the colors...
            val verticesAndColors = floatArrayOf(
                -1f, -1f,
                1f, -1f,
                0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f
            )
            val vertexData = ByteBuffer.allocateDirect(6 * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            for (i in 0..5) vertexData.put(verticesAndColors[i])
            vertexData.position(0)
            val colorData = ByteBuffer.allocateDirect(9 * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            for (i in 6 until verticesAndColors.size) colorData.put(verticesAndColors[i])
            colorData.position(0)
            val VBO = IntArray(2) //0: vpos, 1: color
            GLES30.glGenVertexArrays(1, VAO, 0)
            GLES20.glGenBuffers(2, VBO, 0)
            GLES30.glBindVertexArray(VAO[0])
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[0])
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * vertexData.capacity(),
                vertexData, GLES20.GL_STATIC_DRAW
            )
            GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, 0) //vpos
            GLES20.glEnableVertexAttribArray(1)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[1])
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * colorData.capacity(),
                colorData, GLES20.GL_STATIC_DRAW
            )
            GLES20.glVertexAttribPointer(2, 3, GLES20.GL_FLOAT, false, 0, 0) //color
            GLES20.glEnableVertexAttribArray(2)
            GLES30.glBindVertexArray(0)
        }
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }


}