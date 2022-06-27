package ogles.oglbackbone;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
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
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFrontFace;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniform3fv;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class TeapotLight extends BasicRenderer {

    final static boolean PHONG_MODEL = true;

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
    private int uEyePos;

    public TeapotLight() {
        super();
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

        String vertexSrcGouraud =  "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec3 vPos;\n" +
                "layout(location = 2) in vec3 normal;\n" +
                "uniform mat4 MVP;\n" +
                "uniform mat4 modelMatrix;\n" +
                "uniform mat4 inverseModel;\n" +
                "uniform vec3 lightPos;\n" +
                "uniform vec3 eyePos;\n" +
                "out vec4 finalColor;\n" +
                "\n" +
                "void main(){\n" +
                "vec3 transfNormal = normalize(vec3(inverseModel * vec4(normal,1)));\n" +
                "vec3 fragModel = vec3(modelMatrix * vec4(vPos,1));\n" +
                "vec4 specComponent = vec4(0.92,0.94,0.69,1);\n"+
                "vec4 diffuseComponent = vec4(0.64,0.84,0.15,1);\n"+
                "vec4 ambientComponent = vec4(0.12,0.4,0.01,1);\n" +
                "vec3 eyeDir = normalize(eyePos-fragModel);\n"+
                "vec3 lightDir = normalize(lightPos-fragModel);\n"+
                "float diff = max(dot(lightDir,transfNormal),0.0);\n"+
                "vec3 refl = reflect(-lightDir,transfNormal);\n" +
                "float spec =  pow( max(dot(eyeDir,refl),0.0), 32.0);\n"+
                "finalColor = ambientComponent + diff*diffuseComponent + spec*specComponent; \n"+
                "gl_Position = MVP * vec4(vPos,1);\n" +
                "}";


        String fragmentSrcGouraud = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "in vec4 finalColor;\n"+
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "fragColor = finalColor;\n"+
                "}";

        String vertexSrcPhong = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec3 vPos;\n" +
                "layout(location = 2) in vec3 normal;\n" +
                "uniform mat4 MVP;\n" +
                "uniform mat4 modelMatrix;\n" +
                "uniform mat4 inverseModel;\n" +
                "out vec3 fragModel;\n" +
                "out vec3 transfNormal;\n" +
                "\n" +
                "void main(){\n" +
                    "transfNormal = normalize(vec3(inverseModel * vec4(normal,1)  ));\n" +
                    "fragModel = vec3(modelMatrix * vec4(vPos,1));\n" +
                    "gl_Position = MVP * vec4(vPos,1);\n" +
                "}";

        String fragmentSrcPhong = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform vec3 lightPos;\n" +
                "uniform vec3 eyePos;\n" +
                "in vec3 fragModel;\n" +
                "in vec3 transfNormal;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 specComponent = vec4(0.92,0.94,0.69,1);\n"+
                "vec4 diffuseComponent = vec4(0.64,0.84,0.15,1);\n"+
                "vec4 ambientComponent = vec4(0.12,0.4,0.01,1);\n" +
                "vec3 eyeDir = normalize(eyePos-fragModel);\n"+
                "vec3 lightDir = normalize(lightPos-fragModel);\n"+
                "float diff = max(dot(lightDir,transfNormal),0.0);\n"+
                "vec3 refl = reflect(-lightDir,transfNormal);\n" +
                "float spec =  pow( max(dot(eyeDir,refl),0.0), 32.0);\n"+
                "fragColor = ambientComponent + diff*diffuseComponent + spec*specComponent; \n"+
                "}";

        if(PHONG_MODEL)
            shaderHandle = ShaderCompiler.createProgram(vertexSrcPhong, fragmentSrcPhong);
        else shaderHandle = ShaderCompiler.createProgram(vertexSrcGouraud, fragmentSrcGouraud);

        if(shaderHandle==-1)
            System.exit(-1);

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
        uEyePos = glGetUniformLocation(shaderHandle,"eyePos");

        //pre load uniform values:

        glUseProgram(shaderHandle);
        glUniform3fv(uLightPos,1,lightPos,0);
        glUniform3fv(uEyePos,1,eyePos,0);
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

        //angle = 185f;

        angle += 1f;

        //compute first part of MVP (no model yet)
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);

        //compute model Matrix
        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,0,8);
        Matrix.rotateM(modelM,0,angle,0.2f,1f,0.4f);

        //send model matrix
        glUniformMatrix4fv(umodelM,1,false,modelM,0);

        //compute second part of MVP
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);

        //send MVP
        glUniformMatrix4fv(MVPloc, 1, false, MVP, 0);

        //compute T(modelM^-1) and send
        Matrix.invertM(inverseModel, 0,modelM,0);
        glUniformMatrix4fv(uInverseModel,1,true,inverseModel,0);
        //the third param of glUniformMatrix4fv is set to true (i.e. transpose while send)

        //draw (finally...)
        glDrawElements(GL_TRIANGLES, countFacesToElement, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }



}
