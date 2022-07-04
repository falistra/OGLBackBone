package ogles.oglbackbone

import android.opengl.GLES10
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import ogles.oglbackbone.utils.PlyObject
import ogles.oglbackbone.utils.ShaderCompiler
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class TeapotLight_kt : BasicRenderer_kt() {
    val PHONG_MODEL = false

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
    private var uEyePos = 0

    init {
        Matrix.setIdentityM(inverseModel, 0)
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)
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
        val vertexSrcGouraud = """
             #version 300 es
             
             layout(location = 1) in vec3 vPos;
             layout(location = 2) in vec3 normal;
             uniform mat4 MVP;
             uniform mat4 modelMatrix;
             uniform mat4 inverseModel;
             uniform vec3 lightPos;
             uniform vec3 eyePos;
             out vec4 finalColor;
             
             void main(){
             vec3 transfNormal = normalize(vec3(inverseModel * vec4(normal,1)));
             vec3 fragModel = vec3(modelMatrix * vec4(vPos,1));
             vec4 specComponent = vec4(0.92,0.94,0.69,1);
             vec4 diffuseComponent = vec4(0.64,0.84,0.15,1);
             vec4 ambientComponent = vec4(0.12,0.4,0.01,1);
             vec3 eyeDir = normalize(eyePos-fragModel);
             vec3 lightDir = normalize(lightPos-fragModel);
             float diff = max(dot(lightDir,transfNormal),0.0);
             vec3 refl = reflect(-lightDir,transfNormal);
             float spec =  pow( max(dot(eyeDir,refl),0.0), 32.0);
             finalColor = ambientComponent + diff*diffuseComponent + spec*specComponent; 
             gl_Position = MVP * vec4(vPos,1);
             }
             """.trimIndent()
        val fragmentSrcGouraud = """
             #version 300 es
             
             precision mediump float;
             
             in vec4 finalColor;
             out vec4 fragColor;
             
             void main() {
             fragColor = finalColor;
             }
             """.trimIndent()
        val vertexSrcPhong = """
             #version 300 es
             
             layout(location = 1) in vec3 vPos;
             layout(location = 2) in vec3 normal;
             uniform mat4 MVP;
             uniform mat4 modelMatrix;
             uniform mat4 inverseModel;
             out vec3 fragModel;
             out vec3 transfNormal;
             
             void main(){
             transfNormal = normalize(vec3(inverseModel * vec4(normal,1)  ));
             fragModel = vec3(modelMatrix * vec4(vPos,1));
             gl_Position = MVP * vec4(vPos,1);
             }
             """.trimIndent()
        val fragmentSrcPhong = """
             #version 300 es
             
             precision mediump float;
             
             uniform vec3 lightPos;
             uniform vec3 eyePos;
             in vec3 fragModel;
             in vec3 transfNormal;
             out vec4 fragColor;
             
             void main() {
             vec4 specComponent = vec4(0.92,0.94,0.69,1);
             vec4 diffuseComponent = vec4(0.64,0.84,0.15,1);
             vec4 ambientComponent = vec4(0.12,0.4,0.01,1);
             vec3 eyeDir = normalize(eyePos-fragModel);
             vec3 lightDir = normalize(lightPos-fragModel);
             float diff = max(dot(lightDir,transfNormal),0.0);
             vec3 refl = reflect(-lightDir,transfNormal);
             float spec =  pow( max(dot(eyeDir,refl),0.0), 32.0);
             fragColor = ambientComponent + diff*diffuseComponent + spec*specComponent; 
             }
             """.trimIndent()
        shaderHandle = if (PHONG_MODEL) ShaderCompiler.createProgram(
            vertexSrcPhong,
            fragmentSrcPhong
        ) else ShaderCompiler.createProgram(vertexSrcGouraud, fragmentSrcGouraud)
        if (shaderHandle == -1) System.exit(-1)
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
        uEyePos = GLES20.glGetUniformLocation(shaderHandle, "eyePos")

        //pre load uniform values:
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform3fv(uLightPos, 1, lightPos, 0)
        GLES20.glUniform3fv(uEyePos, 1, eyePos, 0)
        GLES20.glUseProgram(0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        //angle = 185f;
        angle += 1f

        //compute first part of MVP (no model yet)
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])

        //compute model Matrix
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, 0f, 8f)
        Matrix.rotateM(modelM, 0, angle, 0.2f, 1f, 0.4f)

        //send model matrix
        GLES20.glUniformMatrix4fv(umodelM, 1, false, modelM, 0)

        //compute second part of MVP
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)

        //send MVP
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)

        //compute T(modelM^-1) and send
        Matrix.invertM(inverseModel, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(uInverseModel, 1, true, inverseModel, 0)
        //the third param of glUniformMatrix4fv is set to true (i.e. transpose while send)

        //draw (finally...)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, countFacesToElement, GLES20.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }

}