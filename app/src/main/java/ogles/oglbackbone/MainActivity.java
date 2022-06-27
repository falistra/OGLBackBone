package ogles.oglbackbone;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private GLSurfaceView surface;
    private boolean isSurfaceCreated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Optional for full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags
                (WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //get a reference to the Activity Manager (AM)
        final ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        //from the AM we get an object with our mobile device info
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();

        int supported = 1;

        if(configurationInfo.reqGlEsVersion>=0x30000)
            supported = 3;
        else if(configurationInfo.reqGlEsVersion>=0x20000)
            supported = 2;

        Log.v("TAG","Opengl ES supported >= " +
                supported + " (" + Integer.toHexString(configurationInfo.reqGlEsVersion) + " " +
                configurationInfo.getGlEsVersion() + ")");

        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(supported);
        surface.setPreserveEGLContextOnPause(true);
        //GLSurfaceView.Renderer renderer = new BasicRenderer(0.45f,0.32f,0.13f);
        //GLSurfaceView.Renderer renderer = new ScissorRenderer();
        //GLSurfaceView.Renderer renderer = new VBOVAORenderer();
        //GLSurfaceView.Renderer renderer = new MultiInterleavedRenderer();
        GLSurfaceView.Renderer renderer = new IndexedRenderer();
        //GLSurfaceView.Renderer renderer = new MatTransfRenderer();
        //GLSurfaceView.Renderer renderer = new PersOrthoRenderer();
        //GLSurfaceView.Renderer renderer = new DepthTestRenderer();
        //GLSurfaceView.Renderer renderer = new ModelViewer();
        //GLSurfaceView.Renderer renderer = new TextFilterRenderer();
        //GLSurfaceView.Renderer renderer = new TexTeapot();
        //GLSurfaceView.Renderer renderer = new MultiTexPlane();
        //GLSurfaceView.Renderer renderer = new TeapotDisplaced();
        //GLSurfaceView.Renderer renderer = new TeapotLight();
        //GLSurfaceView.Renderer renderer = new TexAndLightRenderer();
        //GLSurfaceView.Renderer renderer = new ToonShadingRenderer();
        //GLSurfaceView.Renderer renderer = new PostProcessLuminance();
        //GLSurfaceView.Renderer renderer = new ComputeShaderExample();
        //GLSurfaceView.Renderer renderer = new ComputeShaderNN();
        //GLSurfaceView.Renderer renderer = new ClothVerletCS();

        setContentView(surface);
        ((BasicRenderer) renderer).setContextAndSurface(this,surface);
        surface.setRenderer(renderer);
        isSurfaceCreated = true;

        //Log.v("TAG",getWindow().getDecorView().findViewById(android.R.id.content).toString());

    }

    @Override
    public void onResume(){
        super.onResume();
        if(isSurfaceCreated)
            surface.onResume();
    }


    @Override
    public void onPause(){
        super.onPause();
        if(isSurfaceCreated)
            surface.onPause();
    }
}
