package ogles.oglbackbone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
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

import static android.opengl.GLES10.GL_CCW;
import static android.opengl.GLES10.GL_TEXTURE0;
import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_BACK;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LEQUAL;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LINEAR_MIPMAP_NEAREST;
import static android.opengl.GLES20.GL_REPEAT;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_INT;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glDepthFunc;
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFrontFace;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGenerateMipmap;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniform3f;
import static android.opengl.GLES20.glUniform3fv;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class TexAndLightRenderer extends BasicRenderer {

    private int VAO[];
    private int texObjId[];
    private int texUnit[];
    private int shaderHandle;
    private int MVPloc;

    private int blinnPhong;
    private int uBlinnPhong;

    private float viewM[];
    private float modelM[];

    private int uModelM;

    private float projM[];
    private float MVP[];
    private float temp[];
    private float inverseModel[];

    private int uInverseModel;

    private float lightPos[];
    private int uLightPos;

    private float eyePos[];
    private int uEyePos;

    private int countFacesToElementPlane;

    private float frame;

    public TexAndLightRenderer() {
        super();
        blinnPhong = 0;
        viewM = new float[16];
        modelM = new float[16];
        projM = new float[16];
        MVP = new float[16];
        temp = new float[16];
        inverseModel = new float[16];
        lightPos = new float[]{0,0.5f,5};
        eyePos = new float[]{0,0,10};
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
                if(blinnPhong==0)
                    blinnPhong = 1;
                else blinnPhong = 0;

                Log.v(TAG, (blinnPhong==0 ? "Phong":"Blinn-Phong") +
                        " lighting model activated");

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
                "layout(location = 3) in vec2 texCoord;\n" +
                "out vec2 varyingTexCoord;\n"+
                "out vec3 norm;\n"+
                "out vec3 fragModel;\n" +
                "uniform mat4 MVP;\n" +
                "uniform mat4 modelM;\n" +
                "uniform mat4 inverseModel;\n" +
                "\n" +
                "void main(){\n" +
                "norm = normalize(vec3(inverseModel*vec4(normal,1)));\n"+
                "fragModel = vec3(modelM*vec4(vPos,1));\n"+
                "varyingTexCoord = texCoord;\n"+
                "gl_Position = MVP * vec4(vPos,1);\n" +
                "}";

        String fragmentSrc = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D moondiff;\n" +
                "uniform sampler2D moonspec;\n" +
                "uniform int blinnPhong;\n" +
                "uniform vec3 lightPos;\n" +
                "uniform vec3 eyePos;\n" +
                "in vec2 varyingTexCoord;\n" +
                "in vec3 norm;\n"+
                "in vec3 fragModel;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 diffuseMap = texture(moondiff,varyingTexCoord);\n"+
                "vec4 specMap = texture(moonspec,varyingTexCoord);\n"+
                "vec4 ambientMap = mix(diffuseMap,specMap,vec4(0.5));\n"+
                "vec4 ambientComponent = 0.15 * ambientMap;\n" +
                "vec3 lightDir = normalize(lightPos-fragModel);\n" +
                "vec3 eyeDir = normalize(eyePos-fragModel);\n"+
                "float diffuse = max(dot(lightDir,norm),0.0);\n"+
                "float specular = 0.0;\n" +
                "if(blinnPhong==0){\n"+
                "vec3 refl = reflect(-lightDir,norm);\n" +
                "specular = pow( max(dot(eyeDir,refl),0.0), 1.0);\n"+
                "}else{\n"+
                "vec3 halfWay = normalize(lightDir+eyeDir);\n"+
                "specular = pow(max(dot(halfWay,norm),0.0),1.0);\n"+
                "}\n"+
                "fragColor = ambientComponent + diffuse*diffuseMap + specular*specMap; \n"+
                "}";

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);

        if(shaderHandle==-1)
            System.exit(-1);

        //mapping vertices to s,t texture coordinates.
        //also normals...
        float[] vertices = new float[]{
                -1,0,-1, 0,1,0, 0,5,
                1,0,1,  0,1,0,  5,0,
                1,0,-1, 0,1,0, 5,5,
                -1,0,1, 0,1,0, 0,0
        };


        int[] indices=new int[]{
                0, 1, 2,
                0, 3, 1
        };

        VAO = new int[1]; //0: plane

        //plane
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

        countFacesToElementPlane = indices.length;

        int VBO[] = new int[2]; //0: vpos, 1: indices

        glGenBuffers(2, VBO, 0);

        GLES30.glGenVertexArrays(2, VAO, 0);

        GLES30.glBindVertexArray(VAO[0]);
        glBindBuffer(GL_ARRAY_BUFFER, VBO[0]);
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexData.capacity(),
                vertexData, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, Float.BYTES*8, 0); //vpos
        glVertexAttribPointer(2, 3, GL_FLOAT, false, Float.BYTES*8, 3*Float.BYTES); //normals
        glVertexAttribPointer(3,2, GL_FLOAT, false, Float.BYTES*8,6*Float.BYTES); //st tex coord
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBO[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        //uniform fetching
        texUnit = new int[3];
        MVPloc = glGetUniformLocation(shaderHandle, "MVP");
        uModelM = glGetUniformLocation(shaderHandle, "modelM");
        uInverseModel = glGetUniformLocation(shaderHandle,"inverseModel");
        texUnit[0] = glGetUniformLocation(shaderHandle, "moondiff");
        texUnit[1] = glGetUniformLocation(shaderHandle, "moonspec");
        uBlinnPhong = glGetUniformLocation(shaderHandle,"blinnPhong");
        uEyePos = glGetUniformLocation(shaderHandle,"eyePos");
        uLightPos = glGetUniformLocation(shaderHandle,"lightPos");

        //send "fixed" uniforms
        glUseProgram(shaderHandle);
        glUniform3f(uLightPos,lightPos[0],lightPos[1],lightPos[2]);
        glUniform3f(uEyePos, eyePos[0], eyePos[1], eyePos[2]);
        glUseProgram(0);

        //tex units will be sent as we create GL_TEXTUREs

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled=false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.moondiff,opts);

        if(bitmap!=null)
            Log.v(TAG,"bitmap of size " + bitmap.getWidth()+"x"+bitmap.getHeight()+ " loaded " +
                    "with format " + bitmap.getConfig().name());

        texObjId = new int[2];
        glGenTextures(2, texObjId, 0);
        //index 0: diff
        //index 1: spec

        glBindTexture(GL_TEXTURE_2D, texObjId[0]);
        //tex filtering try both "nearest"
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        //try other params "i" for wrapping
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D,0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,texObjId[0]);
        glUseProgram(shaderHandle);
        glUniform1i(texUnit[0],0); //0 because active texture is GL_TEXTURE0.
        //glActiveTexture(GL_TEXTURE0+0) and glUniform1i(texUnit,GL_TEXTURE0+0) would be more correct
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);

        bitmap.recycle();

        bitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.moonspec,opts);
        glBindTexture(GL_TEXTURE_2D, texObjId[1]);
        //tex filtering try both "nearest"
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        //try other params "i" for wrapping
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D,0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,texObjId[1]);
        glUseProgram(shaderHandle);
        glUniform1i(texUnit[1],1);
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);

        bitmap.recycle();


    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        //activate textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texObjId[0]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texObjId[1]);

        //compute first part of MVP (no model yet)
        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);

        glUniform1i(uBlinnPhong,blinnPhong);

        //compute model Matrix for the plane
        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,-1,0);
        Matrix.scaleM(modelM,0,10,1,10);

        //send model matrix
        glUniformMatrix4fv(uModelM,1,false,modelM,0);

        //compute second part of MVP
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);

        //send MVP
        glUniformMatrix4fv(MVPloc, 1, false, MVP, 0);

        //find T(modelM^-1) and send
        Matrix.invertM(inverseModel, 0,modelM,0);
        glUniformMatrix4fv(uInverseModel,1,true,inverseModel,0);

        frame++;
        lightPos[2] = 10f * (float) Math.sin(frame/22.5f);
        glUniform3fv(uLightPos, 1, lightPos, 0);

        //draw (finally...)
        glDrawElements(GL_TRIANGLES, countFacesToElementPlane, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }

}
