package ogles.oglbackbone

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.*
import android.util.Log
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin
import kotlin.system.exitProcess

class TexAndLightRenderer_kt :  BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private lateinit var texObjId: IntArray
    private lateinit var texUnit: IntArray
    private var shaderHandle = 0
    private var MVPloc = 0

    private var blinnPhong = 0
    private var uBlinnPhong = 0

    private val viewM: FloatArray = FloatArray(16)
    private val modelM: FloatArray = FloatArray(16)

    private var uModelM = 0

    private val projM: FloatArray = FloatArray(16)
    private val MVP: FloatArray = FloatArray(16)
    private val temp: FloatArray = FloatArray(16)
    private val inverseModel: FloatArray = FloatArray(16)

    private var uInverseModel = 0

    private val lightPos: FloatArray = floatArrayOf(05f, 0.5f, 5f)
    private var uLightPos = 0

    private val eyePos: FloatArray = floatArrayOf(0f, 0f, 10f)
    private var uEyePos = 0

    private var countFacesToElementPlane = 0

    private var frame = 0f

    init {
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)
    }

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)
        this.surface!!.setOnClickListener {
            blinnPhong = if (blinnPhong == 0) 1 else 0
            Log.v(
                BasicRenderer.TAG, (if (blinnPhong == 0) "Phong" else "Blinn-Phong") +
                        " lighting model activated"
            )
        }
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
             
             layout(location = 1) in vec3 vPos;
             layout(location = 2) in vec3 normal;
             layout(location = 3) in vec2 texCoord;
             out vec2 varyingTexCoord;
             out vec3 norm;
             out vec3 fragModel;
             uniform mat4 MVP;
             uniform mat4 modelM;
             uniform mat4 inverseModel;
             
             void main(){
             norm = normalize(vec3(inverseModel*vec4(normal,1)));
             fragModel = vec3(modelM*vec4(vPos,1));
             varyingTexCoord = texCoord;
             gl_Position = MVP * vec4(vPos,1);
             }
             """.trimIndent()
        val fragmentSrc = """
             #version 300 es
             
             precision mediump float;
             
             uniform sampler2D moondiff;
             uniform sampler2D moonspec;
             uniform int blinnPhong;
             uniform vec3 lightPos;
             uniform vec3 eyePos;
             in vec2 varyingTexCoord;
             in vec3 norm;
             in vec3 fragModel;
             out vec4 fragColor;
             
             void main() {
             vec4 diffuseMap = texture(moondiff,varyingTexCoord);
             vec4 specMap = texture(moonspec,varyingTexCoord);
             vec4 ambientMap = mix(diffuseMap,specMap,vec4(0.5));
             vec4 ambientComponent = 0.15 * ambientMap;
             vec3 lightDir = normalize(lightPos-fragModel);
             vec3 eyeDir = normalize(eyePos-fragModel);
             float diffuse = max(dot(lightDir,norm),0.0);
             float specular = 0.0;
             if(blinnPhong==0){
             vec3 refl = reflect(-lightDir,norm);
             specular = pow( max(dot(eyeDir,refl),0.0), 1.0);
             }else{
             vec3 halfWay = normalize(lightDir+eyeDir);
             specular = pow(max(dot(halfWay,norm),0.0),1.0);
             }
             fragColor = ambientComponent + diffuse*diffuseMap + specular*specMap; 
             }
             """.trimIndent()
        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        if (shaderHandle == -1) exitProcess(-1)

        //mapping vertices to s,t texture coordinates.
        //also normals...
        val vertices = floatArrayOf(
            -1f,
            0f,
            -1f,
            0f,
            1f,
            0f,
            0f,
            5f,
            1f,
            0f,
            1f,
            0f,
            1f,
            0f,
            5f,
            0f,
            1f,
            0f,
            -1f,
            0f,
            1f,
            0f,
            5f,
            5f,
            -1f,
            0f,
            1f,
            0f,
            1f,
            0f,
            0f,
            0f
        )
        val indices = intArrayOf(
            0, 1, 2,
            0, 3, 1
        )
        VAO = IntArray(1) //0: plane

        //plane
        val vertexData = ByteBuffer.allocateDirect(vertices.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexData.put(vertices)
        vertexData.position(0)
        val indexData = ByteBuffer.allocateDirect(indices.size * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexData.put(indices)
        indexData.position(0)
        countFacesToElementPlane = indices.size
        val VBO = IntArray(2) //0: vpos, 1: indices
        GLES20.glGenBuffers(2, VBO, 0)
        GLES30.glGenVertexArrays(2, VAO, 0)
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
            java.lang.Float.BYTES * 8,
            0
        ) //vpos
        GLES20.glVertexAttribPointer(
            2,
            3,
            GLES20.GL_FLOAT,
            false,
            java.lang.Float.BYTES * 8,
            3 * java.lang.Float.BYTES
        ) //normals
        GLES20.glVertexAttribPointer(
            3,
            2,
            GLES20.GL_FLOAT,
            false,
            java.lang.Float.BYTES * 8,
            6 * java.lang.Float.BYTES
        ) //st tex coord
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glEnableVertexAttribArray(3)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBO[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
            GLES20.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)

        //uniform fetching
        texUnit = IntArray(3)
        MVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
        uModelM = GLES20.glGetUniformLocation(shaderHandle, "modelM")
        uInverseModel = GLES20.glGetUniformLocation(shaderHandle, "inverseModel")
        texUnit[0] = GLES20.glGetUniformLocation(shaderHandle, "moondiff")
        texUnit[1] = GLES20.glGetUniformLocation(shaderHandle, "moonspec")
        uBlinnPhong = GLES20.glGetUniformLocation(shaderHandle, "blinnPhong")
        uEyePos = GLES20.glGetUniformLocation(shaderHandle, "eyePos")
        uLightPos = GLES20.glGetUniformLocation(shaderHandle, "lightPos")

        //send "fixed" uniforms
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform3f(uLightPos, lightPos[0], lightPos[1], lightPos[2])
        GLES20.glUniform3f(uEyePos, eyePos[0], eyePos[1], eyePos[2])
        GLES20.glUseProgram(0)

        //tex units will be sent as we create GL_TEXTUREs
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        var bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.moondiff, opts)
        if (bitmap != null) Log.v(
            BasicRenderer.TAG, "bitmap of size " + bitmap.width + "x" + bitmap.height + " loaded " +
                    "with format " + bitmap.config.name
        )
        texObjId = IntArray(2)
        GLES20.glGenTextures(2, texObjId, 0)
        //index 0: diff
        //index 1: spec
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        //tex filtering try both "nearest"
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        //try other params "i" for wrapping
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform1i(texUnit[0], 0) //0 because active texture is GL_TEXTURE0.
        //glActiveTexture(GL_TEXTURE0+0) and glUniform1i(texUnit,GL_TEXTURE0+0) would be more correct
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap!!.recycle()
        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.moonspec, opts)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[1])
        //tex filtering try both "nearest"
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        //try other params "i" for wrapping
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[1])
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform1i(texUnit[1], 1)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        //activate textures
        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[1])

        //compute first part of MVP (no model yet)
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glUniform1i(uBlinnPhong, blinnPhong)

        //compute model Matrix for the plane
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, -1f, 0f)
        Matrix.scaleM(modelM, 0, 10f, 1f, 10f)

        //send model matrix
        GLES20.glUniformMatrix4fv(uModelM, 1, false, modelM, 0)

        //compute second part of MVP
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)

        //send MVP
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)

        //find T(modelM^-1) and send
        Matrix.invertM(inverseModel, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(uInverseModel, 1, true, inverseModel, 0)
        frame++
        lightPos[2] = 10f * sin((frame / 22.5f).toDouble()).toFloat()
        GLES20.glUniform3fv(uLightPos, 1, lightPos, 0)

        //draw (finally...)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            countFacesToElementPlane,
            GLES20.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }


}