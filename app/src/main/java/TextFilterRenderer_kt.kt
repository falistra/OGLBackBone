package ogles.oglbackbone

import android.graphics.BitmapFactory
import android.opengl.*
import android.util.Log
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class TextFilterRenderer_kt : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private lateinit var texObjId: IntArray
    private var texUnit = 0
    private var shaderHandle = 0
    private var MVPloc = 0

    private val viewM: FloatArray
    private val modelM: FloatArray
    private val projM: FloatArray
    private val MVP: FloatArray
    private val temp: FloatArray

    private var countFacesToElement = 0

    init {
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
             layout(location = 2) in vec2 texCoord;
             out vec2 varyingTexCoord;
             uniform mat4 MVP;
             
             void main(){
             varyingTexCoord = texCoord;
             gl_Position = MVP * vec4(vPos,1);
             }
             """.trimIndent()
        val fragmentSrc = """
             #version 300 es
             
             precision mediump float;
             
             uniform sampler2D tex;
             in vec2 varyingTexCoord;
             out vec4 fragColor;
             
             void main() {
             fragColor = texture(tex,varyingTexCoord);
             }
             """.trimIndent()
        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        if (shaderHandle < 0) {
            Log.v(
                BasicRenderer.TAG,
                "Error in shader(s) compile. Check SHADER_COMPILER logcat tag. Exiting"
            )
            System.exit(-1)
        }

        //mapping vertices to s,t texture coordinates
        val vertices = floatArrayOf(
            -1f,
            0f,
            -1f,
            0f,
            10f,
            1f,
            0f,
            1f,
            10f,
            0f,
            1f,
            0f,
            -1f,
            10f,
            10f,
            -1f,
            0f,
            1f,
            0f,
            0f
        )
        //scaling factor = 10. Put 1 and control texture wrapping/scaling
        //through a frag. shader uniform.
        val indices = intArrayOf(
            0, 1, 2,
            0, 3, 1
        )
        VAO = IntArray(1) //one VAO to bind the vpos
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
            java.lang.Float.BYTES * 5,
            0
        ) //vpos
        GLES20.glVertexAttribPointer(
            2,
            2,
            GLES20.GL_FLOAT,
            false,
            java.lang.Float.BYTES * 5,
            3 * java.lang.Float.BYTES
        ) //texcoord
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBO[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
            GLES20.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
        MVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
        texUnit = GLES20.glGetUniformLocation(shaderHandle, "tex")
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        val bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.tiles, opts)
        if (bitmap != null) Log.v(
            BasicRenderer.TAG, "bitmap of size " + bitmap.width + "x" + bitmap.height + " loaded " +
                    "with format " + bitmap.config.name
        )
        texObjId = IntArray(1)
        GLES20.glGenTextures(1, texObjId, 0)
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
        val extensions = GLES10.glGetString(GLES20.GL_EXTENSIONS)
        Log.v(BasicRenderer.TAG, extensions)
        var e = ""
        for (i in 0 until extensions.length) {
            if (extensions[i] == ' ') {
                Log.v(BasicRenderer.TAG, e)
                e = ""
            } else e += extensions[i]
        }
        if (extensions.contains("anisotropic")) Log.v(
            BasicRenderer.TAG,
            "Anisotropic filtering supported"
        ) else Log.v(
            BasicRenderer.TAG, "Anisotropic filtering NOT supported. " +
                    "(Might be false if an emulator is used...)"
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform1i(texUnit, 0) //0 because active texture is GL_TEXTURE0.
        //glActiveTexture(GL_TEXTURE0+0) and glUniform1i(texUnit,GL_TEXTURE0+0) would be more correct
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap!!.recycle()
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, -1f, 0f)
        //Matrix.rotateM(modelM,0,90,1,0,0);
        Matrix.scaleM(modelM, 0, 10f, 1f, 10f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, countFacesToElement, GLES20.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
    }


}