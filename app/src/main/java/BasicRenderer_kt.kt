package ogles.oglbackbone

import android.content.Context
import android.graphics.Point
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

open class BasicRenderer_kt(r: Float = 0f, g: Float = 0f, b: Float = 0f, a: Float = 1f) : GLSurfaceView.Renderer  {
    protected var clearScreen: FloatArray
    protected var currentScreen: Point? = null
    protected var context: Context? = null
    protected var surface: GLSurfaceView? = null
    open var TAG: String? = javaClass.simpleName

    init {
        clearScreen = floatArrayOf(r, g, b, a)
        currentScreen = Point(0, 0)
    }

    open fun setContextAndSurface(context: Context?, surface: GLSurfaceView?) {
        this.context = context
        this.surface = surface
    }

    open fun getContextBasic(): Context? {
        return context
    }

    open fun getSurfaceBasic(): GLSurfaceView? {
        return surface
    }


    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        GLES20.glClearColor(clearScreen[0], clearScreen[1], clearScreen[2], clearScreen[3])
        Log.v(TAG, "glGetString " + GLES20.glGetString(GLES20.GL_VERSION))
    }

    override fun onSurfaceChanged(gl10: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        currentScreen!!.x = w
        currentScreen!!.y = h
    }

    override fun onDrawFrame(gl10: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }


}