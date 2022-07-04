package ogles.oglbackbone

import android.content.Context
import android.graphics.BitmapFactory
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

class MultiTexPlane_kt : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private lateinit var texObjId: IntArray
    private lateinit var texUnit: IntArray
    private var texUniSky = 0
    private lateinit var texUnitSky: IntArray
    private var shaderHandle = 0
    private var shaderHandleSky = 0
    private var MVPloc = 0
    private var MVPlocSky = 0

    private var texSelector = 0
    private var texSelectorUni = 0

    private val viewM: FloatArray
    private val modelM: FloatArray
    private val projM: FloatArray
    private val MVP: FloatArray
    private val temp: FloatArray

    private var deltaXsky = 0f

    private var countFacesToElementPlane = 0
    private var countFacesToElementSkyDome = 0

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

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)

        this.surface!!.setOnClickListener {
            texSelector++
            texSelector %= 3
            if (texSelector == 0) Log.v(
                BasicRenderer.TAG,
                "Multi texturing enable"
            ) else if (texSelector == 1) Log.v(
                BasicRenderer.TAG,
                "Unit 0 only"
            ) else Log.v(BasicRenderer.TAG, "Unit 1 only")
        }
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        super.onSurfaceChanged(gl10, w, h)
        val aspect = w.toFloat() / (if (h == 0) 1 else h).toFloat()
        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 500f)
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
            
            uniform sampler2D grass;
            uniform sampler2D sand;
            uniform sampler2D mixmap;
            uniform int texSelector;
            in vec2 varyingTexCoord;
            out vec4 fragColor;
            
            void main() {
            vec4 k = texture(mixmap,varyingTexCoord);
            if(texSelector==0)
            fragColor = mix(texture(sand,varyingTexCoord),texture(grass,varyingTexCoord),k);
            else if(texSelector==1)
            fragColor = texture(grass,varyingTexCoord);
            else fragColor = texture(sand,varyingTexCoord);
            }
            """.trimIndent()

        val fragmentSrcSky = """
            #version 300 es
            
            precision mediump float;
            
            uniform sampler2D skytex;
            in vec2 varyingTexCoord;
            out vec4 fragColor;
            
            void main() {
            fragColor = texture(skytex,varyingTexCoord);
            }
            """.trimIndent()

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        shaderHandleSky = ShaderCompiler.createProgram(vertexSrc, fragmentSrcSky)

        //mapping vertices to s,t texture coordinates
        val vertices = floatArrayOf(
            -1f,
            0f,
            -1f,
            0f,
            5f,
            1f,
            0f,
            1f,
            5f,
            0f,
            1f,
            0f,
            -1f,
            5f,
            5f,
            -1f,
            0f,
            1f,
            0f,
            0f
        )

        val indices = intArrayOf(
            0, 1, 2,
            0, 3, 1
        )

        VAO = IntArray(2) //0: plane, 1: skydome


        //plane

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

        //skydome:


        //skydome:
        val `is`: InputStream
        var verticesSky: FloatArray? = null
        var indicesSky: IntArray? = null

        try {
            `is` = context!!.assets.open("skydome.ply")
            val po = PlyObject(`is`)
            po.parse()
            //Log.v("TAG",po.toString());
            verticesSky = po.vertices
            indicesSky = po.indices
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }

        val vertexDataSky = ByteBuffer.allocateDirect(verticesSky!!.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexDataSky.put(verticesSky)
        vertexDataSky.position(0)

        val indexDataSky = ByteBuffer.allocateDirect(indicesSky!!.size * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexDataSky.put(indicesSky)
        indexDataSky.position(0)

        countFacesToElementSkyDome = indicesSky.size

        val VBOsky = IntArray(2)
        GLES20.glGenBuffers(2, VBOsky, 0)

        GLES30.glBindVertexArray(VAO[1])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBOsky[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * vertexDataSky.capacity(),
            vertexDataSky, GLES20.GL_STATIC_DRAW
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

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBOsky[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexDataSky.capacity(), indexDataSky,
            GLES20.GL_STATIC_DRAW
        )

        GLES30.glBindVertexArray(0)

        //uniform fetching for planes

        //uniform fetching for planes
        texUnit = IntArray(3)
        MVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
        texUnit[0] = GLES20.glGetUniformLocation(shaderHandle, "grass")
        texUnit[1] = GLES20.glGetUniformLocation(shaderHandle, "sand")
        texUnit[2] = GLES20.glGetUniformLocation(shaderHandle, "mixmap")
        texSelectorUni = GLES20.glGetUniformLocation(shaderHandle, "texSelector")

        //now for the skydome

        //now for the skydome
        MVPlocSky = GLES20.glGetUniformLocation(shaderHandleSky, "MVP")
        texUniSky = GLES20.glGetUniformLocation(shaderHandleSky, "skytex")

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)

        val opts = BitmapFactory.Options()
        opts.inScaled = false
        var bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.grass, opts)

        if (bitmap != null) Log.v(
            BasicRenderer.TAG, "bitmap of size " + bitmap.width + "x" + bitmap.height + " loaded " +
                    "with format " + bitmap.config.name
        )

        texObjId = IntArray(3)
        GLES20.glGenTextures(3, texObjId, 0)
        //index 0: grass
        //index 1: dirt
        //index 3: mix map

        //index 0: grass
        //index 1: dirt
        //index 3: mix map
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        //tex filtering try both "nearest"
        //tex filtering try both "nearest"
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        //try other params "i" for wrapping
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
        //glActiveTexture(GL_TEXTURE0+0) and glUniform1i(texUnit,GL_TEXTURE0+0) would be more correct
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        bitmap!!.recycle()

        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.sand, opts)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[1])
        //tex filtering try both "nearest"
        //tex filtering try both "nearest"
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        //try other params "i" for wrapping
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

        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.reactdiffuse, opts)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[2])
        //tex filtering try both "nearest"
        //tex filtering try both "nearest"
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        //try other params "i" for wrapping
        //try other params "i" for wrapping
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[2])
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform1i(texUnit[2], 2)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        bitmap.recycle()

        //now for the skydome:


        //now for the skydome:
        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.skydome, opts)

        if (bitmap != null) Log.v(
            BasicRenderer.TAG, "bitmap of size " + bitmap.width + "x" + bitmap.height + " loaded " +
                    "with format " + bitmap.config.name
        )

        texUnitSky = IntArray(1)
        GLES20.glGenTextures(1, texUnitSky, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSky[0])
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSky[0])
        GLES20.glUseProgram(shaderHandleSky)
        GLES20.glUniform1i(texUniSky, 0)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)


    }

    override fun onDrawFrame(gl10: GL10?) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        //update cam pos

        //update cam pos
        Matrix.translateM(viewM, 0, 0f, 0f, 0.01f)

        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)

        //plane


        //plane
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, -1f, 0f)
        //Matrix.rotateM(modelM,0,90,1,0,0);
        //Matrix.rotateM(modelM,0,90,1,0,0);
        Matrix.scaleM(modelM, 0, 20f, 1f, 20f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)

        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[0])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[1])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[2])

        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glUniform1i(texSelectorUni, texSelector)
        GLES20.glUniformMatrix4fv(MVPloc, 1, false, MVP, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            countFacesToElementPlane,
            GLES20.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)

        //sky

        //sky
        deltaXsky += 0.01f

        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, -2f, 0.01f)
        Matrix.rotateM(modelM, 0, deltaXsky, 0f, 1f, 0f)
        Matrix.scaleM(modelM, 0, 40f, 40f, 40f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)

        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSky[0])
        GLES20.glUseProgram(shaderHandleSky)
        GLES30.glBindVertexArray(VAO[1])
        GLES20.glUniformMatrix4fv(MVPlocSky, 1, false, MVP, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            countFacesToElementSkyDome,
            GLES20.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)


    }


}