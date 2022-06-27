package ogles.oglbackbone

import android.opengl.GLES20
import android.opengl.GLES30
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class IndexedRenderer_kt : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private var shaderHandle = 0

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
            3, 2, 0 //second triangle
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

        //Experiments with culling
        /*glEnable(GL_CULL_FACE); //as this is initially disabled
        glCullFace(GL_BACK); //which face to cull? back,front or both (Still points and lines visible
        glFrontFace(GL_CCW); */
        //winding order that defines front and back
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            6,
            GLES20.GL_UNSIGNED_INT,
            0
        ) //num of indices, not vertices!
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }

}