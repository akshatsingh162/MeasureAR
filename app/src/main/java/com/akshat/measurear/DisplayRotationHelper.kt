package com.akshat.measurear

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Session

/**
 * Keeps ARCore's display geometry in sync with the screen rotation and size.
 * The flag is set from the UI thread and consumed on the GL thread.
 */
class DisplayRotationHelper(private val context: Context) : DisplayManager.DisplayListener {

    @Volatile private var viewportChanged = false
    @Volatile private var viewportWidth = 0
    @Volatile private var viewportHeight = 0

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun onResume() {
        displayManager.registerDisplayListener(this, null)
        viewportChanged = true
    }

    fun onPause() {
        displayManager.unregisterDisplayListener(this)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    /** Call on the GL thread, before session.update(). */
    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged && viewportWidth > 0 && viewportHeight > 0) {
            session.setDisplayGeometry(currentRotation(), viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    @Suppress("DEPRECATION")
    private fun currentRotation(): Int {
        val display: Display? = if (Build.VERSION.SDK_INT >= 30) {
            context.display
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        return display?.rotation ?: Surface.ROTATION_0
    }

    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }
}
