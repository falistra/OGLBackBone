package ogles.oglbackbone;

import android.opengl.GLES30;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
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
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class VBOVAORenderer  extends BasicRenderer {

    private int VAO[];
    private int VBO[];
    private int shaderHandle;
    private int attrPos;
    private int attrColor;
    private static final boolean USE_VAO = true;

    public VBOVAORenderer(){
        super();
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);
        InputStream isV = null;
        InputStream isF = null;

        try {
            isV = context.getAssets().open("passthrough.glslv");
            isF = context.getAssets().open("passthrough.glslf");
            shaderHandle = ShaderCompiler.createProgram(isV,isF);
        }catch(IOException e){
            e.printStackTrace();
            System.exit(-1);
        }

        if(shaderHandle==-1)
            System.exit(-1);

        float vertices[] = new float[]{
                -1f, -1f,
                1f, -1f,
                0.f, 1f};

        float colors[] = new float[]{
            1,0,0,
            0,1,0,
            0,0,1
        };

        FloatBuffer vertexData = ByteBuffer.allocateDirect(vertices.length * Float.BYTES).
                order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        vertexData.put(vertices);
        vertexData.position(0);

        FloatBuffer colorData = ByteBuffer.allocateDirect(colors.length*Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        colorData.put(colors);
        colorData.position(0);

        attrPos = glGetAttribLocation(shaderHandle, "vPos");
        attrColor = glGetAttribLocation(shaderHandle, "aColor");

        VBO = new int[2];
        glGenBuffers(2, VBO, 0);

        if(USE_VAO) {
            Log.v(TAG,"Using VAO");
            VAO = new int[1];
            GLES30.glGenVertexArrays(1, VAO, 0);

            GLES30.glBindVertexArray(VAO[0]);
            glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                    vertexData, GL_STATIC_DRAW);
            glVertexAttribPointer(attrPos, 2, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(attrPos);
            glBindBuffer(GL_ARRAY_BUFFER, VBO[1]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * colorData.capacity(),
                    colorData, GL_STATIC_DRAW);
            glVertexAttribPointer(attrColor,3,GL_FLOAT,false,0,0);
            glEnableVertexAttribArray(attrColor);

            glBindBuffer(GL_ARRAY_BUFFER,0);
            GLES30.glBindVertexArray(0);
        }
        else{
            Log.v(TAG,"Using VBOs");
            glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                    vertexData, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, VBO[1]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * colorData.capacity(),
                    colorData, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER,0);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT);

        if(USE_VAO) {
            glUseProgram(shaderHandle);
            GLES30.glBindVertexArray(VAO[0]);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            GLES30.glBindVertexArray(0);
            glUseProgram(0);
        }else{
            glUseProgram(shaderHandle); //bind to the program (shaders)
                glBindBuffer(GL_ARRAY_BUFFER,VBO[0]); //bind the VBO (positions)
                    glVertexAttribPointer(attrPos, 2, GL_FLOAT, false, 0, 0);
                    glEnableVertexAttribArray(attrPos); //enable attributes
                glBindBuffer(GL_ARRAY_BUFFER,VBO[1]); //bind the VBO (colors)
                    glVertexAttribPointer(attrColor,3,GL_FLOAT,false,0,0);
                    glEnableVertexAttribArray(attrColor);
                        glDrawArrays(GL_TRIANGLES,0,3); //drawcall!
                glBindBuffer(GL_ARRAY_BUFFER,0);
            glUseProgram(0);
        }

    }

}
