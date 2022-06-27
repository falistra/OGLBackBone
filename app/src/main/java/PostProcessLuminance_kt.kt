package ogles.oglbackbone

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.*
import android.util.Log
import android.view.MotionEvent
import ogles.oglbackbone.utils.PlyObject
import ogles.oglbackbone.utils.ShaderCompiler
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PostProcessLuminance_kt  : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private var texObjId: IntArray = IntArray(1)
    private var texUnit: IntArray = IntArray(1)
    private var texUniSkyAndBunny = 0
    private var  texUnitSkyAndBunny: IntArray = IntArray(1)
    private var texUnitFBO: IntArray = IntArray(1)
    private var uTexFBO = 0
    private var shaderHandle = 0
    private var shaderHandleSkyAndBunny = 0
    private var shaderHandleFBO = 0
    private var MVPloc = 0
    private var MVPlocSkyAndBunny = 0

    private val viewM: FloatArray = FloatArray(16)
    private val modelM: FloatArray = FloatArray(16)
    private val projM: FloatArray = FloatArray(16)
    private val MVP: FloatArray = FloatArray(16)
    private val temp: FloatArray = FloatArray(16)

    private var deltaXsky = 0f

    private var countFacesToElementPlane = 0
    private var countFacesToElementSkyDome = 0
    private var countFacesToElementBunny = 0

    private var scissorPosition = 0f
    private var uScissorPosition = 0

    private var FBO: IntArray = IntArray(1)

    init {
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)
    }

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)
        this.surface!!.setOnTouchListener { view, motionEvent ->
            val action = motionEvent.actionMasked
            when (action) {
                MotionEvent.ACTION_MOVE -> scissorPosition = motionEvent.x
            }
            true
        }
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        super.onSurfaceChanged(gl10, w, h)
        Log.v("TAG", "surf changed")
        val aspect = w.toFloat() / (if (h == 0) 1 else h).toFloat()
        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 500f)
        Matrix.setLookAtM(viewM, 0, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f)
        Log.v("TAG", "SIZE CH $w $h")
        if (currentScreen!!.x != 0 && currentScreen!!.y != 0 && FBO == null) {
            FBO = IntArray(1)
            GLES20.glGenFramebuffers(1, FBO, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[0])

            //texture
            Log.v("TAG", "Size now is " + currentScreen!!.x + " " + currentScreen!!.y)
            texUnitFBO = IntArray(1)
            GLES20.glGenTextures(1, texUnitFBO, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitFBO[0])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGB,
                currentScreen!!.x,
                currentScreen!!.y,
                0,
                GLES20.GL_RGB,
                GLES20.GL_UNSIGNED_BYTE,
                null
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                texUnitFBO[0], 0
            )

            //depth renderbuffer
            val RBO = IntArray(1)
            GLES20.glGenRenderbuffers(1, RBO, 0)
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, RBO[0])
            GLES20.glRenderbufferStorage(
                GLES20.GL_RENDERBUFFER,
                GLES20.GL_DEPTH_COMPONENT16,
                currentScreen!!.x,
                currentScreen!!.y
            )
            //we attach the RBO to the Currently bind FBO
            GLES20.glFramebufferRenderbuffer(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER,
                RBO[0]
            )
            val error = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) //finalize operations
            if (error != GLES20.GL_FRAMEBUFFER_COMPLETE) Log.v(
                BasicRenderer.TAG,
                "Error in FBO completeness: $error"
            ) else Log.v(BasicRenderer.TAG, "FBO is ok!")
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            uTexFBO = GLES20.glGetUniformLocation(shaderHandleFBO, "fbo")
            uScissorPosition = GLES20.glGetUniformLocation(shaderHandleFBO, "scisPos")
            scissorPosition = (currentScreen!!.x / 2).toFloat()
            GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitFBO[0])
            GLES20.glUseProgram(shaderHandleFBO)
            GLES20.glUniform1i(uTexFBO, 0)
            GLES20.glUseProgram(0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }
    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        Log.v("TAG", "surf created")
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
         in vec2 varyingTexCoord;
         out vec4 fragColor;
         
         void main() {
         vec4 k = texture(mixmap,varyingTexCoord);
         k+=vec4(0.15f,0.25f,0.15f,0.0f);
         fragColor = mix(texture(sand,varyingTexCoord),texture(grass,varyingTexCoord),k);
         }
         """.trimIndent()

        //Also for the bunny! Is a simple passthrough vpos -> fragcol -> texcol shader!
        val fragmentSrcSky = """
         #version 300 es
         
         precision mediump float;
         
         uniform sampler2D skytex;
         in vec2 varyingTexCoord;
         out vec4 fragColor;
         
         void main() {
         fragColor = texture(skytex,varyingTexCoord);
         fragColor.a = 1.0;
         }
         """.trimIndent()
        val FBOvertex = """
         #version 300 es
         
         layout(location = 1) in vec3 vPos;
         layout(location = 2) in vec2 texCoord;
         out vec2 texel;
         
         void main(){
         texel = texCoord;
         gl_Position = vec4(vPos,1);
         }
         """.trimIndent()
        val FBOfragmentLum = """
         #version 300 es
         
         precision mediump float;
         
         uniform sampler2D fbo;
         uniform float scisPos;
         in vec2 texel;
         out vec4 fragColor;
         
         void main() {
         vec4 color = texture(fbo,texel);
         if(gl_FragCoord.x<scisPos){
         float lum = 0.2126*color.r + 0.7152*color.g + 0.0722*color.b;
         fragColor = vec4(lum,lum,lum,1);
         }
         else fragColor = color;
         }
         """.trimIndent()
        val FBOfragmentSobel = """
         #version 300 es
         
         precision mediump float;
         
         uniform sampler2D fbo;
         uniform float scisPos;
         in vec2 texel;
         out vec4 fragColor;
         
         void main() {
         vec4 color = texture(fbo,texel);
         if(gl_FragCoord.x<scisPos){
         float filterS[9] = float[](1.0,   1.0, 1.0, 
         1.0, -8.0, 1.0, 
         1.0,   1.0, 1.0);
         float off = 1.0/300.0;
         vec4 sum = vec4(0.0);
         sum += texture(fbo,texel + vec2(-off,-off))*filterS[0];
         sum += texture(fbo,texel + vec2(0,-off))*filterS[1];
         sum += texture(fbo,texel + vec2(off,-off))*filterS[2];
         sum += texture(fbo,texel + vec2(-off,0))*filterS[3];
         sum += texture(fbo,texel + vec2(0,0))*filterS[4];
         sum += texture(fbo,texel + vec2(off,0))*filterS[5];
         sum += texture(fbo,texel + vec2(-off,off))*filterS[6];
         sum += texture(fbo,texel + vec2(0,off))*filterS[7];
         sum += texture(fbo,texel + vec2(off,off))*filterS[8];
         if(dot(vec3(0.2126,0.7152,0.0722),sum.rgb)>0.85)
         sum=vec4(0,0,0,0);
         else sum = color;
         fragColor = sum;
         }
         else fragColor = color;
         }
         """.trimIndent()
        val FBOfragmentGBlur = """
         #version 300 es
         
         precision mediump float;
         
         uniform sampler2D fbo;
         uniform float scisPos;
         in vec2 texel;
         out vec4 fragColor;
         
         void main() {
         vec4 color = texture(fbo,texel);
         if(gl_FragCoord.x<scisPos){
         float filterB[9] = float[](1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0,
         2.0 / 16.0, 4.0 / 16.0, 2.0 / 16.0,
         1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0);
         float off = 1.0/250.0;
         vec4 sum = vec4(0.0);
         sum += texture(fbo,texel + vec2(-off,-off))*filterB[0];
         sum += texture(fbo,texel + vec2(0,-off))*filterB[1];
         sum += texture(fbo,texel + vec2(off,-off))*filterB[2];
         sum += texture(fbo,texel + vec2(-off,0))*filterB[3];
         sum += texture(fbo,texel + vec2(0,0))*filterB[4];
         sum += texture(fbo,texel + vec2(off,0))*filterB[5];
         sum += texture(fbo,texel + vec2(-off,off))*filterB[6];
         sum += texture(fbo,texel + vec2(0,off))*filterB[7];
         sum += texture(fbo,texel + vec2(off,off))*filterB[8];
         fragColor = sum;
         }
         else fragColor = color;
         }
         """.trimIndent()
        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        shaderHandleSkyAndBunny = ShaderCompiler.createProgram(vertexSrc, fragmentSrcSky)
        shaderHandleFBO = ShaderCompiler.createProgram(
            FBOvertex,
            FBOfragmentSobel
        )
        //FBOfragmentLum);
        //FBOfragmentGBlur);

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
        VAO = IntArray(4) //0: plane, 1: skydome, 2: bunny,  3: post process FBO

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
        val VBO = IntArray(2) //0: vertex attributes, 1: indices
        GLES20.glGenBuffers(2, VBO, 0)
        GLES30.glGenVertexArrays(4, VAO, 0)
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
        var `is`: InputStream
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

        //Stanford bunny
        var verticesBunny: FloatArray? = null
        var indicesBunny: IntArray? = null
        try {
            `is` = context!!.assets.open("stanbunny.ply")
            val po = PlyObject(`is`)
            po.parse()
            //Log.v("TAG",po.toString());
            verticesBunny = po.vertices
            indicesBunny = po.indices
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        val vertexDataBunny =
            ByteBuffer.allocateDirect(verticesBunny!!.size * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        vertexDataBunny.put(verticesBunny)
        vertexDataBunny.position(0)
        val indexDataBunny = ByteBuffer.allocateDirect(indicesBunny!!.size * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexDataBunny.put(indicesBunny)
        indexDataBunny.position(0)
        countFacesToElementBunny = indicesBunny.size
        val VBObunny = IntArray(2)
        GLES20.glGenBuffers(2, VBObunny, 0)
        GLES30.glBindVertexArray(VAO[2])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBObunny[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * vertexDataBunny.capacity(),
            vertexDataBunny, GLES20.GL_STATIC_DRAW
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
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBObunny[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            Integer.BYTES * indexDataBunny.capacity(),
            indexDataBunny,
            GLES20.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)

        //Quad for VBO
        val quadVertices =
            floatArrayOf( // vertex attributes for a quad that fills the entire screen
                // positions   // texCoords
                -1.0f, 1.0f, 0.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 0.0f,
                1.0f, -1.0f, 1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f, 1.0f,
                1.0f, -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f
            )
        val quadVerticesData = ByteBuffer.allocateDirect(quadVertices.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        quadVerticesData.put(quadVertices)
        quadVerticesData.position(0)
        val VBOquad = IntArray(1)
        GLES20.glGenBuffers(1, VBOquad, 0) //1 VBO, no indexing this time.
        GLES30.glBindVertexArray(VAO[3])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBOquad[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER, java.lang.Float.BYTES * quadVerticesData.capacity(),
            quadVerticesData, GLES20.GL_STATIC_DRAW
        )
        GLES20.glVertexAttribPointer(
            1,
            2,
            GLES20.GL_FLOAT,
            false,
            java.lang.Float.BYTES * 4,
            0
        ) //vpos
        GLES20.glVertexAttribPointer(
            2,
            2,
            GLES20.GL_FLOAT,
            false,
            java.lang.Float.BYTES * 4,
            2 * java.lang.Float.BYTES
        ) //texcoord
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES30.glBindVertexArray(0)
        texUnit = IntArray(3)

        //uniform fetching for planes
        MVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
        texUnit[0] = GLES20.glGetUniformLocation(shaderHandle, "grass")
        texUnit[1] = GLES20.glGetUniformLocation(shaderHandle, "sand")
        texUnit[2] = GLES20.glGetUniformLocation(shaderHandle, "mixmap")

        //now for the skydome
        MVPlocSkyAndBunny = GLES20.glGetUniformLocation(shaderHandleSkyAndBunny, "MVP")
        texUniSkyAndBunny = GLES20.glGetUniformLocation(shaderHandleSkyAndBunny, "skytex")
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES10.GL_CCW)
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        var bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.grass, opts)
        if (bitmap != null) Log.v(
            "TAG", "bitmap of size " + bitmap.width + "x" + bitmap.height + " loaded " +
                    "with format " + bitmap.config.name
        )
        texObjId = IntArray(3)
        GLES20.glGenTextures(3, texObjId, 0)
        //index 0: grass
        //index 1: dirt
        //index 2: mix map
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
        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.sand, opts)
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
        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.reactdiffuse, opts)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[2])
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
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texObjId[2])
        GLES20.glUseProgram(shaderHandle)
        GLES20.glUniform1i(texUnit[2], 2)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()

        //now for the skydome:
        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.skydome, opts)
        if (bitmap != null) Log.v(
            BasicRenderer.TAG, "bitmap of size " + bitmap.width + "x" + bitmap.height + " loaded " +
                    "with format " + bitmap.config.name
        )
        texUnitSkyAndBunny = IntArray(2) //0: skydome, 1: bunny furry texture
        GLES20.glGenTextures(2, texUnitSkyAndBunny, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSkyAndBunny[0])
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
        bitmap.recycle()

        //finally the bunny:
        bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.fabric, opts)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSkyAndBunny[1])
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
        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            texUnitSkyAndBunny[0]
        ) //does not matter which between 0 and 1.
        GLES20.glUseProgram(shaderHandleSkyAndBunny)
        GLES20.glUniform1i(texUniSkyAndBunny, 0)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST) //we need depth here.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[0]) //switch to custom FBO
        draw() //offscreen render to texture of our beautiful scene.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) //switch back to default FBO
        GLES20.glDisable(GLES20.GL_DEPTH_TEST) //we don't need depth anymore...
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT) //clear everything.
        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitFBO[0])
        GLES30.glBindVertexArray(VAO[3]) //that's a quad: it's as big as the screen.
        GLES20.glUseProgram(shaderHandleFBO) //post processing effect.
        GLES20.glUniform1f(uScissorPosition, scissorPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6) //quad drawcall
        GLES20.glUseProgram(0) //resetting states
        GLES30.glBindVertexArray(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(scissorPosition.toInt(), 0, 5, currentScreen!!.y)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(1f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    private fun draw() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        //update cam pos
        //Matrix.translateM(viewM,0,0,0,0.01f);
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)

        //plane
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, -1f, 0f)
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
        deltaXsky += 0.01f
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, -2f, 0.01f)
        Matrix.rotateM(modelM, 0, deltaXsky, 0f, 1f, 0f)
        Matrix.scaleM(modelM, 0, 40f, 40f, 40f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
        GLES20.glActiveTexture(GLES10.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSkyAndBunny[0])
        GLES20.glUseProgram(shaderHandleSkyAndBunny)
        GLES30.glBindVertexArray(VAO[1])
        GLES20.glUniformMatrix4fv(MVPlocSkyAndBunny, 1, false, MVP, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            countFacesToElementSkyDome,
            GLES20.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)

        //bunny
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, 0f, 0f, 6f)
        Matrix.rotateM(modelM, 0, deltaXsky * 20.0f, 0f, 1f, 0f)
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)

        //active texture unit is still zero!
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texUnitSkyAndBunny[1])
        GLES20.glUseProgram(shaderHandleSkyAndBunny)
        GLES30.glBindVertexArray(VAO[2])
        GLES20.glUniformMatrix4fv(MVPlocSkyAndBunny, 1, false, MVP, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            countFacesToElementBunny,
            GLES20.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

}