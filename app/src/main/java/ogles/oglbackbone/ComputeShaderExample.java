package ogles.oglbackbone;

import android.opengl.GLES30;
import android.opengl.GLES31;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ogles.oglbackbone.utils.ShaderCompiler;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFinish;
import static android.opengl.GLES20.glFlush;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniform4f;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class ComputeShaderExample extends BasicRenderer {

    private int VBO[];
    private int shaderHandle;

    private FloatBuffer hA;
    private FloatBuffer hB;
    private FloatBuffer hC;

    static final int NUM_ELEMENTS = 1024;
    static final int THREADS_X_GROUP = 64;
    static final float EPSILON = 0.0001f;

    private int uNloc;

    public ComputeShaderExample() {
        super();

        hA = ByteBuffer.allocateDirect(NUM_ELEMENTS * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        hB = ByteBuffer.allocateDirect(NUM_ELEMENTS * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            hA.put((float) i);
            hB.put((float) NUM_ELEMENTS - i);
        }

        hA.position(0);
        hB.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String csSrc = "#version 310 es\n" +
                "\n" +
                "layout(binding = 1) readonly buffer AStruct{\n" +
                "float elements[];\n" +
                "}A;\n" +
                "layout(binding = 2) readonly buffer BStruct{\n" +
                "float elements[];\n" +
                "}B;\n" +
                "layout(binding = 3) writeonly buffer CStruct{\n" +
                "float elements[];\n" +
                "}C;\n" +
                "uniform int N;\n" +
                "\n" +
                "layout( local_size_x = " + THREADS_X_GROUP + ", local_size_y = 1, local_size_z = 1 ) in;\n" +
                "void main(){\n" +
                "int gid = int(gl_GlobalInvocationID.x);\n" +
                "if(gid<N)\n" +
                "C.elements[gid] = A.elements[gid] + B.elements[gid];\n" +
                "}";

        shaderHandle = ShaderCompiler.createComputeProgram(csSrc);

        uNloc = glGetUniformLocation(shaderHandle, "N");
        VBO = new int[3]; //A,B and C buffers (all SSBOs)

        glGenBuffers(3, VBO, 0);

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[0]);
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hA.capacity(), hA,
                GL_STATIC_DRAW);

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[1]);
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hB.capacity(), hB,
                GL_STATIC_DRAW);

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2]);
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * NUM_ELEMENTS, null,
                GL_STATIC_DRAW);

        //Log.v(TAG, glGetError() + " after buffer error");

        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);
        //glFlush();

        glUseProgram(shaderHandle);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[0]);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[1]);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, VBO[2]);
        glUniform1i(uNloc, NUM_ELEMENTS);
        GLES31.glDispatchCompute(NUM_ELEMENTS / THREADS_X_GROUP, 1, 1);
        glUseProgram(0);

        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[2]);
        ByteBuffer out = (ByteBuffer)
                GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0, Float.BYTES * NUM_ELEMENTS, GLES31.GL_MAP_READ_BIT);
        GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER);

        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

        out.order(ByteOrder.nativeOrder());
        hC = out.asFloatBuffer();
        hC.position(0);

        //Log.v(TAG, "out pos and size " + out.capacity() + " " + out.position());
        Log.v(TAG, "Example " + hA.get(100) + " + " + hB.get(100) + " = " + hC.get(100));

        boolean passed = true;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            if (!((hA.get(i) + hB.get(i)) <= (hC.get(i) + EPSILON) &&
                    (hA.get(i) + hB.get(i)) >= (hC.get(i) - EPSILON))) {
                passed = false;
                Log.e(TAG, "MISMATCH at index " + i + " : " +
                        hA.get(i) + " + " + hB.get(i) + " = " + hC.get(i));
                break;
            }

        }

        Log.v(TAG,"Test is " + (passed ? "passed":"NOT passed."));

    }


    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT);

    }

}
