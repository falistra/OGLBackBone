package ogles.oglbackbone;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLES31;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

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
import static android.opengl.GLES20.GL_LINES;
import static android.opengl.GLES20.GL_LINE_LOOP;
import static android.opengl.GLES20.GL_LINE_STRIP;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_INT;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glDepthFunc;
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFinish;
import static android.opengl.GLES20.glFlush;
import static android.opengl.GLES20.glFrontFace;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLineWidth;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniform3f;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class ClothVerletCS extends BasicRenderer {

    private int VAO[];
    private int VBO[];
    private int shaderHandle;
    private int computePositionShader;
    private int computeStickShader;

    private int uMVPloc;
    private int uNPosition;
    private int uNSticks;
    private int uColor;
    private int uSwitch;
    private int uFrameCount;

    private float viewM[];
    private float modelM[];
    private float projM[];
    private float MVP[];
    private float temp[];

    private FloatBuffer hVPos; //host side vertex local position
    private FloatBuffer hVPosOld; //host side for vertex local previous position
    private IntBuffer hIndices; //host side indices
    private IntBuffer hSticks; //host side sticks

    private static final int THREADS_PER_GROUP_X = 16;
    private static final int THREADS_PER_GROUP_Y = 16;
    //we divide the tesselated quad along the X and Y dimension
    //assigning a thread on each cloth intersection point.

    private static final int NUM_ELEMENTS = THREADS_PER_GROUP_X * THREADS_PER_GROUP_Y * 4;
    //we use a vec4 for each vertex. (x,y,z,isFixed)

    private static final int NUM_INDICES = (THREADS_PER_GROUP_X-1)*(THREADS_PER_GROUP_Y-1)*6;
    private static final int NUM_STICKS = 2*((NUM_INDICES/3)+
            (THREADS_PER_GROUP_Y-1) +
            (THREADS_PER_GROUP_X-1));

    //cloth size:
    private static final float CLOTH_WIDTH = 1.0f;
    private static final float CLOTH_HEIGHT = 1.0f;

    private int countFaces;
    private int countFrame;
    private boolean resubmit;

    private TextView renderTimeDisplayTexT;
    private long startTime;
    private long endTime;
    private float deltaBetweenFrames;

    private static float round (float value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (float) Math.round(value * scale) / scale;
    }

    public ClothVerletCS() {
        super();

        //Back to using projective geometry
        viewM = new float[16];
        modelM = new float[16];
        projM = new float[16];
        MVP = new float[16];
        temp = new float[16];
        Matrix.setIdentityM(viewM, 0);
        Matrix.setIdentityM(modelM, 0);
        Matrix.setIdentityM(projM, 0);
        Matrix.setIdentityM(MVP, 0);

        resubmit = false;

        //allocating memory on host side
        hVPos = ByteBuffer.allocateDirect(NUM_ELEMENTS * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        hVPosOld = ByteBuffer.allocateDirect(NUM_ELEMENTS * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        hIndices = ByteBuffer.allocateDirect(NUM_INDICES * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();

        hSticks = ByteBuffer.allocateDirect(NUM_STICKS * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();

        //procedurally generation of a indexed, tessellated quad mesh:

        float stepX = CLOTH_WIDTH / (float)(THREADS_PER_GROUP_X-1);
        float stepY = CLOTH_HEIGHT / (float)(THREADS_PER_GROUP_Y-1);

        float startPointX = -CLOTH_WIDTH/2.0f;
        float startPointY = -CLOTH_HEIGHT/2.0f;

        float tempArray[] = new float[4];
        int count = 0;
        for (int y = 0; y < THREADS_PER_GROUP_Y; y++) {
            for(int x=0; x < THREADS_PER_GROUP_X; x++) {
               tempArray[0] = round(startPointX + (float)x*stepX,3);
               tempArray[1] = round(startPointY + (float)y*stepY,3);
               tempArray[2] = 0;

               if((x==(0)&&y==(THREADS_PER_GROUP_Y-1)) ||(x==(THREADS_PER_GROUP_X-1)&&y==(THREADS_PER_GROUP_Y-1)))
                   tempArray[3] = 1.0f;
               else tempArray[3] = -1.0f;

               //Log.v(TAG,"C: " + count+ " " + tempArray[0] + " " + tempArray[1] + " " + tempArray[2] + " " + tempArray[3]);
               hVPos.put(tempArray);
                //should give initial velocity on positive Z
               tempArray[2] = 0.0000f/**(THREADS_PER_GROUP_Y*THREADS_PER_GROUP_X-count)*/;
               hVPosOld.put(tempArray);
               count++;
            }
        }

        //count=0;
        int tempTriIndex[] = new int[6];
        for(int y=0; y<(THREADS_PER_GROUP_Y-1); y++){
            for(int x=0; x<(THREADS_PER_GROUP_X-1); x++){
                //first triangle
                int startPoint = y*(THREADS_PER_GROUP_X)+x;
                tempTriIndex[0] = startPoint;
                tempTriIndex[1] = startPoint + 1;
                tempTriIndex[2] = startPoint + THREADS_PER_GROUP_X;
                /*Log.v(TAG,"1TRI "+ count+ " " + tempTriIndex[0] + " " + tempTriIndex[1] + " " + tempTriIndex[2]);
                count++;*/
                //second triangle
                tempTriIndex[3] = tempTriIndex[1];
                tempTriIndex[4] = tempTriIndex[2]+1;
                tempTriIndex[5] = tempTriIndex[2];
                hIndices.put(tempTriIndex);
                /*count++;
                Log.v(TAG,"2TRI "+ count+ " " + tempTriIndex[3] + " " + tempTriIndex[4] + " " + tempTriIndex[5]);*/
                //Sticks: 2 per 2 triangles, 4 for the last couple of the row
                /* tempTriIndex Indices
                (2/5)--4
                | \    |
                |  \   |
                |   \  |
                0----(1/3)
                */
                hSticks.put(startPoint); hSticks.put(startPoint+1);
                hSticks.put(startPoint); hSticks.put(startPoint + THREADS_PER_GROUP_X);

                if(y==(THREADS_PER_GROUP_Y-2)) {
                    hSticks.put(startPoint + THREADS_PER_GROUP_X+1);
                    hSticks.put(startPoint + THREADS_PER_GROUP_X);

                }
                if(x==(THREADS_PER_GROUP_X-2)){
                    hSticks.put(startPoint+1);
                    hSticks.put(startPoint + THREADS_PER_GROUP_X+1);
                }

            }
        }

        hIndices.position(0);
        hVPosOld.position(0);
        hVPos.position(0);
        hSticks.position(0);

        /*for(int i=0; i<(hSticks.capacity()-1); i=i+2)
            Log.v(TAG, hSticks.get(i) + " " +hSticks.get(i+1));*/

        countFaces = hIndices.capacity();

    }

    @Override
    public void setContextAndSurface(Context context, GLSurfaceView surface) {
        super.setContextAndSurface(context, surface);

        this.surface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v(TAG,"Resubmitting data to reset the cloth simulation");
                resubmit = true;
            }
        });

        renderTimeDisplayTexT = new TextView(getContext());
        renderTimeDisplayTexT.setText("PROVA");
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        ((Activity)getContext()).addContentView(renderTimeDisplayTexT,params);

    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int w, int h) {
        super.onSurfaceChanged(gl10, w, h);
        float aspect = ((float) w) / ((float) (h == 0 ? 1 : h));

        Matrix.perspectiveM(projM, 0, 45f, aspect, 0.1f, 100f);

        Matrix.setLookAtM(viewM, 0, 0, 0f, 10f,
                0, 0, 0,
                0, 1, 0);

        if(shaderHandle!=0){
            glUseProgram(shaderHandle);
            Matrix.multiplyMM(temp, 0, projM, 0, viewM, 0);
            Matrix.setIdentityM(modelM,0);
            Matrix.translateM(modelM,0,0,0,6.75f);
            Matrix.multiplyMM(MVP, 0, temp, 0, modelM, 0);
            glUniformMatrix4fv(uMVPloc,1,false,MVP,0);
            glUseProgram(0);
        }


    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        super.onSurfaceCreated(gl10, eglConfig);

        String vertexSrc = "#version 300 es\n" +
                "\n" +
                "layout(location = 1) in vec4 vPos;\n" +
                "uniform mat4 MVP;\n" +
                "out float isFixed;\n" +
                "\n" +
                "void main(){\n" +
                "isFixed = vPos.w;\n"+
                "gl_Position = MVP * vec4(vPos.xyz,1);\n" +
                "}";

        String fragmentSrc = "#version 300 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "in float isFixed;\n"+
                "uniform vec3 color;\n" +
                "out vec4 fragColor;\n" +
                "\n" +
                "void main() {\n" +
                "if(isFixed<0.0)\n" +
                "fragColor = vec4(color,1);\n" +
                "else fragColor = vec4(1,0,0,1);\n"+
                "}";

        String computeSrcPosition =  "#version 310 es\n" +
                "\n" +
                "layout(binding = 1) buffer oldPosStruct{\n" +
                "vec4 elements[];\n" +
                "}oldPos;\n" +
                "layout(binding = 2) buffer posStruct{\n" +
                "vec4 elements[];\n" +
                "}pos;\n" +
                "uniform int N;\n" +
                "uniform int countFrame;\n"+
                "const float gravity = -0.009;\n"+
                "\n" +
                "layout( local_size_x = " + THREADS_PER_GROUP_X + ", local_size_y = 1, local_size_z = 1 ) in;\n" +
                "void main(){\n" +
                "int gid = int(gl_GlobalInvocationID.x);\n" +
                "if(countFrame>250 && gid==("+((THREADS_PER_GROUP_Y*THREADS_PER_GROUP_X)-1)+"))\n"+
                "pos.elements[gid].w=-1.0;\n"+
                "if(gid >= N || pos.elements[gid].w>0.0) return;\n" +
                "vec3 velocity = pos.elements[gid].xyz - oldPos.elements[gid].xyz;\n" +
                //"if(length(velocity)<0.001f) return;\n"+
                "oldPos.elements[gid] = pos.elements[gid];\n"+
                "pos.elements[gid] = vec4(pos.elements[gid].xyz+velocity,pos.elements[gid].w);\n" +
                "pos.elements[gid].y += gravity;\n"+
                "}";

        String computeSrcSticks = "#version 310 es\n" +
                "\n" +
                "layout(binding = 2) buffer posStruct{\n" +
                "vec4 elements[];\n" +
                "}pos;\n" +
                "layout(binding = 3) readonly buffer stickStruct{\n" +
                "ivec2 elements[];\n" +
                "}sticks;\n"+
                "uniform int N;\n" +
                "uniform int switchSide;\n"+
                "const float stickLen = "+ CLOTH_HEIGHT/(float)(THREADS_PER_GROUP_X) +";\n"+
                "\n" +
                "layout( local_size_x = " + THREADS_PER_GROUP_X + ", local_size_y = 1, local_size_z = 1 ) in;\n" +
                "void main(){\n" +
                "int gid = int(gl_GlobalInvocationID.x);\n" +
                "if(gid >= N || ((gid%2)!=switchSide)) return;\n" +
                "int indexA = sticks.elements[gid].x;\n" +
                "int indexB = sticks.elements[gid].y;\n" +
                "vec3 delta = vec3(pos.elements[indexB].xyz-pos.elements[indexA].xyz);\n"+
                "float currStickLen = length(delta);\n"+
                "float differ = (stickLen - currStickLen);\n" +
                "float perc = differ / currStickLen / 2.0;\n"+
                "vec3 offset = vec3(delta.x*perc,delta.y*perc, delta.z*perc);\n"+
                "if(length(offset)<0.0001f) return;\n"+
                "if(pos.elements[indexA].w < 0.0)\n" +
                "pos.elements[indexA] = vec4(pos.elements[indexA].xyz-offset,pos.elements[indexA].w);\n"+
                "if(pos.elements[indexB].w < 0.0)\n" +
                "   pos.elements[indexB] = vec4(pos.elements[indexB].xyz+offset,pos.elements[indexB].w);\n"+
                "}";


        shaderHandle = ShaderCompiler.createProgram(vertexSrc, fragmentSrc);
        computePositionShader = ShaderCompiler.createComputeProgram(computeSrcPosition);
        computeStickShader = ShaderCompiler.createComputeProgram(computeSrcSticks);

        VAO = new int[1]; //for drawing
        VBO = new int[4]; //0: old pos, 1: vpos, 2: sticks, 3: indices
        glGenBuffers(4, VBO, 0);

        GLES30.glGenVertexArrays(1, VAO, 0);

        //for drawing
        GLES30.glBindVertexArray(VAO[0]);
        glBindBuffer(GL_ARRAY_BUFFER, VBO[1]); //vpos: that's what we draw
        glBufferData(GL_ARRAY_BUFFER, Float.BYTES * hVPos.capacity(), hVPos, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 4*Float.BYTES, 0); //vpos
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, VBO[3]); //indices
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, Integer.BYTES * hIndices.capacity(), hIndices,
                GL_STATIC_DRAW);
        GLES30.glBindVertexArray(0);

        //for computing Verlet integration
        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER,VBO[0]); //old vpos
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hVPosOld.capacity(), hVPosOld,
                GL_STATIC_DRAW);
        //we already sent vpos at position 1 earlier.
        //...
        glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER,VBO[2]); //sticks
        glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Integer.BYTES * hSticks.capacity(), hSticks,
                GL_STATIC_DRAW);

        //all necessary data is allocated device-side and sent.

        //error check:
        Log.v(TAG, glGetError() + " after buffers movement error");

        uMVPloc = glGetUniformLocation(shaderHandle, "MVP");
        uColor = glGetUniformLocation(shaderHandle,"color");
        uNPosition = glGetUniformLocation(computePositionShader, "N");
        uNSticks = glGetUniformLocation(computeStickShader, "N");
        uSwitch = glGetUniformLocation(computeStickShader,"switchSide");
        uFrameCount = glGetUniformLocation(computePositionShader,"countFrame");

        glUseProgram(computePositionShader);
        glUniform1i(uNPosition,THREADS_PER_GROUP_X*THREADS_PER_GROUP_Y);
        glUseProgram(0);

        glUseProgram(computeStickShader);
        glUniform1i(uNSticks,NUM_STICKS/2);
        glUseProgram(0);

        glLineWidth(3.0f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

    }


    @Override
    public void onDrawFrame(GL10 gl10) {

        deltaBetweenFrames = (float)(System.nanoTime()-startTime);
        deltaBetweenFrames /= (1000f*1000f);
        startTime = System.nanoTime();

        glClear(GL_COLOR_BUFFER_BIT  | GL_DEPTH_BUFFER_BIT);
        glClearColor(1,1,1,1);

        if(resubmit){
            glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, VBO[1]);
            glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER,Float.BYTES * hVPos.capacity(),
                    hVPos, GL_STATIC_DRAW);
            glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER,VBO[0]);
            glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Float.BYTES * hVPosOld.capacity(),
                    hVPosOld, GL_STATIC_DRAW);
            glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER,VBO[2]);
            glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, Integer.BYTES * hSticks.capacity(),
                    hSticks, GL_STATIC_DRAW);
            resubmit = false;
            countFrame = 0;
        }

        countFrame++;

        //draw
        glUseProgram(shaderHandle);
        GLES30.glBindVertexArray(VAO[0]);
        glUniform3f(uColor,118.0f/255.0f,185.0f/255f,0);
        glDrawElements(GL_TRIANGLES, countFaces, GL_UNSIGNED_INT, 0); //solid
        glUniform3f(uColor,0,0,0);
        glDrawElements(GL_LINES, countFaces, GL_UNSIGNED_INT, 0); //wireframe
        GLES30.glBindVertexArray(0);
        glUseProgram(0);

        if((countFrame%1)==0) {
            //compute

            glUseProgram(computePositionShader);

            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, VBO[0]); //bind oldPos
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, VBO[1]); //bind vPos
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, VBO[2]); //bind sticks

            glUniform1i(uFrameCount,countFrame);
            GLES31.glDispatchCompute(THREADS_PER_GROUP_X * THREADS_PER_GROUP_Y / THREADS_PER_GROUP_X, 1, 1);
            //glUseProgram(0);

            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

            if(countFrame>250)
                countFrame = 0;

            glUseProgram(computeStickShader);
            for (int i = 0; i <500; i++) {
                glUniform1i(uSwitch, (i % 2));
                GLES31.glDispatchCompute((int) Math.ceil(
                        (double) (NUM_STICKS / 2) / (double) THREADS_PER_GROUP_X), 1, 1);
            }
            //glUseProgram(0);
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);
        }

/*
        glFlush();
        glFinish();
*/
        endTime = System.nanoTime();

        //renderTimeDisplayTexT.setText("Render time " + (end-start)); //won't work

        if((countFrame%5)==0) {
            ((Activity) getContext()).
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String s = "" + ((float)(endTime - startTime))/(1000f*1000f) + " ms";
                            s += " delta " + deltaBetweenFrames + " ms";
                            renderTimeDisplayTexT.setText(s);
                        }
                    });
        }
    }

    }
