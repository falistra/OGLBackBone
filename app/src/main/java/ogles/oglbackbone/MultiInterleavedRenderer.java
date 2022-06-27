package ogles.oglbackbone;

import android.opengl.GLES30;

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
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class MultiInterleavedRenderer extends BasicRenderer {

    private int VAO[];
    private int shaderHandle;
    private static final boolean INTERLEAVED = true;

    public MultiInterleavedRenderer(){
        super();
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String vertexSrc = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec2 vPos;\n" +
                "layout(location = 2) in vec3 color;\n" +
                "out vec3 colorVarying; \n" +
                "\n" +
                "void main(){\n" +
                "colorVarying = color;\n" +
                "gl_Position = vec4(vPos,0,1);\n" +
                "}";

        String fragmentSrc = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "in vec3 colorVarying;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "fragColor = vec4(colorVarying,1);\n" +
                "}\n";

        shaderHandle = ShaderCompiler.createProgram(vertexSrc,fragmentSrc);

        VAO = new int[1]; //one VAO to bind both vpos and color

        if(INTERLEAVED) {

            //--1--|--2---|
            //vx,vy,r,g,b
            float verticesAndColors[] = new float[]{
                    -1f, -1f, 1f,0f,0f,
                    1f, -1f,  0f,1f,0f,
                    0.f, 1f,  0f,0f,1f};

            FloatBuffer vertexData =
                    ByteBuffer.allocateDirect(verticesAndColors.length * Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexData.put(verticesAndColors);
            vertexData.position(0);

            int VBO[] = new int[1]; //0: vpos/color

            glGenBuffers(1, VBO, 0);

            GLES30.glGenVertexArrays(1, VAO, 0);
            GLES30.glBindVertexArray(VAO[0]);
            glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                    vertexData, GL_STATIC_DRAW);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 0); //vpos
            glVertexAttribPointer(2, 3, GL_FLOAT, false, 5 * Float.BYTES, 2 * Float.BYTES); //color
            glEnableVertexAttribArray(1);
            glEnableVertexAttribArray(2);
            GLES30.glBindVertexArray(0);
        }
        else{

            // first all the vertices pos, then all the colors...
            float verticesAndColors[] = new float[]{
                    -1f, -1f,
                    1f, -1f,
                    0.f, 1f,
                    1,0,0,
                    0,1,0,
                    0,0,1
            };

            FloatBuffer vertexData =
                    ByteBuffer.allocateDirect(6 * Float.BYTES)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer();

            for(int i=0; i<6; i++)
                vertexData.put(verticesAndColors[i]);

            vertexData.position(0);

            FloatBuffer colorData =
                    ByteBuffer.allocateDirect(9 * Float.BYTES)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer();

            for(int i=6; i<verticesAndColors.length; i++)
                colorData.put(verticesAndColors[i]);

            colorData.position(0);

            int VBO[] = new int[2]; //0: vpos, 1: color

            GLES30.glGenVertexArrays(1, VAO, 0);
            glGenBuffers(2, VBO, 0);

            GLES30.glBindVertexArray(VAO[0]);

            glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                    vertexData, GL_STATIC_DRAW);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0); //vpos
            glEnableVertexAttribArray(1);

            glBindBuffer(GL_ARRAY_BUFFER, VBO[1]);
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * colorData.capacity(),
                    colorData, GL_STATIC_DRAW);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0); //color
            glEnableVertexAttribArray(2);

            GLES30.glBindVertexArray(0);
        }


    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }

}
