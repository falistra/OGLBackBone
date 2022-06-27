package ogles.oglbackbone;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_SCISSOR_TEST;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glScissor;

public class ScissorRenderer extends BasicRenderer {

   private Random rnd;
   private float color[];

    public ScissorRenderer(){
        super();
        rnd = new Random();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        super.onSurfaceCreated(gl10, eglConfig);
        color = new float[]{rnd.nextFloat(),rnd.nextFloat(),rnd.nextFloat(),1};
        glEnable(GL_SCISSOR_TEST);
        Log.v(TAG,"Scissor test enabled");

        final Activity androidActivityContext = ((Activity)this.getContext());
        final TextView textView = new TextView(androidActivityContext);
        final FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        androidActivityContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                androidActivityContext.addContentView(textView,params);
                textView.setText("Ready for input...");
            }
        });

        this.getSurface().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                //Log.v(TAG,"Touch event runs in " + Thread.currentThread().getName());
                textView.setText("Touch event runs in " + Thread.currentThread().getName());
                color = new float[]{rnd.nextFloat(),rnd.nextFloat(),rnd.nextFloat(),1};
                return false;
            }
        });
    }

    @Override
    public void onDrawFrame(GL10 gl10){

        glScissor(0,0, currentScreen.x/2, currentScreen.y/2);
        glClearColor(color[0],color[1],color[2],color[3]);
        glClear(GL_COLOR_BUFFER_BIT);

        glScissor(currentScreen.x/2,0, currentScreen.x/2, currentScreen.y/2);
        glClearColor(1-color[0],1-color[1],1-color[2],color[3]);
        glClear(GL_COLOR_BUFFER_BIT);

        glScissor(0,currentScreen.y/2, currentScreen.x/2, currentScreen.y/2);
        glClearColor(1-color[0],1-color[1],1-color[2],color[3]);
        glClear(GL_COLOR_BUFFER_BIT);

        glScissor(currentScreen.x/2,currentScreen.y/2, currentScreen.x/2, currentScreen.y/2);
        glClearColor(color[0],color[1],color[2],color[3]);
        glClear(GL_COLOR_BUFFER_BIT);

        //Try with viewports. It won't work...
/*
        glViewport(0, 0, currentScreen.x, currentScreen.y/2);
        glClearColor(1,0,0,1);
        glClear(GL_COLOR_BUFFER_BIT);
        glViewport(0, currentScreen.y/2, currentScreen.x, currentScreen.y/2);
        glClearColor(0,1,0,1);
        glClear(GL_COLOR_BUFFER_BIT);
*/
    }







}
