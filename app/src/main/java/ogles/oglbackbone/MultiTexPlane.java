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
import static android.opengl.GLES20.GL_TEXTURE2;
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
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class MultiTexPlane extends BasicRenderer {

    private int VAO[];
    private int texObjId[];
    private int texUnit[];
    private int texUniSky;
    private int texUnitSky[];
    private int shaderHandle;
    private int shaderHandleSky;
    private int MVPloc;
    private int MVPlocSky;

    private int texSelector;
    private int texSelectorUni;

    private float viewM[];
    private float modelM[];
    private float projM[];
    private float MVP[];
    private float temp[];

    private float deltaXsky;

    private int countFacesToElementPlane;
    private int countFacesToElementSkyDome;

    public MultiTexPlane() {
        super();
        texSelector = 0;
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
                texSelector++;
                texSelector %= 3;
                if(texSelector==0)
                Log.v(TAG, "Multi texturing enable" );
                else if (texSelector==1)
                    Log.v(TAG, "Unit 0 only");
                else Log.v(TAG, "Unit 1 only");

            }
        });

    }


    @Override
    public void onSurfaceChanged(GL10 gl10, int w, int h) {
        super.onSurfaceChanged(gl10, w, h);
        float aspect = ((float) w) / ((float) (h == 0 ? 1 : h));

        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 500f);

        Matrix.setLookAtM(viewM, 0, 0, 0f, 10f,
                0, 0, 0,
                0, 1, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String vertexSrc = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec3 vPos;\n" +
                "layout(location = 2) in vec2 texCoord;\n" +
                "out vec2 varyingTexCoord;\n"+
                "uniform mat4 MVP;\n" +
                "\n" +
                "void main(){\n" +
                "varyingTexCoord = texCoord;\n"+
                "gl_Position = MVP * vec4(vPos,1);\n" +
                "}";

        String fragmentSrc = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D grass;\n" +
                "uniform sampler2D sand;\n" +
                "uniform sampler2D mixmap;\n" +
                "uniform int texSelector;\n" +
                "in vec2 varyingTexCoord;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 k = texture(mixmap,varyingTexCoord);\n"+
                //"k+=vec4(0.15f,0.25f,0.15f,0.0f);\n"+
                "if(texSelector==0)\n" +
                "fragColor = mix(texture(sand,varyingTexCoord),texture(grass,varyingTexCoord),k);\n"+
                "else if(texSelector==1)\n"+
                "fragColor = texture(grass,varyingTexCoord);\n"+
                "else fragColor = texture(sand,varyingTexCoord);\n"+
                "}";

        String fragmentSrcSky = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D skytex;\n" +
                "in vec2 varyingTexCoord;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "fragColor = texture(skytex,varyingTexCoord);\n"+
                "}";

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);
        shaderHandleSky = ShaderCompiler.createProgram(vertexSrc,fragmentSrcSky);

        //mapping vertices to s,t texture coordinates
        float[] vertices = new float[]{
                -1,0,-1, 0,5,
                1,0,1, 5,0,
                1,0,-1, 5,5,
                -1,0,1, 0,0
        };

        int[] indices=new int[]{
                0, 1, 2,
                0, 3, 1
        };

        VAO = new int[2]; //0: plane, 1: skydome

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
        glVertexAttribPointer(1, 3, GL_FLOAT, false, Float.BYTES*5, 0); //vpos
        glVertexAttribPointer(2, 2, GL_FLOAT, false, Float.BYTES*5, 3*Float.BYTES); //texcoord
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBO[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        //skydome:

        InputStream is;
        float[] verticesSky=null;
        int[] indicesSky=null;

        try {
            is = context.getAssets().open("skydome.ply");
            PlyObject po = new PlyObject(is);
            po.parse();
            //Log.v("TAG",po.toString());
            verticesSky = po.getVertices();
            indicesSky = po.getIndices();

        }catch(IOException | NumberFormatException e){
            e.printStackTrace();
        }

        FloatBuffer vertexDataSky =
                ByteBuffer.allocateDirect(verticesSky.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        vertexDataSky.put(verticesSky);
        vertexDataSky.position(0);

        IntBuffer indexDataSky =
                ByteBuffer.allocateDirect(indicesSky.length * Integer.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer();
        indexDataSky.put(indicesSky);
        indexDataSky.position(0);

        countFacesToElementSkyDome = indicesSky.length;

        int VBOsky[] = new int[2];
        glGenBuffers(2, VBOsky, 0);

        GLES30.glBindVertexArray(VAO[1]);
        glBindBuffer(GL_ARRAY_BUFFER, VBOsky[0]);
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexDataSky.capacity(),
                vertexDataSky, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, Float.BYTES*5, 0); //vpos
        glVertexAttribPointer(2, 2, GL_FLOAT, false, Float.BYTES*5, 3*Float.BYTES); //texcoord
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBOsky[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexDataSky.capacity(), indexDataSky,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        //uniform fetching for planes
        texUnit = new int[3];
        MVPloc = glGetUniformLocation(shaderHandle, "MVP");
        texUnit[0] = glGetUniformLocation(shaderHandle, "grass");
        texUnit[1] = glGetUniformLocation(shaderHandle, "sand");
        texUnit[2] = glGetUniformLocation(shaderHandle, "mixmap");
        texSelectorUni = glGetUniformLocation(shaderHandle,"texSelector");

        //now for the skydome
        MVPlocSky = glGetUniformLocation(shaderHandleSky,"MVP");
        texUniSky = glGetUniformLocation(shaderHandleSky,"skytex");

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled=false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.grass,opts);

        if(bitmap!=null)
            Log.v(TAG,"bitmap of size " + bitmap.getWidth()+"x"+bitmap.getHeight()+ " loaded " +
                    "with format " + bitmap.getConfig().name());

        texObjId = new int[3];
        glGenTextures(3, texObjId, 0);
        //index 0: grass
        //index 1: dirt
        //index 3: mix map

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

        bitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.sand,opts);
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

        bitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.reactdiffuse,opts);
        glBindTexture(GL_TEXTURE_2D, texObjId[2]);
        //tex filtering try both "nearest"
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        //try other params "i" for wrapping
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D,0);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D,texObjId[2]);
        glUseProgram(shaderHandle);
        glUniform1i(texUnit[2],2);
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);

        bitmap.recycle();

        //now for the skydome:

         bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.skydome,opts);

        if(bitmap!=null)
            Log.v(TAG,"bitmap of size " + bitmap.getWidth()+"x"+bitmap.getHeight()+ " loaded " +
                    "with format " + bitmap.getConfig().name());

        texUnitSky = new int[1];
        glGenTextures(1, texUnitSky, 0);
        glBindTexture(GL_TEXTURE_2D, texUnitSky[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D,0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,texUnitSky[0]);
        glUseProgram(shaderHandleSky);
        glUniform1i(texUniSky,0);
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);

    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        //update cam pos
        Matrix.translateM(viewM,0,0,0,0.01f);

        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);

        //plane

        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,-1,0);
        //Matrix.rotateM(modelM,0,90,1,0,0);
        Matrix.scaleM(modelM,0,5,1,5);
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texObjId[0]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texObjId[1]);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, texObjId[2]);

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);
        glUniform1i(texSelectorUni,texSelector);
        glUniformMatrix4fv(MVPloc, 1, false, MVP, 0);
        glDrawElements(GL_TRIANGLES, countFacesToElementPlane, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

        //sky
        deltaXsky += 0.01f;

        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,-2,0.01f);
        Matrix.rotateM(modelM,0,deltaXsky,0,1,0);
        Matrix.scaleM(modelM,0,40,40,40);
        Matrix.multiplyMM(MVP,0,temp,0,modelM,0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texUnitSky[0]);
        glUseProgram(shaderHandleSky);
        GLES30.glBindVertexArray(VAO[1]);
        glUniformMatrix4fv(MVPlocSky, 1, false, MVP, 0);
        glDrawElements(GL_TRIANGLES, countFacesToElementSkyDome, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }

}
