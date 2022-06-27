package ogles.oglbackbone

import android.opengl.GLES20
import android.opengl.GLES31
import android.util.Log
import ogles.oglbackbone.nnet.MLPNet
import ogles.oglbackbone.nnet.NNetParser
import ogles.oglbackbone.utils.ShaderCompiler
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ComputeShaderNN_kt : BasicRenderer_kt()  {
    private var mlp: MLPNet? = null

    private lateinit var VBO: IntArray
    private var shaderHandle = 0

    private var hIn: FloatBuffer? = null
    private var hOut: FloatBuffer? = null
    private var hWeights: FloatBuffer? = null

    private var uNInput = 0
    private var uNThreads = 0
    private var uStride = 0
    private var uOffsetW = 0

    val THREADS_X_GROUP = 64

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        var `is`: InputStream? = null
        try {
            //  is = context.getResources().openRawResource(R.raw.xor);
            `is` = context!!.resources.openRawResource(R.raw.b2d)
            mlp = NNetParser.parseNet(`is`)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        hIn = ByteBuffer.allocateDirect(mlp!!.largestLayer * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        hWeights = ByteBuffer.allocateDirect(mlp!!.totalWeightSize * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        hIn!!.put(1f)
        hIn!!.put(1f) //setting inputs
        hIn!!.position(0)

        //copying weights
        for (i in mlp!!.layers.indices) {
            val l = mlp!!.layers[i]
            for (j in l.weights.indices) {
                hWeights!!.put(l.weights[j])
                //Log.v(TAG,""+l.weights[j]);
            }
        }
        hWeights!!.position(0)
        val csSrc = """
             #version 310 es
             
             layout(binding = 1) buffer INStruct{
             float elements[];
             }bin;
             layout(binding = 2) buffer OUTStruct{
             float elements[];
             }bout;
             layout(binding = 3) readonly buffer WStruct{
             float elements[];
             }W;
             uniform int NInput;
             uniform int NThreads;
             uniform int stride;
             uniform int offsetW;
             
             layout( local_size_x = ${ComputeShaderNN.THREADS_X_GROUP}, local_size_y = 1, local_size_z = 1 ) in;
             void main(){
             int gid = int(gl_GlobalInvocationID.x);
             if(gid>=NThreads) return;
             float sum=0.0;
             for(int r=0; r<NInput; r++){
             sum += (bin.elements[r]*W.elements[offsetW+gid+r*stride]);
             }
             bout.elements[gid] = 1.0/(1.0+exp(-1.0*sum));
             }
             """.trimIndent()
        shaderHandle = ShaderCompiler.createComputeProgram(csSrc)
        uNInput = GLES20.glGetUniformLocation(shaderHandle, "NInput")
        uNThreads = GLES20.glGetUniformLocation(shaderHandle, "NThreads")
        uStride = GLES20.glGetUniformLocation(shaderHandle, "stride")
        uOffsetW = GLES20.glGetUniformLocation(shaderHandle, "offsetW")
        var lastOffset = 0
        val launches = mlp!!.layers.size
        var lastOutputVBOIndex = 0
        VBO = IntArray(3)
        GLES20.glGenBuffers(3, VBO, 0) //0: in, 1: out, 2: weights
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[0])
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hIn!!.capacity(), hIn,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[1])
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hIn!!.capacity(), null,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2])
        GLES20.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER, java.lang.Float.BYTES * hWeights!!.capacity(), hWeights,
            GLES20.GL_STATIC_DRAW
        )
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        GLES20.glUseProgram(shaderHandle)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, VBO[2]) //binding Weights
        for (i in 0 until launches) {

            //Switching IN/OUT logic
            lastOutputVBOIndex = if (i % 2 == 0) {
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[0])
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[1])
                1
            } else {
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[0])
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[1])
                0
            }
            GLES20.glUniform1i(uNInput, mlp!!.layers[i].input)
            GLES20.glUniform1i(uNThreads, mlp!!.layers[i].output)
            GLES20.glUniform1i(uStride, mlp!!.layers[i].output)
            val offset =
                lastOffset + if (i == 0) 0 else mlp!!.layers[i - 1].input * mlp!!.layers[i - 1].output
            lastOffset = offset
            GLES20.glUniform1i(uOffsetW, offset)
            GLES31.glDispatchCompute(
                Math.ceil(mlp!!.layers[i].output.toDouble() / ComputeShaderNN.THREADS_X_GROUP.toDouble())
                    .toInt(),
                1, 1
            )
            Log.v(BasicRenderer.TAG, GLES20.glGetError().toString() + " after buffer error")
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        }
        GLES20.glUseProgram(0)

        //read back result. Who was the last buffer to be written? VBO[lastOutputVBOIndex]...
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[lastOutputVBOIndex])
        val out = GLES31.glMapBufferRange(
            GLES31.GL_SHADER_STORAGE_BUFFER, 0,
            java.lang.Float.BYTES * hIn!!.capacity(),
            GLES31.GL_MAP_READ_BIT
        ) as ByteBuffer
        GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        out.order(ByteOrder.nativeOrder())
        hOut = out.asFloatBuffer()
        hOut!!.position(0)

        //print output:
        Log.v(BasicRenderer.TAG, "Printing output ")
        for (i in 0 until mlp!!.layers[mlp!!.layers.size - 1].output) {
            Log.v(BasicRenderer.TAG, "[" + (mlp!!.layers.size - i) + "] -> " + hOut!!.get(i))
        }
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
/*
        greenComponent += 0.01f;
        if(greenComponent>=1f)
            greenComponent = 0f;

        if(USE_VAO) {
            glUseProgram(shaderHandle);
            GLES30.glBindVertexArray(VAO[0]);
            glUniform4f(colorUni, 0.56f, greenComponent, 0.1f, 1.0f);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            GLES30.glBindVertexArray(0);
            glUseProgram(0);
        }else{
            glUseProgram(shaderHandle);
            glBindBuffer(GL_ARRAY_BUFFER,VBO[0]);
            glEnableVertexAttribArray(attrPos);
            glVertexAttribPointer(attrPos, 2, GL_FLOAT, false, 0, 0);
            glUniform4f(colorUni, 0.56f, greenComponent, 0.1f, 1.0f);
            glDrawArrays(GL_TRIANGLES,0,3);
            glDisableVertexAttribArray(attrPos);
            glBindBuffer(GL_ARRAY_BUFFER,0);
            glUseProgram(0);
        }
*/
    }

}