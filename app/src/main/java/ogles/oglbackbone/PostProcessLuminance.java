package ogles.oglbackbone;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;
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
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_CW;
import static android.opengl.GLES20.GL_DEPTH_ATTACHMENT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_COMPONENT;
import static android.opengl.GLES20.GL_DEPTH_COMPONENT16;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_COMPLETE;
import static android.opengl.GLES20.GL_FRONT;
import static android.opengl.GLES20.GL_GREATER;
import static android.opengl.GLES20.GL_LEQUAL;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LINEAR_MIPMAP_NEAREST;
import static android.opengl.GLES20.GL_RENDERBUFFER;
import static android.opengl.GLES20.GL_REPEAT;
import static android.opengl.GLES20.GL_RGB;
import static android.opengl.GLES20.GL_SCISSOR_TEST;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.GL_UNSIGNED_INT;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindRenderbuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glCheckFramebufferStatus;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glDepthFunc;
import static android.opengl.GLES20.glDisable;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFramebufferRenderbuffer;
import static android.opengl.GLES20.glFramebufferTexture2D;
import static android.opengl.GLES20.glFrontFace;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenRenderbuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGenerateMipmap;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glRenderbufferStorage;
import static android.opengl.GLES20.glScissor;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glUniform1f;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;
import static android.opengl.GLES30.GL_DEPTH_STENCIL_ATTACHMENT;

public class PostProcessLuminance extends BasicRenderer {

    private int VAO[];
    private int texObjId[];
    private int texUnit[];
    private int texUniSkyAndBunny;
    private int texUnitSkyAndBunny[];
    private int texUnitFBO[];
    private int uTexFBO;
    private int shaderHandle;
    private int shaderHandleSkyAndBunny;
    private int shaderHandleFBO;
    private int MVPloc;
    private int MVPlocSkyAndBunny;

    private float viewM[];
    private float modelM[];
    private float projM[];
    private float MVP[];
    private float temp[];

    private float deltaXsky;

    private int countFacesToElementPlane;
    private int countFacesToElementSkyDome;
    private int countFacesToElementBunny;

    private float scissorPosition;
    private int uScissorPosition;

    private int FBO[];

    public PostProcessLuminance() {
        super();
        viewM = new float[16];
        modelM = new float[16];
        projM = new float[16];
        MVP = new float[16];
        temp = new float[16];
        scissorPosition = 800;
        Matrix.setIdentityM(viewM, 0);
        Matrix.setIdentityM(modelM, 0);
        Matrix.setIdentityM(projM, 0);
        Matrix.setIdentityM(MVP, 0);
    }

    @Override
    @SuppressWarnings("ClickableViewAccessibility")
    public void setContextAndSurface(Context context, GLSurfaceView surface) {
        super.setContextAndSurface(context, surface);

        this.surface.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getActionMasked();

                switch(action){
                    case MotionEvent.ACTION_MOVE:
                        scissorPosition = motionEvent.getX();
                        break;
                }

                return true;
            }
        });

    }


    @Override
    public void onSurfaceChanged(GL10 gl10, int w, int h) {
        super.onSurfaceChanged(gl10, w, h);

        Log.v("TAG", "surf changed");

        float aspect = ((float) w) / ((float) (h == 0 ? 1 : h));

        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 500f);

        Matrix.setLookAtM(viewM, 0, 0, 0f, 10f,
                0, 0, 0,
                0, 1, 0);

        Log.v("TAG","SIZE CH " + w + " " +h);

        if(currentScreen.x !=0 && currentScreen.y !=0 && FBO==null){

            FBO = new int[1];
            glGenFramebuffers(1,FBO,0);
            glBindFramebuffer(GL_FRAMEBUFFER,FBO[0]);

            //texture
            Log.v("TAG","Size now is " + currentScreen.x + " " + currentScreen.y);
            texUnitFBO = new int[1];
            glGenTextures(1,texUnitFBO,0);
            glBindTexture(GL_TEXTURE_2D,texUnitFBO[0]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, currentScreen.x,currentScreen.y, 0, GL_RGB, GL_UNSIGNED_BYTE, null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindTexture(GL_TEXTURE_2D, 0);

            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texUnitFBO[0], 0);

            //depth renderbuffer
            int RBO[] = new int[1];
            glGenRenderbuffers(1,RBO,0);
            glBindRenderbuffer(GL_RENDERBUFFER,RBO[0]);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, currentScreen.x,currentScreen.y);
            //we attach the RBO to the Currently bind FBO
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, RBO[0]);

            int error = glCheckFramebufferStatus(GL_FRAMEBUFFER); //finalize operations
            if(error!=GL_FRAMEBUFFER_COMPLETE)
                Log.v(TAG,"Error in FBO completeness: " + error);
            else Log.v(TAG,"FBO is ok!");

            glBindFramebuffer(GL_FRAMEBUFFER, 0);

            uTexFBO = glGetUniformLocation(shaderHandleFBO,"fbo");
            uScissorPosition = glGetUniformLocation(shaderHandleFBO,"scisPos");

            scissorPosition = currentScreen.x/2;

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D,texUnitFBO[0]);
            glUseProgram(shaderHandleFBO);
            glUniform1i(uTexFBO,0);
            glUseProgram(0);
            glBindTexture(GL_TEXTURE_2D,0);
        }

    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        Log.v("TAG", "surf created");

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
                "in vec2 varyingTexCoord;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 k = texture(mixmap,varyingTexCoord);\n"+
                "k+=vec4(0.15f,0.25f,0.15f,0.0f);\n"+
                "fragColor = mix(texture(sand,varyingTexCoord),texture(grass,varyingTexCoord),k);\n"+
                "}";

        //Also for the bunny! Is a simple passthrough vpos -> fragcol -> texcol shader!
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
                "fragColor.a = 1.0;\n"+
                "}";

        String FBOvertex = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec3 vPos;\n" +
                "layout(location = 2) in vec2 texCoord;\n" +
                "out vec2 texel;\n"+
                "\n" +
                "void main(){\n" +
                "texel = texCoord;\n"+
                "gl_Position = vec4(vPos,1);\n" +
                "}";

        String FBOfragmentLum = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D fbo;\n" +
                "uniform float scisPos;\n"+
                "in vec2 texel;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 color = texture(fbo,texel);\n"+
                "if(gl_FragCoord.x<scisPos){\n"+
                "float lum = 0.2126*color.r + 0.7152*color.g + 0.0722*color.b;\n"+
                "fragColor = vec4(lum,lum,lum,1);\n"+
                "}\n"+
                "else fragColor = color;\n"+
                "}";

        String FBOfragmentSobel = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D fbo;\n" +
                "uniform float scisPos;\n"+
                "in vec2 texel;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 color = texture(fbo,texel);\n"+
                "if(gl_FragCoord.x<scisPos){\n"+
                "float filterS[9] = float[](" +
                "1.0,   1.0, 1.0, \n"+
                "1.0, -8.0, 1.0, \n"+
                "1.0,   1.0, 1.0);\n"+
                "float off = 1.0/300.0;\n"+
                "vec4 sum = vec4(0.0);\n"+
                "sum += texture(fbo,texel + vec2(-off,-off))*filterS[0];\n" +
                "sum += texture(fbo,texel + vec2(0,-off))*filterS[1];\n" +
                "sum += texture(fbo,texel + vec2(off,-off))*filterS[2];\n" +
                "sum += texture(fbo,texel + vec2(-off,0))*filterS[3];\n" +
                "sum += texture(fbo,texel + vec2(0,0))*filterS[4];\n" +
                "sum += texture(fbo,texel + vec2(off,0))*filterS[5];\n" +
                "sum += texture(fbo,texel + vec2(-off,off))*filterS[6];\n" +
                "sum += texture(fbo,texel + vec2(0,off))*filterS[7];\n" +
                "sum += texture(fbo,texel + vec2(off,off))*filterS[8];\n" +
                /**/"if(dot(vec3(0.2126,0.7152,0.0722),sum.rgb)>0.85)\n" + //play with the threshold!
                "sum=vec4(0,0,0,0);\n"+
                "else sum = color;\n"+ //to here for see the direct output of a sobel matrix./**/
                "fragColor = sum;\n"+
                "}\n"+
                "else fragColor = color;\n"+
                "}";

        String FBOfragmentGBlur = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D fbo;\n" +
                "uniform float scisPos;\n"+
                "in vec2 texel;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "vec4 color = texture(fbo,texel);\n"+
                "if(gl_FragCoord.x<scisPos){\n"+
                "float filterB[9] = float[](" +
                "1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0,\n"+
                "2.0 / 16.0, 4.0 / 16.0, 2.0 / 16.0,\n"+
                "1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0);\n"+
                "float off = 1.0/250.0;\n"+
                "vec4 sum = vec4(0.0);\n"+
                "sum += texture(fbo,texel + vec2(-off,-off))*filterB[0];\n" +
                "sum += texture(fbo,texel + vec2(0,-off))*filterB[1];\n" +
                "sum += texture(fbo,texel + vec2(off,-off))*filterB[2];\n" +
                "sum += texture(fbo,texel + vec2(-off,0))*filterB[3];\n" +
                "sum += texture(fbo,texel + vec2(0,0))*filterB[4];\n" +
                "sum += texture(fbo,texel + vec2(off,0))*filterB[5];\n" +
                "sum += texture(fbo,texel + vec2(-off,off))*filterB[6];\n" +
                "sum += texture(fbo,texel + vec2(0,off))*filterB[7];\n" +
                "sum += texture(fbo,texel + vec2(off,off))*filterB[8];\n" +
                "fragColor = sum;\n"+
                "}\n"+
                "else fragColor = color;\n"+
                "}";

        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);
        shaderHandleSkyAndBunny = ShaderCompiler.createProgram(vertexSrc,fragmentSrcSky);
        shaderHandleFBO = ShaderCompiler.createProgram(FBOvertex,
                FBOfragmentSobel);
                //FBOfragmentLum);
                //FBOfragmentGBlur);

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

        VAO = new int[4]; //0: plane, 1: skydome, 2: bunny,  3: post process FBO

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

        int VBO[] = new int[2]; //0: vertex attributes, 1: indices

        glGenBuffers(2, VBO, 0);

        GLES30.glGenVertexArrays(4, VAO, 0);

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

        //Stanford bunny
        float[] verticesBunny=null;
        int[] indicesBunny=null;

        try {
            is = context.getAssets().open("stanbunny.ply");
            PlyObject po = new PlyObject(is);
            po.parse();
            //Log.v("TAG",po.toString());
            verticesBunny = po.getVertices();
            indicesBunny = po.getIndices();

        }catch(IOException | NumberFormatException e){
            e.printStackTrace();
        }

        FloatBuffer vertexDataBunny =
                ByteBuffer.allocateDirect(verticesBunny.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        vertexDataBunny.put(verticesBunny);
        vertexDataBunny.position(0);

        IntBuffer indexDataBunny =
                ByteBuffer.allocateDirect(indicesBunny.length * Integer.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer();
        indexDataBunny.put(indicesBunny);
        indexDataBunny.position(0);

        countFacesToElementBunny= indicesBunny.length;

        int VBObunny[] = new int[2];
        glGenBuffers(2, VBObunny, 0);

        GLES30.glBindVertexArray(VAO[2]);
        glBindBuffer(GL_ARRAY_BUFFER, VBObunny[0]);
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertexDataBunny.capacity(),
                vertexDataBunny, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, Float.BYTES*5, 0); //vpos
        glVertexAttribPointer(2, 2, GL_FLOAT, false, Float.BYTES*5, 3*Float.BYTES); //texcoord
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBObunny[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * indexDataBunny.capacity(), indexDataBunny,
                GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);

        //Quad for VBO
        float quadVertices[] = new float[]{ // vertex attributes for a quad that fills the entire screen
                // positions   // texCoords
                -1.0f,  1.0f,  0.0f, 1.0f,
                -1.0f, -1.0f,  0.0f, 0.0f,
                1.0f, -1.0f,  1.0f, 0.0f,

                -1.0f,  1.0f,  0.0f, 1.0f,
                1.0f, -1.0f,  1.0f, 0.0f,
                1.0f,  1.0f,  1.0f, 1.0f
        };

        FloatBuffer quadVerticesData =
                ByteBuffer.allocateDirect(quadVertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        quadVerticesData.put(quadVertices);
        quadVerticesData.position(0);

        int VBOquad[] = new int[1];
        glGenBuffers(1,VBOquad,0); //1 VBO, no indexing this time.

        GLES30.glBindVertexArray(VAO[3]);
        glBindBuffer(GL_ARRAY_BUFFER,VBOquad[0]);
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * quadVerticesData.capacity(),
                quadVerticesData, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, Float.BYTES*4, 0); //vpos
        glVertexAttribPointer(2, 2, GL_FLOAT, false, Float.BYTES*4, 2*Float.BYTES); //texcoord
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        GLES30.glBindVertexArray(0);

        texUnit = new int[3];

        //uniform fetching for planes
        MVPloc = glGetUniformLocation(shaderHandle, "MVP");
        texUnit[0] = glGetUniformLocation(shaderHandle, "grass");
        texUnit[1] = glGetUniformLocation(shaderHandle, "sand");
        texUnit[2] = glGetUniformLocation(shaderHandle, "mixmap");

        //now for the skydome
        MVPlocSkyAndBunny = glGetUniformLocation(shaderHandleSkyAndBunny,"MVP");
        texUniSkyAndBunny = glGetUniformLocation(shaderHandleSkyAndBunny,"skytex");

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled=false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.grass,opts);

        if(bitmap!=null)
            Log.v("TAG","bitmap of size " + bitmap.getWidth()+"x"+bitmap.getHeight()+ " loaded " +
                    "with format " + bitmap.getConfig().name());

        texObjId = new int[3];
        glGenTextures(3, texObjId, 0);
        //index 0: grass
        //index 1: dirt
        //index 2: mix map

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

        texUnitSkyAndBunny = new int[2]; //0: skydome, 1: bunny furry texture
        glGenTextures(2, texUnitSkyAndBunny, 0);
        glBindTexture(GL_TEXTURE_2D, texUnitSkyAndBunny[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D,0);

        bitmap.recycle();

        //finally the bunny:

        bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.fabric, opts);

        glBindTexture(GL_TEXTURE_2D, texUnitSkyAndBunny[1]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D,0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,texUnitSkyAndBunny[0]); //does not matter which between 0 and 1.
        glUseProgram(shaderHandleSkyAndBunny);
        glUniform1i(texUniSkyAndBunny,0);
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);

    }

    @Override
    public void onDrawFrame(GL10 gl10) {

        glEnable(GL_DEPTH_TEST); //we need depth here.
        glBindFramebuffer(GL_FRAMEBUFFER,FBO[0]); //switch to custom FBO
        draw(); //offscreen render to texture of our beautiful scene.
        glBindFramebuffer(GL_FRAMEBUFFER,0); //switch back to default FBO
        glDisable(GL_DEPTH_TEST); //we don't need depth anymore...

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); //clear everything.

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texUnitFBO[0]);
        GLES30.glBindVertexArray(VAO[3]); //that's a quad: it's as big as the screen.
        glUseProgram(shaderHandleFBO); //post processing effect.
        glUniform1f(uScissorPosition,scissorPosition);
        glDrawArrays(GL_TRIANGLES,0,6); //quad drawcall
        glUseProgram(0); //resetting states
        GLES30.glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D,0);

        glEnable(GL_SCISSOR_TEST);
        glScissor((int)scissorPosition,0, 5, currentScreen.y);
        glClear(GL_COLOR_BUFFER_BIT);
        glClearColor(1,0,0,1);
        glDisable(GL_SCISSOR_TEST);

    }

    private void draw(){
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        //update cam pos
        //Matrix.translateM(viewM,0,0,0,0.01f);

        Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);

        //plane

        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,-1,0);
        //Matrix.rotateM(modelM,0,90,1,0,0);
        Matrix.scaleM(modelM,0,20,1,20);
        Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texObjId[0]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texObjId[1]);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, texObjId[2]);

        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);
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
        glBindTexture(GL_TEXTURE_2D, texUnitSkyAndBunny[0]);
        glUseProgram(shaderHandleSkyAndBunny);
        GLES30.glBindVertexArray(VAO[1]);
        glUniformMatrix4fv(MVPlocSkyAndBunny, 1, false, MVP, 0);
        glDrawElements(GL_TRIANGLES, countFacesToElementSkyDome, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

        //bunny

        Matrix.setIdentityM(modelM,0);
        Matrix.translateM(modelM,0,0,0,6);
        Matrix.rotateM(modelM,0,deltaXsky*20.0f,0,1,0);
        Matrix.multiplyMM(MVP,0,temp,0,modelM,0);

        //active texture unit is still zero!
        glBindTexture(GL_TEXTURE_2D,texUnitSkyAndBunny[1]);
        glUseProgram(shaderHandleSkyAndBunny);
        GLES30.glBindVertexArray(VAO[2]);
        glUniformMatrix4fv(MVPlocSkyAndBunny, 1, false, MVP, 0);
        glDrawElements(GL_TRIANGLES, countFacesToElementBunny, GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        glUseProgram(0);
        glBindTexture(GL_TEXTURE_2D,0);



    }

}
