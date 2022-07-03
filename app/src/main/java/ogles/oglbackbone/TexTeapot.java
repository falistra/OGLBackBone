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

public class TexTeapot extends BasicRenderer {

    private int VAO[];
    private int texObjId[];
    private int texUnit;
    private int shaderHandle;
    private int MVPloc;
    private int texSelectorLoc;
    private int texSelector;

    private int angle;

    private float viewM[];
    private float modelM[];
    private float projM[];
    private float MVP[];
    private float temp[];

    private int countFacesToElement;

    public TexTeapot() {
        super();
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
                    if(texSelector==0){
                        Log.v(TAG,"Procedurally calculating tex coords in the VS");
                        texSelector=1;
                    }
                    else {
                        Log.v(TAG,"Using texcoords are read from .ply file");
                        texSelector = 0;
                    }


            }
        });

    }


    @Override
    public void onSurfaceChanged(GL10 gl10, int w, int h) {
        super.onSurfaceChanged(gl10, w, h);
        float aspect = ((float) w) / ((float) (h == 0 ? 1 : h));

        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f);

        Matrix.setLookAtM(viewM, 0, 0, 0f, 2f,
                0, 0, 0,
                0, 1, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String vertexSrc = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec3 vPos;\n" +
                "layout(location = 2) in vec3 normals;\n" +
                "layout(location = 3) in vec2 texCoord;\n" +
                "out vec2 varyingTexCoord;\n"+
                "uniform mat4 MVP;\n" +
                "uniform int texSelector;\n"+
                "\n" +
                "void main(){\n" +
                "vec3 nv = normalize(vPos);\n" +
                "if(texSelector==0) varyingTexCoord = texCoord;\n"+
                "else varyingTexCoord =  vec2(nv.x/(1.0f-nv.y), nv.z/(1.0f-nv.y) );\n"+
                "gl_Position = MVP * vec4(vPos,1);\n" +
                "}";

        String fragmentSrc = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D tex;\n" +
                "in vec2 varyingTexCoord;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "fragColor = texture(tex,varyingTexCoord);\n" +
                "}";

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);

        if(shaderHandle < 0) {
            Log.v(TAG,"Error in shader(s) compile. Check SHADER_COMPILER logcat tag. Exiting");
            System.exit(-1);
        }

        InputStream is;
        float[] vertices=null;
        int[] indices=null;

        try {
            is = context.getAssets().open("ohioteapotst.ply");
            PlyObject po = new PlyObject(is);
            po.parse();
            vertices = po.getVertices();
            indices = po.getIndices();

        }catch(IOException | NumberFormatException e){
            e.printStackTrace();
        }


        VAO = new int[1]; //one VAO to bind the vpos, normals and st text coords

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
        glVertexAttribPointer(1, 3, GL_FLOAT, false, Float.BYTES*8, 0); //vpos
        glVertexAttribPointer(2, 3, GL_FLOAT, false, Float.BYTES*8, 3*Float.BYTES); //normals
        glVertexAttribPointer(3, 2, GL_FLOAT, false, Float.BYTES*8, 6*Float.BYTES); //texcoord
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBO[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexData.capacity(), indexData,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        MVPloc = glGetUniformLocation(shaderHandle, "MVP");
        texUnit = glGetUniformLocation(shaderHandle, "tex");
        texSelectorLoc = glGetUniformLocation(shaderHandle,"texSelector");

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled=false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.quilt,opts);

        if(bitmap!=null)
            Log.v(TAG,"bitmap of size " + bitmap.getWidth()+"x"+bitmap.getHeight()+ " loaded " +
                    "with format " + bitmap.getConfig().name());

        texObjId = new int[1];
        glGenTextures(1, texObjId, 0);
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
        glUniform1i(texUnit,0); //0 because active texture is GL_TEXTURE0.
        //glActiveTexture(GL_TEXTURE0+0) and glUniform1i(texUnit,GL_TEXTURE0+0) would be more correct
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);

        bitmap.recycle();



    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        angle += 1;

        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);

        Matrix.setIdentityM(modelM,0);
        //Matrix.translateM(modelM,0,0,0,0);
        Matrix.rotateM(modelM,0,angle,0.3f,-0.2f,0.45f);
        //Matrix.scaleM(modelM,0,10,1,10);
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texObjId[0]);
        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);
        glUniformMatrix4fv(MVPloc, 1, false, MVP, 0);
        glUniform1i(texSelectorLoc,texSelector);
        glDrawElements(GL_TRIANGLES, countFacesToElement, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

    }

}
