package ogles.oglbackbone

import android.content.Context
import android.opengl.*
import ogles.oglbackbone.utils.PlyObject
import ogles.oglbackbone.utils.ShaderCompiler
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ToonShadingRenderer_kt : BasicRenderer_kt(1f,1f,1f,1f)  {
    private lateinit var VAO: IntArray
    private var shaderHandle = 0
    private var MVPloc = 0
    private var umodelM = 0

    private val viewM: FloatArray = FloatArray(16)
    private val modelM: FloatArray = FloatArray(16)
    private val projM: FloatArray = FloatArray(16)
    private val MVP: FloatArray = FloatArray(16)
    private val temp: FloatArray = FloatArray(16)
    private val inverseModel:  FloatArray = FloatArray(16)

    private var uInverseModel = 0

    private var countFacesToElement = 0
    private var angle = 0f

    private val lightPos: FloatArray = floatArrayOf(-0.25f, 0.25f, 10f)
    private var uLightPos = 0


    private val eyePos: FloatArray = floatArrayOf(0f, 0f, 10f)

    private var uDrawContour = 0
    private var drawContour = false

    override var TAG: String? = javaClass.simpleName
    init {
        Matrix.setIdentityM(inverseModel, 0)
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)
    }

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)
        this.surface!!.setOnClickListener { drawContour = !drawContour }
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        super.onSurfaceChanged(gl10, w, h)
        val aspect = w.toFloat() / (if (h == 0) 1 else h).toFloat()
        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f)
        Matrix.setLookAtM(
            viewM, 0,
            eyePos[0], eyePos[1], eyePos[2], 0f, 0f, 0f, 0f, 1f, 0f
        )
    }

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        val vertexSrc = """
             #version 300 es
             precision highp int;

             layout(location = 1) in vec3 vPos;
             layout(location = 2) in vec3 normal;
             uniform mat4 MVP;
             uniform mat4 modelMatrix;
             uniform mat4 inverseModel;
             uniform int drawContour;
             out vec3 fragModel;
             out vec3 transfNormal;
             
             void main(){
             float scaling = 1.1;
             if(drawContour==0){
             transfNormal = normalize(vec3(inverseModel * vec4(normal,1)));
             fragModel = vec3(modelMatrix * vec4(vPos,1));
             scaling = 1.0;
             }
             gl_Position = MVP * vec4(vPos*scaling,1);
             }
             """.trimIndent()
        val fragmentSrc = """
             #version 300 es
             
             precision mediump float;
             precision highp int;

             
             uniform vec3 lightPos;
             uniform int drawContour;
             in vec3 fragModel;
             in vec3 transfNormal;
             out vec4 fragColor;
             
             void main() {
             if(drawContour==1){
             fragColor=vec4(0.5,0.5,0.5,1);
             return;
             }
             vec3 lightDir = normalize(lightPos-fragModel);
             float diff = max(dot(lightDir,transfNormal),0.0);
             if(diff>0.95) fragColor = vec4(0.85,0.95,0.25,1);
             else if (diff>0.5) fragColor = vec4(0.425,0.474,0.125,1);
             else if (diff>0.25) fragColor = vec4(0.2,0.2,0.075,1);
             else fragColor = vec4(0.1,0.1,0,1);
             
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
        umodelM = GLES20.glGetUniformLocation(shaderHandle, "modelMatrix")
        uInverseModel = GLES20.glGetUniformLocation(shaderHandle, "inverseModel")
        uLightPos = GLES20.glGetUniformLocation(shaderHandle, "lightPos")
        uDrawContour = GLES20.glGetUniformLocation(shaderHandle, "drawContour")

        //pre load uniform values:
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform3fv(uLightPos, 1, lightPos, 0)
        GLES20.glUseProgram(0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        angle += 1f
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, 0f, 8f)
        Matrix.rotateM(modelM, 0, angle, 0.2f, 1f, 0.4f)

        //calculate and send MVP
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)

        //draw contours
        if (drawContour) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glUniform1i(uDrawContour, 1)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                countFacesToElement,
                GLES20.GL_UNSIGNED_INT,
                0
            )
            GLES20.glUniform1i(uDrawContour, 0)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }
        //draw regular model

        //send model with proper transformations
        GLES20.glUniformMatrix4fv(umodelM, 1, false, modelM, 0)

        //calculate normal matrix
        Matrix.invertM(inverseModel, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(uInverseModel, 1, true, inverseModel, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, countFacesToElement, GLES20.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }

}