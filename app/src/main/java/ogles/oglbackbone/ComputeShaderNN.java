package ogles.oglbackbone;

import android.opengl.GLES31;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ogles.oglbackbone.nnet.MLPNet;
import ogles.oglbackbone.nnet.NLayer;
import ogles.oglbackbone.nnet.NNetParser;
import ogles.oglbackbone.utils.ShaderCompiler;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUseProgram;

public class ComputeShaderNN extends BasicRenderer {

    private MLPNet mlp;

    private int VBO[];
    private int shaderHandle;

    private FloatBuffer hIn;
    private FloatBuffer hOut;
    private FloatBuffer hWeights;

    private int uNInput;
    private int uNThreads;
    private int uStride;
    private int uOffsetW;

    static final int THREADS_X_GROUP = 64;

    public ComputeShaderNN() {
        super();
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        InputStream is = null;

        try {
          //  is = context.getResources().openRawResource(R.raw.xor);
            is = context.getResources().openRawResource(R.raw.b2d);
            mlp = NNetParser.parseNet(is);
        }catch(IOException e){
            e.printStackTrace();
        }

        hIn = ByteBuffer.allocateDirect(mlp.largestLayer * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        hWeights = ByteBuffer.allocateDirect(mlp.totalWeightSize * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        hIn.put(1); hIn.put(1);  //setting inputs
        hIn.position(0);

        //copying weights
        for(int i=0; i<mlp.layers.length; i++){
            NLayer l = mlp.layers[i];
            for(int j=0; j<l.weights.length;j++){
                hWeights.put(l.weights[j]);
                //Log.v(TAG,""+l.weights[j]);
            }
        }

        hWeights.position(0);

        String csSrc = "#version 310 es\n" +
                "\n" +
                "layout(binding = 1) buffer INStruct{\n" +
                "float elements[];\n" +
                "}bin;\n" +
                "layout(binding = 2) buffer OUTStruct{\n" +
                "float elements[];\n" +
                "}bout;\n" +
                "layout(binding = 3) readonly buffer WStruct{\n" +
                "float elements[];\n" +
                "}W;\n" +
                "uniform int NInput;\n" +
                "uniform int NThreads;\n" +
                "uniform int stride;\n" +
                "uniform int offsetW;\n" +
                "\n" +
                "layout( local_size_x = " + THREADS_X_GROUP + ", local_size_y = 1, local_size_z = 1 ) in;\n" +
                "void main(){\n" +
                "int gid = int(gl_GlobalInvocationID.x);\n" +
                "if(gid>=NThreads) return;\n" +
                "float sum=0.0;\n" +
                "for(int r=0; r<NInput; r++){\n"+
                "sum += (bin.elements[r]*W.elements[offsetW+gid+r*stride]);\n"+
                "}\n"+
                "bout.elements[gid] = 1.0/(1.0+exp(-1.0*sum));\n"+
                "}";

        shaderHandle = ShaderCompiler.createComputeProgram(csSrc);

        uNInput = glGetUniformLocation(shaderHandle, "NInput");
        uNThreads = glGetUniformLocation(shaderHandle, "NThreads");
        uStride = glGetUniformLocation(shaderHandle, "stride");
        uOffsetW = glGetUniformLocation(shaderHandle, "offsetW");

        int lastOffset = 0;
        final int launches = mlp.layers.length;
        int lastOutputVBOIndex=0;

        VBO = new int[3];

        glGenBuffers(3, VBO, 0); //0: in, 1: out, 2: weights

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[0]);
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hIn.capacity(), hIn,
                GL_STATIC_DRAW);

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[1]);
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hIn.capacity(), null,
                GL_STATIC_DRAW);

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2]);
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hWeights.capacity(), hWeights,
                GL_STATIC_DRAW);

        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

        glUseProgram(shaderHandle);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, VBO[2]); //binding Weights

        for(int i=0; i<launches; i++){

            //Switching IN/OUT logic
            if((i%2)==0){
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[0]);
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[1]);
                lastOutputVBOIndex=1;
            }else{
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[0]);
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[1]);
                lastOutputVBOIndex=0;
            }

            glUniform1i(uNInput, mlp.layers[i].input);
            glUniform1i(uNThreads, mlp.layers[i].output);
            glUniform1i(uStride, mlp.layers[i].output);
            int offset = lastOffset + (i==0 ? 0 : (mlp.layers[i-1].input*mlp.layers[i-1].output));
            lastOffset = offset;
            glUniform1i(uOffsetW, offset);
            GLES31.glDispatchCompute(
                    (int)Math.ceil((double)mlp.layers[i].output / (double)THREADS_X_GROUP),
                    1, 1);

            Log.v(TAG, glGetError() + " after buffer error");

            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);
        }

        glUseProgram(0);

        //read back result. Who was the last buffer to be written? VBO[lastOutputVBOIndex]...

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[lastOutputVBOIndex]);
        ByteBuffer out = (ByteBuffer)
                GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0,
                        Float.BYTES * hIn.capacity(),
                        GLES31.GL_MAP_READ_BIT);
        GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER);

        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

        out.order(ByteOrder.nativeOrder());
        hOut = out.asFloatBuffer();
        hOut.position(0);

        //print output:
        Log.v(TAG, "Printing output ");
        for(int i=0; i<mlp.layers[mlp.layers.length-1].output; i++){
            Log.v(TAG,"["+(mlp.layers.length-i)+"] -> " + hOut.get(i));
        }

    }


    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT);
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
