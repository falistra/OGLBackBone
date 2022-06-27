package ogles.oglbackbone;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ogles.oglbackbone.utils.PlyObject;
import ogles.oglbackbone.utils.ShaderCompiler;

import static android.opengl.GLES10.GL_CCW;
import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_BACK;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LEQUAL;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_INT;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glDepthFunc;
import static android.opengl.GLES20.glDisable;
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFrontFace;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniform3fv;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class ToonShadingRenderer extends BasicRenderer {

    private int VAO[];
    private int shaderHandle;
    private int MVPloc;
    private int umodelM;

    private float viewM[];
    private float modelM[];
    private float projM[];
    private float MVP[];
    private float temp[];
    private float inverseModel[];
    private int uInverseModel;

    private int countFacesToElement;
    private float angle;

    private float[] lightPos;
    private int uLightPos;

    private float[] eyePos;

    private int uDrawContour;
    private boolean drawContour;

    private static String TAG;

    public ToonShadingRenderer() {
        super(1,1,1,1);
        TAG = getClass().getSimpleName();

        drawContour = true;
        lightPos = new float[]{-0.25f,0.25f,10};
        eyePos = new float[]{0f,0f,10f};
        viewM = new float[16];
        modelM = new float[16];
        projM = new float[16];
        MVP = new float[16];
        temp = new float[16];
        inverseModel = new float[16];
        Matrix.setIdentityM(inverseModel,0);
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
                drawContour = !drawContour;
            }
        });

    }


    @Override
    public void onSurfaceChanged(GL10 gl10, int w, int h) {
        super.onSurfaceChanged(gl10, w, h);
        float aspect = ((float) w) / ((float) (h == 0 ? 1 : h));

        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f);

        Matrix.setLookAtM(viewM, 0, eyePos[0], eyePos[1], eyePos[2],
                0, 0, 0,
                0, 1, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String vertexSrc = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec3 vPos;\n" +
                "layout(location = 2) in vec3 normal;\n" +
                "uniform mat4 MVP;\n" +
                "uniform mat4 modelMatrix;\n" +
                "uniform mat4 inverseModel;\n" +
                "uniform int drawContour;\n"+
                "out vec3 fragModel;\n" +
                "out vec3 transfNormal;\n" +
                "\n" +
                "void main(){\n" +
                "float scaling = 1.1;\n"+
                "if(drawContour==0){\n"+
                "transfNormal = normalize(vec3(inverseModel * vec4(normal,1)));\n" +
                "fragModel = vec3(modelMatrix * vec4(vPos,1));\n" +
                "scaling = 1.0;\n"+
                "}\n"+
                "gl_Position = MVP * vec4(vPos*scaling,1);\n" +
                "}";

        String fragmentSrc = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform vec3 lightPos;\n" +
                "uniform int drawContour;\n"+
                "in vec3 fragModel;\n" +
                "in vec3 transfNormal;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "if(drawContour==1){\n"+
                "fragColor=vec4(0,0,0,1);\n" +
                "return;\n"+
                "}\n"+
                "vec3 lightDir = normalize(lightPos-fragModel);\n"+
                "float diff = max(dot(lightDir,transfNormal),0.0);\n"+
                "if(diff>0.95) fragColor = vec4(0.85,0.95,0.25,1);\n"+
                "else if (diff>0.5) fragColor = vec4(0.425,0.474,0.125,1);\n"+
                "else if (diff>0.25) fragColor = vec4(0.2,0.2,0.075,1);\n"+
                "else fragColor = vec4(0.1,0.1,0,1);\n" +
                "}";

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);

        InputStream is;
        float[] vertices=null;
        int[] indices=null;

        try {
            is = context.getAssets().open("ohioteapot.ply");
            PlyObject po = new PlyObject(is);
            po.parse();
            vertices = po.getVertices();
            indices = po.getIndices();

        }catch(IOException | NumberFormatException e){
            e.printStackTrace();
        }

        VAO = new int[1]; //one VAO to bind both vpos and color

        FloatBuffer vertexData =
                ByteBuffer.allocateDirect(vertices.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        vertexData.put(vertices);
        vertexData.position(0);

        IntBuffer indexData =
                ByteBuffer.allocateDirect(indices.length * Integer.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer();
        indexData.put(indices);
        indexData.position(0);

        countFacesToElement = indices.length;

        int VBO[] = new int[2]; //0: vpos, 1: indices

        glGenBuffers(2, VBO, 0);

        GLES30.glGenVertexArrays(1, VAO, 0);

        GLES30.glBindVertexArray(VAO[0]);
        glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                vertexData, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6*Float.BYTES, 0); //vpos
        glVertexAttribPointer(2,3,GL_FLOAT, false, 6*Float.BYTES, 3*Float.BYTES); //color/normal
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBO[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        MVPloc = glGetUniformLocation(shaderHandle, "MVP");
        umodelM = glGetUniformLocation(shaderHandle, "modelMatrix");
        uInverseModel = glGetUniformLocation(shaderHandle,"inverseModel");
        uLightPos = glGetUniformLocation(shaderHandle,"lightPos");
        uDrawContour = glGetUniformLocation(shaderHandle,"drawContour");

        //pre load uniform values:

        glUseProgram(shaderHandle);
        glUniform3fv(uLightPos,1,lightPos,0);
        glUseProgram(0);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        angle += 1f;

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);

        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);

        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,0,8);
        Matrix.rotateM(modelM,0,angle,0.2f,1f,0.4f);

        //calculate and send MVP
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);
        glUniformMatrix4fv(MVPloc, 1, false, MVP, 0);

        //draw contours
        if(drawContour) {
            glDisable(GL_DEPTH_TEST);
            glUniform1i(uDrawContour, 1);
            glDrawElements(GL_TRIANGLES, countFacesToElement, GL_UNSIGNED_INT, 0);
            glUniform1i(uDrawContour, 0);
            glEnable(GL_DEPTH_TEST);
        }
        //draw regular model

        //send model with proper transformations
        glUniformMatrix4fv(umodelM,1,false,modelM,0);

        //calculate normal matrix
        Matrix.invertM(inverseModel,0,modelM,0);
        glUniformMatrix4fv(uInverseModel,1,true,inverseModel,0);

        glDrawElements(GL_TRIANGLES, countFacesToElement, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }

}
