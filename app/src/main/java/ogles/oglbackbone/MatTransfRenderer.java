package ogles.oglbackbone;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ogles.oglbackbone.utils.ShaderCompiler;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_BACK;
import static android.opengl.GLES20.GL_CCW;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_INT;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glDisable;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFrontFace;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class MatTransfRenderer extends BasicRenderer {

    private int VAO[];
    private int shaderHandle;
    private int MVPloc;

    private float viewM[];
    private float modelM[];
    private float projM[];
    private float MVP[];
    private float temp[];

    private float angle;

    private boolean cullingChanged;
    private boolean culling;

    public MatTransfRenderer() {
        super();
        culling = false;
        cullingChanged = false;
        viewM = new float[16];
        modelM = new float[16];
        projM = new float[16];
        MVP = new float[16];
        temp = new float[16];
        Matrix.setIdentityM(viewM, 0);
        Matrix.setIdentityM(modelM, 0);
        Matrix.setIdentityM(projM, 0);
        Matrix.setIdentityM(MVP, 0);
    }

    @Override
    public void setContextAndSurface(Context context, GLSurfaceView surface) {
        super.setContextAndSurface(context, surface);

        this.surface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                culling = !culling;
                cullingChanged = true;
                Log.v(TAG, "Culling set to " +  culling );
            }
        });

    }


    @Override
    public void onSurfaceChanged(GL10 gl10, int w, int h) {
        super.onSurfaceChanged(gl10, w, h);
        float aspect = ((float) w) / ((float) (h == 0 ? 1 : h));

        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f);

        Matrix.setLookAtM(viewM, 0, 0, 0f, 4f,
                0, 0, 0,
                0, 1, 0);

    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String vertexSrc = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec2 vPos;\n" +
                "layout(location = 2) in vec3 color;\n" +
                "uniform mat4 MVP;\n" +
                "out vec3 colorVarying; \n" +
                "\n" +
                "void main(){\n" +
                "colorVarying = color;\n" +
                "gl_Position = MVP * vec4(vPos,0,1);\n" +
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

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);

        VAO = new int[1]; //one VAO to bind both vpos and color

        //--1--|--2---|
        //vx,vy,r,g,b
        float verticesAndColors[] = new float[]{
                -1f, -1f, 1f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                1f, 1f, 0f, 0f, 1f,
                -1f, 1f, 1f, 0f, 1f};

        int indices[] = new int[]{
                0, 1, 2, //first triangle
                0, 2, 3 //second triangle (fixed winding order)
        };

        FloatBuffer vertexData =
                ByteBuffer.allocateDirect(verticesAndColors.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        vertexData.put(verticesAndColors);
        vertexData.position(0);

        IntBuffer indexData =
                ByteBuffer.allocateDirect(indices.length * Integer.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer();
        indexData.put(indices);
        indexData.position(0);

        int VBO[] = new int[2]; //0: vpos/color, 1: indices

        glGenBuffers(2, VBO, 0);

        GLES30.glGenVertexArrays(1, VAO, 0);

        GLES30.glBindVertexArray(VAO[0]);
        glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                vertexData, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 0); //vpos
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 5 * Float.BYTES, 2 * Float.BYTES); //color
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBO[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        MVPloc = glGetUniformLocation(shaderHandle, "MVP");

        glCullFace(GL_BACK); //which face to cull? back,front or both (Still points and lines visible)
        glFrontFace(GL_CCW); //winding order that defines front and back

    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT);

        if(cullingChanged) {
            if(culling)
                glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);

            cullingChanged=false;

        }

        angle += 1f;

        Matrix.setIdentityM(modelM, 0);
        Matrix.translateM(modelM, 0, 0, 0f, -1);
        Matrix.rotateM(modelM, 0, angle, 0, 1, 0);
        Matrix.scaleM(modelM, 0, 0.5f, 1f, 0.5f);

        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);
        glUniformMatrix4fv(MVPloc, 1, false, MVP, 0);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0); //num of faces, not vertices!
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }

}
