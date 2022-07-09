package ogles.oglbackbone

import android.app.Activity
import android.opengl.GLES20
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ScissorRenderer_kt : BasicRenderer_kt() {
    private var rnd: Random = Random()
    private lateinit var color: FloatArray


    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        super.onSurfaceCreated(gl10, eglConfig)
        color = floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), 1f)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        Log.v(BasicRenderer.TAG, "Scissor test enabled")
        val androidActivityContext = this.getContextBasic() as Activity
        val textView = TextView(androidActivityContext)
        val params = FrameLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.BOTTOM
        androidActivityContext.runOnUiThread {
            androidActivityContext.addContentView(textView, params)
            textView.text = "Ready for input..."
        }
        this.getSurfaceBasic()!!
            .setOnTouchListener { view, _ ->
                view.performClick()
                //Log.v(TAG,"Touch event runs in " + Thread.currentThread().getName());
                textView.text = "Touch event runs in " + Thread.currentThread().name
                color = floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), 1f)
                false
            }

    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glScissor(0, 0, currentScreen!!.x / 2, currentScreen!!.y / 2)
        GLES20.glClearColor(color[0], color[1], color[2], color[3])
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glScissor(currentScreen!!.x / 2, 0, currentScreen!!.x / 2, currentScreen!!.y / 2)
        GLES20.glClearColor(1 - color[0], 1 - color[1], 1 - color[2], color[3])
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glScissor(0, currentScreen!!.y / 2, currentScreen!!.x / 2, currentScreen!!.y / 2)
        GLES20.glClearColor(1 - color[0], 1 - color[1], 1 - color[2], color[3])
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glScissor(
            currentScreen!!.x / 2,
            currentScreen!!.y / 2,
            currentScreen!!.x / 2,
            currentScreen!!.y / 2
        )
        GLES20.glClearColor(color[0], color[1], color[2], color[3])
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    }


}