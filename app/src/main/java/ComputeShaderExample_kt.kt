package ogles.oglbackbone

import android.opengl.GLES20
import android.opengl.GLES31
import android.util.Log
import ogles.oglbackbone.utils.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ComputeShaderExample_kt  : BasicRenderer_kt()  {
    private lateinit var VBO: IntArray
    private var shaderHandle = 0

    private var hA: FloatBuffer? = null
    private var hB: FloatBuffer? = null
    private var hC: FloatBuffer? = null

    val NUM_ELEMENTS = 1024
    val THREADS_X_GROUP = 64
    val EPSILON = 0.0001f

    private var uNloc = 0

    init {
        hA = ByteBuffer.allocateDirect(NUM_ELEMENTS * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        hB = ByteBuffer.allocateDirect(NUM_ELEMENTS * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (i in 0 until NUM_ELEMENTS) {
            hA!!.put(i.toFloat())
            hB!!.put(NUM_ELEMENTS.toFloat() - i)
        }

        hA!!.position(0)
        hB!!.position(0)

    }

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)

        val csSrc = """
            #version 310 es
            
            layout(binding = 1) readonly buffer AStruct{
            float elements[];
            }A;
            layout(binding = 2) readonly buffer BStruct{
            float elements[];
            }B;
            layout(binding = 3) writeonly buffer CStruct{
            float elements[];
            }C;
            uniform int N;
            
            layout( local_size_x = ${THREADS_X_GROUP}, local_size_y = 1, local_size_z = 1 ) in;
            void main(){
            int gid = int(gl_GlobalInvocationID.x);
            if(gid<N)
            C.elements[gid] = A.elements[gid] + B.elements[gid];
            }
            """.trimIndent()

        shaderHandle = ShaderCompiler.createComputeProgram(csSrc)

        uNloc = GLES20.glGetUniformLocation(shaderHandle, "N")
        VBO = IntArray(3) //A,B and C buffers (all SSBOs)


        GLES20.glGenBuffers(3, VBO, 0)

        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[0])
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hA!!.capacity(), hA,
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[1])
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hB!!.capacity(), hB,
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2])
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            java.lang.Float.BYTES * NUM_ELEMENTS,
            null,
            GLES20.GL_STATIC_DRAW
        )

        //Log.v(TAG, glGetError() + " after buffer error");


        //Log.v(TAG, glGetError() + " after buffer error");
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        //glFlush();

        //glFlush();
        GLES20.glUseProgram(shaderHandle)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[0])
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[1])
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, VBO[2])
        GLES20.glUniform1i(uNloc, NUM_ELEMENTS)
        GLES31.glDispatchCompute(
            NUM_ELEMENTS / THREADS_X_GROUP,
            1,
            1
        )
        GLES20.glUseProgram(0)

        /*
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)

        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2])
        val out = GLES31.glMapBufferRange(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            0,
            java.lang.Float.BYTES * NUM_ELEMENTS,
            GLES31.GL_MAP_READ_BIT
        ) as ByteBuffer
        GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)

        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)

        out.order(ByteOrder.nativeOrder())
        hC = out.asFloatBuffer()
        hC!!.position(0)

        //Log.v(TAG, "out pos and size " + out.capacity() + " " + out.position());

        //Log.v(TAG, "out pos and size " + out.capacity() + " " + out.position());
        Log.v(BasicRenderer.TAG, "Example " + hA!![100] + " + " + hB!![100] + " = " + hC!!.get(100))

        var passed = true
        for (i in 0 until NUM_ELEMENTS) {
            if (!(hA!![i] + hB!![i] <= hC!!.get(i) + EPSILON &&
                        hA!![i] + hB!![i] >= hC!!.get(i) - EPSILON)
            ) {
                passed = false
                Log.e(
                    BasicRenderer.TAG, "MISMATCH at index " + i + " : " +
                            hA!![i] + " + " + hB!![i] + " = " + hC!!.get(i)
                )
                break
            }
        }

        Log.v(BasicRenderer.TAG, "Test is " + if (passed) "passed" else "NOT passed.")
        */

    }


    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }


}