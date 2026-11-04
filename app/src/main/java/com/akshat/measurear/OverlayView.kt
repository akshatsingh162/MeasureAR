package com.akshat.measurear

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/** Transparent view above the camera. Draws reticle, markers, lines and distance labels. */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val painter = OverlayPainter(context)
    private val density = resources.displayMetrics.density

    @Volatile private var frameData: OverlayFrame? = null

    var unit: MeasureUnit = MeasureUnit.CENTIMETERS
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    /** Safe to call from any thread. */
    fun submit(frame: OverlayFrame?) {
        frameData = frame
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val f = frameData ?: return
        val cx = width / 2f
        val cy = height * MeasureConfig.RETICLE_Y_FRACTION

        for (segment in f.segments) drawSegment(canvas, segment, preview = false)
        f.preview?.let { drawSegment(canvas, it, preview = true) }
        f.pending?.let {
            if (it.visible) painter.drawMarker(canvas, it.x, it.y, if (it.stable) 255 else 110)
        }

        val ring = f.reticleRing
        if (f.reticleLocked && ring != null) {
            painter.drawSurfaceRing(canvas, ring, cx, cy)
        } else {
            painter.drawIdleReticle(canvas, cx, cy)
        }
    }

    private fun drawSegment(canvas: Canvas, s: OverlaySegment, preview: Boolean) {
        val alpha = if (s.a.stable && s.b.stable) 255 else 110

        if (s.a.visible && s.b.visible) {
            painter.drawLine(canvas, s.a.x, s.a.y, s.b.x, s.b.y, alpha, dashed = preview)
        }
        if (s.a.visible) painter.drawMarker(canvas, s.a.x, s.a.y, alpha)
        if (s.b.visible && !preview) painter.drawMarker(canvas, s.b.x, s.b.y, alpha)

        if (s.a.visible && s.b.visible) {
            val text = DistanceFormatter.format(s.distanceMeters, unit).full
            painter.drawLabel(
                canvas,
                text,
                (s.a.x + s.b.x) / 2f,
                (s.a.y + s.b.y) / 2f - 28f * density,
                width.toFloat(),
                alpha
            )
        }
    }
}
