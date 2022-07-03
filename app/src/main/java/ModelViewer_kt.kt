package ogles.oglbackbone

import android.content.Context
import android.opengl.*
import android.util.Log
import ogles.oglbackbone.utils.PlyObject
import ogles.oglbackbone.utils.ShaderCompiler
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ModelViewer_kt : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private var shaderHandle = 0
    private var MVPloc = 0

    private val viewM: FloatArray
    private val modelM: FloatArray
    private val projM: FloatArray
    private val MVP: FloatArray
    private val temp: FloatArray

    private var drawMode = 0
    private var countFacesToElement = 0
    private var angle = 0f

    init {

        //super(1,1,1);
        drawMode = GLES20.GL_TRIANGLES
        viewM = FloatArray(16)
        modelM = FloatArray(16)
        projM = FloatArray(16)
        MVP = FloatArray(16)
        temp = FloatArray(16)
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)

    }

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)
        this.surface!!.setOnClickListener {
            drawMode =
                if (drawMode == GLES20.GL_TRIANGLES) GLES20.GL_LINE_LOOP else GLES20.GL_TRIANGLES
            Log.v(
                "TAG",
                "Drawing " + if (drawMode == GLES20.GL_TRIANGLES) "Triangles" else "Lines"
            )
        }
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        super.onSurfaceChanged(gl10, w, h)
        val aspect = w.toFloat() / (if (h == 0) 1 else h).toFloat()
        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f)
        Matrix.setLookAtM(viewM, 0, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        val vertexSrc = """
             #version 300 es
             
             layout(location = 1) in vec3 vPos;
             layout(location = 2) in vec3 color;
             uniform mat4 MVP;
             out vec4 varyingColor;
             
             void main(){
             varyingColor = vec4(color,1);
             gl_Position = MVP * vec4(vPos,1);
             }
             """.trimIndent()
        val fragmentSrc = """
             #version 300 es
             
             precision mediump float;
             
             in vec4 varyingColor;
             out vec4 fragColor;
             
             void main() {
             fragColor = vec4(varyingColor);
             }
             """.trimIndent()
        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        val `is`: InputStream
        var vertices: FloatArray? = null
        var indices: IntArray? = null
        try {
            `is` = context!!.assets.open("ohioteapot.ply")
            val po = PlyObject(`is`)
            po.parse()
            //Log.v("TAG",po.toString());
            vertices = po.vertices
            indices = po.indices
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        VAO = IntArray(1) //one VAO to bind both vpos and color
        val vertexData = ByteBuffer.allocateDirect(vertices!!.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexData.put(vertices)
        vertexData.position(0)
        val indexData = ByteBuffer.allocateDirect(indices!!.size * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexData.put(indices)
        indexData.position(0)
        countFacesToElement = indices.size
        val VBO = IntArray(2) //0: vpos, 1: indices
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
            3,
            GLES20.GL_FLOAT,
            false,
            6 * java.lang.Float.BYTES,
            0
        ) //vpos
        GLES20.glVertexAttribPointer(
            2,
            3,
            GLES20.GL_FLOAT,
            false,
            6 * java.lang.Float.BYTES,
            3 * java.lang.Float.BYTES
        ) //color/normal
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBO[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
            GLES20.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
        MVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        angle += 1f
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, 0f, 7f)
        Matrix.rotateM(modelM, 0, angle, 1f, 1f, 1f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)
        GLES20.glDrawElements(drawMode, countFacesToElement, GLES20.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }



}