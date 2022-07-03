package ogles.oglbackbone

import android.app.ActivityManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
import android.app.Activity

class MainActivity_kt : Activity() {

    private var surface: GLSurfaceView? = null
    private var isSurfaceCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //get a reference to the Activity Manager (AM)
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        //from the AM we get an object with our mobile device info
        val configurationInfo = activityManager.deviceConfigurationInfo

        var supported = 1
        if (configurationInfo.reqGlEsVersion >= 0x30000) supported =  3
        else if (configurationInfo.reqGlEsVersion >= 0x20000) supported = 2

        Log.v(
            "TAG", "Opengl ES supported >= " +
                    supported + " (" + Integer.toHexString(configurationInfo.reqGlEsVersion) + " " +
                    configurationInfo.glEsVersion + ")"
        )


        surface = GLSurfaceView(this)
        surface!!.setEGLContextClientVersion(supported)
        surface!!.setPreserveEGLContextOnPause(true)

        //GLSurfaceView.Renderer renderer = new BasicRenderer(0.45f,0.32f,0.13f);
        //val renderer: GLSurfaceView.Renderer = BasicRenderer_kt(0.45f,0.32f,0.13f);

        //GLSurfaceView.Renderer renderer = new ScissorRenderer();
        //val renderer: GLSurfaceView.Renderer = ScissorRenderer_kt()

        //GLSurfaceView.Renderer renderer = new VBOVAORenderer();
        //val renderer: GLSurfaceView.Renderer = VBOVAORenderer_kt()

        //GLSurfaceView.Renderer renderer = new MultiInterleavedRenderer();
        //val renderer: GLSurfaceView.Renderer = MultiInterleavedRenderer_kt()

        //GLSurfaceView.Renderer renderer = new IndexedRenderer();
        //val renderer: GLSurfaceView.Renderer = IndexedRenderer_kt()

        //GLSurfaceView.Renderer renderer = new MatTransfRenderer();
        //val renderer: GLSurfaceView.Renderer = MatTransfRenderer_kt()

        //GLSurfaceView.Renderer renderer = new PersOrthoRenderer();
        //val renderer: GLSurfaceView.Renderer = PersOrthoRenderer_kt()

        //GLSurfaceView.Renderer renderer = new ModelViewer();
        //val renderer: GLSurfaceView.Renderer = ModelViewer_kt()

        //GLSurfaceView.Renderer renderer = new TextFilterRenderer();
        //val renderer: GLSurfaceView.Renderer = TextFilterRenderer_kt()

        //GLSurfaceView.Renderer renderer = new TexTeapot();
        // val renderer: GLSurfaceView.Renderer = TexTeapot_kt()

        //GLSurfaceView.Renderer renderer = new MultiTexPlane();
        val renderer: GLSurfaceView.Renderer = MultiTexPlane_kt()

        //GLSurfaceView.Renderer renderer = new TeapotDisplaced();
        //val renderer: GLSurfaceView.Renderer = TeapotDisplaced_kt()

        //GLSurfaceView.Renderer renderer = new TeapotLight();
        //val renderer: GLSurfaceView.Renderer = TeapotLight_kt()

        //GLSurfaceView.Renderer renderer = new TexAndLightRenderer();
        //** val renderer: GLSurfaceView.Renderer = TexAndLightRenderer_kt()

        //GLSurfaceView.Renderer renderer = new ToonShadingRenderer();
        //** val renderer: GLSurfaceView.Renderer = ToonShadingRenderer_kt()

        //GLSurfaceView.Renderer renderer = new PostProcessLuminance();
        //** val renderer: GLSurfaceView.Renderer = PostProcessLuminance_kt()

        //GLSurfaceView.Renderer renderer = new ComputeShaderExample();
        //** val renderer: GLSurfaceView.Renderer = ComputeShaderExample_kt()

        //GLSurfaceView.Renderer renderer = new ComputeShaderNN();
        //** val renderer: GLSurfaceView.Renderer = ComputeShaderNN_kt()

        //GLSurfaceView.Renderer renderer = new ClothVerletCS();
        //val renderer: GLSurfaceView.Renderer = ClothVerletCS_kt()

        setContentView(surface)
        (renderer as BasicRenderer_kt).setContextAndSurface(this, surface)
        surface!!.setRenderer(renderer)
        isSurfaceCreated = true

    }

    override fun onResume() {
        super.onResume()
        if (isSurfaceCreated) surface!!.onResume()
    }


    override fun onPause() {
        super.onPause()
        if (isSurfaceCreated) surface!!.onPause()
    }

}