package ogles.oglbackbone

import android.app.Activity
import android.content.Context
import android.opengl.*
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ClothVerletCS_kt : BasicRenderer_kt() {
    private lateinit var VAO: IntArray
    private lateinit var VBO: IntArray
    private var shaderHandle = 0
    private var computePositionShader = 0
    private var computeStickShader = 0

    private var uMVPloc = 0
    private var uNPosition = 0
    private var uNSticks = 0
    private var uColor = 0
    private var uSwitch = 0
    private var uFrameCount = 0

    private val viewM: FloatArray = FloatArray(16)
    private val modelM: FloatArray = FloatArray(16)
    private val projM: FloatArray = FloatArray(16)
    private val MVP: FloatArray = FloatArray(16)
    private val temp: FloatArray = FloatArray(16)

    private var hVPos //host side vertex local position
            : FloatBuffer? = null
    private var hVPosOld //host side for vertex local previous position
            : FloatBuffer? = null
    private var hIndices //host side indices
            : IntBuffer? = null
    private var hSticks //host side sticks
            : IntBuffer? = null

    private val THREADS_PER_GROUP_X = 16
    private val THREADS_PER_GROUP_Y = 16
    //we divide the tesselated quad along the X and Y dimension
    //assigning a thread on each cloth intersection point.

    //we divide the tesselated quad along the X and Y dimension
    //assigning a thread on each cloth intersection point.
    private val NUM_ELEMENTS = THREADS_PER_GROUP_X * THREADS_PER_GROUP_Y * 4
    //we use a vec4 for each vertex. (x,y,z,isFixed)

    //we use a vec4 for each vertex. (x,y,z,isFixed)
    private val NUM_INDICES = (THREADS_PER_GROUP_X - 1) * (THREADS_PER_GROUP_Y - 1) * 6
    private val NUM_STICKS = 2 * (NUM_INDICES / 3 +
            (THREADS_PER_GROUP_Y - 1) +
            (THREADS_PER_GROUP_X - 1))

    //cloth size:
    private val CLOTH_WIDTH = 1.0f
    private val CLOTH_HEIGHT = 1.0f

    private var countFaces = 0
    private var countFrame = 0
    private var resubmit = false

    private var renderTimeDisplayTexT: TextView? = null
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var deltaBetweenFrames = 0f

    private fun round(value: Float, precision: Int): Float {
        val scale = Math.pow(10.0, precision.toDouble()).toInt()
        return Math.round(value * scale).toFloat() / scale
    }
       
    
    init {
        Matrix.setIdentityM(viewM, 0)
        Matrix.setIdentityM(modelM, 0)
        Matrix.setIdentityM(projM, 0)
        Matrix.setIdentityM(MVP, 0)

        resubmit = false

        //allocating memory on host side

        //allocating memory on host side
        hVPos = ByteBuffer.allocateDirect(NUM_ELEMENTS * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        hVPosOld = ByteBuffer.allocateDirect(NUM_ELEMENTS * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        hIndices = ByteBuffer.allocateDirect(NUM_INDICES * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()

        hSticks = ByteBuffer.allocateDirect(NUM_STICKS * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()

        //procedurally generation of a indexed, tessellated quad mesh:


        //procedurally generation of a indexed, tessellated quad mesh:
        val stepX = CLOTH_WIDTH / (THREADS_PER_GROUP_X - 1).toFloat()
        val stepY = CLOTH_HEIGHT / (THREADS_PER_GROUP_Y - 1).toFloat()

        val startPointX = -CLOTH_WIDTH / 2.0f
        val startPointY = -CLOTH_HEIGHT / 2.0f

        val tempArray = FloatArray(4)
        var count = 0
        for (y in 0 until THREADS_PER_GROUP_Y) {
            for (x in 0 until THREADS_PER_GROUP_X) {
                tempArray[0] = round(startPointX + x.toFloat() * stepX, 3)
                tempArray[1] = round(startPointY + y.toFloat() * stepY, 3)
                tempArray[2] = 0f
                if (x == 0 && y == THREADS_PER_GROUP_Y - 1 || x == THREADS_PER_GROUP_X - 1 && y == THREADS_PER_GROUP_Y - 1) tempArray[3] =
                    1.0f else tempArray[3] = -1.0f

                //Log.v(TAG,"C: " + count+ " " + tempArray[0] + " " + tempArray[1] + " " + tempArray[2] + " " + tempArray[3]);
                hVPos!!.put(tempArray)
                //should give initial velocity on positive Z
                tempArray[2] = 0.0000f
                /**(THREADS_PER_GROUP_Y*THREADS_PER_GROUP_X-count) */
                hVPosOld!!.put(tempArray)
                count++
            }
        }

        //count=0;

        //count=0;
        val tempTriIndex = IntArray(6)
        for (y in 0 until THREADS_PER_GROUP_Y - 1) {
            for (x in 0 until THREADS_PER_GROUP_X - 1) {
                //first triangle
                val startPoint = y * THREADS_PER_GROUP_X + x
                tempTriIndex[0] = startPoint
                tempTriIndex[1] = startPoint + 1
                tempTriIndex[2] = startPoint + THREADS_PER_GROUP_X
                /*Log.v(TAG,"1TRI "+ count+ " " + tempTriIndex[0] + " " + tempTriIndex[1] + " " + tempTriIndex[2]);
                count++;*/
                //second triangle
                tempTriIndex[3] = tempTriIndex[1]
                tempTriIndex[4] = tempTriIndex[2] + 1
                tempTriIndex[5] = tempTriIndex[2]
                hIndices!!.put(tempTriIndex)
                /*count++;
                Log.v(TAG,"2TRI "+ count+ " " + tempTriIndex[3] + " " + tempTriIndex[4] + " " + tempTriIndex[5]);*/
                //Sticks: 2 per 2 triangles, 4 for the last couple of the row
                /* tempTriIndex Indices
                (2/5)--4
                | \    |
                |  \   |
                |   \  |
                0----(1/3)
                */
                hSticks!!.put(startPoint)
                hSticks!!.put(startPoint + 1)
                hSticks!!.put(startPoint)
                hSticks!!.put(startPoint + THREADS_PER_GROUP_X)
                if (y == THREADS_PER_GROUP_Y - 2) {
                    hSticks!!.put(startPoint + THREADS_PER_GROUP_X + 1)
                    hSticks!!.put(startPoint + THREADS_PER_GROUP_X)
                }
                if (x == THREADS_PER_GROUP_X - 2) {
                    hSticks!!.put(startPoint + 1)
                    hSticks!!.put(startPoint + THREADS_PER_GROUP_X + 1)
                }
            }
        }

        hIndices!!.position(0)
        hVPosOld!!.position(0)
        hVPos!!.position(0)
        hSticks!!.position(0)

        /*for(int i=0; i<(hSticks.capacity()-1); i=i+2)
            Log.v(TAG, hSticks.get(i) + " " +hSticks.get(i+1));*/


        /*for(int i=0; i<(hSticks.capacity()-1); i=i+2)
            Log.v(TAG, hSticks.get(i) + " " +hSticks.get(i+1));*/countFaces = hIndices!!.capacity()


    }

    override fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        super.setContextAndSurface(context, surface)
        this.surface!!.setOnClickListener {
            Log.v(BasicRenderer.TAG, "Resubmitting data to reset the cloth simulation")
            resubmit = true
        }
        renderTimeDisplayTexT = TextView(getContextBasic())
        renderTimeDisplayTexT!!.setText("PROVA")
        val params = FrameLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.BOTTOM
        (getContextBasic() as Activity).addContentView(renderTimeDisplayTexT, params)
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        super.onSurfaceChanged(gl10, w, h)
        val aspect = w.toFloat() / (if (h == 0) 1 else h).toFloat()
        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f)
        Matrix.setLookAtM(viewM, 0, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f)
        if (shaderHandle != 0) {
            GLES20.glUseProgram(shaderHandle)
            Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0)
            Matrix.setIdentityM(modelM, 0)
            Matrix.translateM(modelM, 0, 0f, 0f, 6.75f)
            Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0)
            GLES20.glUniformMatrix4fv(uMVPloc, 1, false, MVP, 0)
            GLES20.glUseProgram(0)
        }
    }

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        val vertexSrc = """
             #version 300 es
             
             layout(location = 1) in vec4 vPos;
             uniform mat4 MVP;
             out float isFixed;
             
             void main(){
             isFixed = vPos.w;
             gl_Position = MVP * vec4(vPos.xyz,1);
             }
             """.trimIndent()
        val fragmentSrc = """
             #version 300 es
             
             precision mediump float;
             in float isFixed;
             uniform vec3 color;
             out vec4 fragColor;
             
             void main() {
             if(isFixed<0.0)
             fragColor = vec4(color,1);
             else fragColor = vec4(1,0,0,1);
             }
             """.trimIndent()
        val computeSrcPosition = """
             #version 310 es
             
             layout(binding = 1) buffer oldPosStruct{
             vec4 elements[];
             }oldPos;
             layout(binding = 2) buffer posStruct{
             vec4 elements[];
             }pos;
             uniform int N;
             uniform int countFrame;
             const float gravity = -0.009;
             
             layout( local_size_x = ${THREADS_PER_GROUP_X}, local_size_y = 1, local_size_z = 1 ) in;
             void main(){
             int gid = int(gl_GlobalInvocationID.x);
             if(countFrame>250 && gid==(${THREADS_PER_GROUP_Y * THREADS_PER_GROUP_X - 1}))
             pos.elements[gid].w=-1.0;
             if(gid >= N || pos.elements[gid].w>0.0) return;
             vec3 velocity = pos.elements[gid].xyz - oldPos.elements[gid].xyz;
             oldPos.elements[gid] = pos.elements[gid];
             pos.elements[gid] = vec4(pos.elements[gid].xyz+velocity,pos.elements[gid].w);
             pos.elements[gid].y += gravity;
             }
             """.trimIndent()
        val computeSrcSticks = """#version 310 es

layout(binding = 2) buffer posStruct{
vec4 elements[];
}pos;
layout(binding = 3) readonly buffer stickStruct{
ivec2 elements[];
}sticks;
uniform int N;
uniform int switchSide;
const float stickLen = ${CLOTH_HEIGHT / THREADS_PER_GROUP_X.toFloat()};

layout( local_size_x = ${THREADS_PER_GROUP_X}, local_size_y = 1, local_size_z = 1 ) in;
void main(){
int gid = int(gl_GlobalInvocationID.x);
if(gid >= N || ((gid%2)!=switchSide)) return;
int indexA = sticks.elements[gid].x;
int indexB = sticks.elements[gid].y;
vec3 delta = vec3(pos.elements[indexB].xyz-pos.elements[indexA].xyz);
float currStickLen = length(delta);
float differ = (stickLen - currStickLen);
float perc = differ / currStickLen / 2.0;
vec3 offset = vec3(delta.x*perc,delta.y*perc, delta.z*perc);
if(length(offset)<0.0001f) return;
if(pos.elements[indexA].w < 0.0)
pos.elements[indexA] = vec4(pos.elements[indexA].xyz-offset,pos.elements[indexA].w);
if(pos.elements[indexB].w < 0.0)
   pos.elements[indexB] = vec4(pos.elements[indexB].xyz+offset,pos.elements[indexB].w);
}"""
        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc)
        computePositionShader = ShaderCompiler.createComputeProgram(computeSrcPosition)
        computeStickShader = ShaderCompiler.createComputeProgram(computeSrcSticks)
        VAO = IntArray(1) //for drawing
        VBO = IntArray(4) //0: old pos, 1: vpos, 2: sticks, 3: indices
        GLES20.glGenBuffers(4, VBO, 0)
        GLES30.glGenVertexArrays(1, VAO, 0)

        //for drawing
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, VBO[1]) //vpos: that's what we draw
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            java.lang.Float.BYTES * hVPos!!.capacity(),
            hVPos,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glVertexAttribPointer(
            1,
            4,
            GLES20.GL_FLOAT,
            false,
            4 * java.lang.Float.BYTES,
            0
        ) //vpos
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, VBO[3]) //indices
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * hIndices!!.capacity(), hIndices,
            GLES20.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)

        //for computing Verlet integration
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[0]) //old vpos
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            java.lang.Float.BYTES * hVPosOld!!.capacity(),
            hVPosOld,
            GLES20.GL_STATIC_DRAW
        )
        //we already sent vpos at position 1 earlier.
        //...
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2]) //sticks
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER, Integer.BYTES * hSticks!!.capacity(), hSticks,
            GLES20.GL_STATIC_DRAW
        )

        //all necessary data is allocated device-side and sent.

        //error check:
        Log.v(BasicRenderer.TAG, GLES20.glGetError().toString() + " after buffers movement error")
        uMVPloc = GLES20.glGetUniformLocation(shaderHandle, "MVP")
        uColor = GLES20.glGetUniformLocation(shaderHandle, "color")
        uNPosition = GLES20.glGetUniformLocation(computePositionShader, "N")
        uNSticks = GLES20.glGetUniformLocation(computeStickShader, "N")
        uSwitch = GLES20.glGetUniformLocation(computeStickShader, "switchSide")
        uFrameCount = GLES20.glGetUniformLocation(computePositionShader, "countFrame")
        GLES20.glUseProgram(computePositionShader)
        GLES20.glUniform1i(
            uNPosition,
            THREADS_PER_GROUP_X * THREADS_PER_GROUP_Y
        )
        GLES20.glUseProgram(0)
        GLES20.glUseProgram(computeStickShader)
        GLES20.glUniform1i(uNSticks, NUM_STICKS / 2)
        GLES20.glUseProgram(0)
        GLES20.glLineWidth(3.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
    }
    override fun onDrawFrame(gl10: GL10?) {
        deltaBetweenFrames = (System.nanoTime() - startTime).toFloat()
        deltaBetweenFrames /= 1000f * 1000f
        startTime = System.nanoTime()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        if (resubmit) {
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[1])
            GLES20.glBufferData(
                GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hVPos!!.capacity(),
                hVPos, GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[0])
            GLES20.glBufferData(
                GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hVPosOld!!.capacity(),
                hVPosOld, GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2])
            GLES20.glBufferData(
                GLES31.GL_SHADER_STORAGE_BUFFER, Integer.BYTES * hSticks!!.capacity(),
                hSticks, GLES20.GL_STATIC_DRAW
            )
            resubmit = false
            countFrame = 0
        }
        countFrame++

        //draw
        GLES20.glUseProgram(shaderHandle)
        GLES30.glBindVertexArray(VAO[0])
        GLES20.glUniform3f(uColor, 118.0f / 255.0f, 185.0f / 255f, 0f)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, countFaces, GLES20.GL_UNSIGNED_INT, 0) //solid
        GLES20.glUniform3f(uColor, 0f, 0f, 0f)
        GLES20.glDrawElements(GLES20.GL_LINES, countFaces, GLES20.GL_UNSIGNED_INT, 0) //wireframe
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
        if (countFrame % 1 == 0) {
            //compute
            GLES20.glUseProgram(computePositionShader)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[0]) //bind oldPos
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[1]) //bind vPos
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, VBO[2]) //bind sticks
            GLES20.glUniform1i(uFrameCount, countFrame)
            GLES31.glDispatchCompute(
                THREADS_PER_GROUP_X * THREADS_PER_GROUP_Y / THREADS_PER_GROUP_X,
                1,
                1
            )
            //glUseProgram(0);
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
            if (countFrame > 250) countFrame = 0
            GLES20.glUseProgram(computeStickShader)
            for (i in 0..499) {
                GLES20.glUniform1i(uSwitch, i % 2)
                GLES31.glDispatchCompute(
                    Math.ceil(
                        (NUM_STICKS / 2).toDouble() / THREADS_PER_GROUP_X.toDouble()
                    ).toInt(), 1, 1
                )
            }
            //glUseProgram(0);
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        }

/*
        glFlush();
        glFinish();
*/endTime = System.nanoTime()

        //renderTimeDisplayTexT.setText("Render time " + (end-start)); //won't work
        if (countFrame % 5 == 0) {
            (getContextBasic() as Activity).runOnUiThread {
                var s =
                    "" + (endTime - startTime).toFloat() / (1000f * 1000f) + " ms"
                s += " delta $deltaBetweenFrames ms"
                renderTimeDisplayTexT!!.text = s
            }
        }
    }

}