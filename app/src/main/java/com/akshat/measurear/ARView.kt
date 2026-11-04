package com.akshat.measurear

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
/**
 * Thin GLSurfaceView that can be inflated from XML. The renderer (which needs an ARCore
 * Session) is attached later, once the session exists.
 */
class ARView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var measureRenderer: MeasureRenderer? = null

    /** GLSurfaceView.onPause()/onResume() crash if called before setRenderer(). */
    val isAttached: Boolean get() = measureRenderer != null

    fun attach(renderer: MeasureRenderer) {
        if (measureRenderer != null) return
        measureRenderer = renderer
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun sendCommand(command: MeasureCommand) {
        measureRenderer?.enqueue(command)
    }
}
