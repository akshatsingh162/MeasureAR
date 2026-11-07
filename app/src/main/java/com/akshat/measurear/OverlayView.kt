package com.akshat.measurear

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/** Transparent view above the camera. Draws reticle, markers, lines, polygons and labels. */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val painter = OverlayPainter(context)
    private val density = resources.displayMetrics.density
    private val polyBuffer = FloatArray(2 * (MeasureConfig.MAX_AREA_POINTS + 1))

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
        if (f.polygon.isNotEmpty()) drawPolygon(canvas, f)

        val ring = f.reticleRing
        if (f.reticleLocked && ring != null) {
            painter.drawSurfaceRing(canvas, ring, cx, cy)
        } else {
            painter.drawIdleReticle(canvas, cx, cy)
        }
    }

    private fun drawPolygon(canvas: Canvas, f: OverlayFrame) {
        val points = ArrayList<OverlayPoint>(f.polygon.size + 1)
        points.addAll(f.polygon)
        f.polygonPreview?.let { points.add(it) }

        val allVisible = points.all { it.visible }
        val stable = f.polygon.all { it.stable }
        val alpha = if (stable) 255 else 110

        if (allVisible && points.size >= 2) {
            var sx = 0f
            var sy = 0f
            for (i in points.indices) {
                polyBuffer[i * 2] = points[i].x
                polyBuffer[i * 2 + 1] = points[i].y
                sx += points[i].x
                sy += points[i].y
            }
            painter.drawPolygon(canvas, polyBuffer, points.size, alpha)

            val area = f.areaValue
            if (area != null && points.size >= 3) {
                val text = DistanceFormatter.formatArea(area, unit).full
                painter.drawLabel(canvas, text, sx / points.size, sy / points.size, width.toFloat(), alpha)
            }
        }
        for (p in f.polygon) if (p.visible) painter.drawMarker(canvas, p.x, p.y, alpha)
    }

    private fun drawSegment(canvas: Canvas, s: OverlaySegment, preview: Boolean) {
        val alpha = if (s.a.stable && s.b.stable) 255 else 110
        val corner = s.corner

        if (corner != null) {
            // HEIGHT: dashed level reference from the first point, solid vertical to the second.
            if (s.a.visible && corner.visible) {
                painter.drawLine(canvas, s.a.x, s.a.y, corner.x, corner.y, alpha / 2, dashed = true)
            }
            if (corner.visible && s.b.visible) {
                painter.drawLine(canvas, corner.x, corner.y, s.b.x, s.b.y, alpha, dashed = preview)
            }
            if (s.a.visible) painter.drawMarker(canvas, s.a.x, s.a.y, alpha)
            if (s.b.visible && !preview) painter.drawMarker(canvas, s.b.x, s.b.y, alpha)
            if (corner.visible && s.b.visible) {
                val text = DistanceFormatter.format(s.distanceMeters, unit).full
                painter.drawLabel(
                    canvas, text,
                    (corner.x + s.b.x) / 2f,
                    (corner.y + s.b.y) / 2f,
                    width.toFloat(), alpha
                )
            }
            return
        }

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
