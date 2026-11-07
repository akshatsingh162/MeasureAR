package com.akshat.measurear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue

/** Shared drawing code so the demo and the live overlay look identical. */
class OverlayPainter(context: Context) {

    private val metrics = context.resources.displayMetrics
    private val density = metrics.density

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, metrics)

    private fun stroke(color: Int, widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = dp(widthDp)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private val lineShadow = stroke(0x66000000, 6.5f)
    private val linePaint = stroke(Color.WHITE, 3f)
    private val dash = DashPathEffect(floatArrayOf(dp(9f), dp(7f)), 0f)

    private val markerShadow = fill(0x59000000)
    private val markerRing = stroke(Color.WHITE, 2.5f)
    private val markerFill = fill(ACCENT)

    private val ringFill = fill((ACCENT and 0x00FFFFFF) or 0x33000000)
    private val ringShadow = stroke(0x66000000, 5f)
    private val ringStroke = stroke(ACCENT, 2.5f)

    private val polyFill = fill((ACCENT and 0x00FFFFFF) or 0x40000000)

    private val idleShadow = stroke(0x55000000, 4.5f)
    private val idleStroke = stroke(0xE6FFFFFF.toInt(), 2f)
    private val dotPaint = fill(Color.WHITE)

    private val labelBg = fill(0xE6101419.toInt())
    private val labelBorder = stroke(0x33FFFFFF, 1f)
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }

    private val path = Path()
    private val rect = RectF()

    /** alpha: 0..255 */
    fun drawLine(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float, alpha: Int, dashed: Boolean) {
        if (alpha <= 0) return
        val effect = if (dashed) dash else null
        lineShadow.pathEffect = effect
        linePaint.pathEffect = effect
        lineShadow.alpha = alpha * 102 / 255
        linePaint.alpha = alpha
        canvas.drawLine(ax, ay, bx, by, lineShadow)
        canvas.drawLine(ax, ay, bx, by, linePaint)
    }

    /** Closed polygon from [count] x/y pairs in [coords]; filled when it has 3+ corners. */
    fun drawPolygon(canvas: Canvas, coords: FloatArray, count: Int, alpha: Int) {
        if (count < 2 || alpha <= 0) return
        path.reset()
        path.moveTo(coords[0], coords[1])
        for (i in 1 until count) path.lineTo(coords[i * 2], coords[i * 2 + 1])
        if (count >= 3) {
            path.close()
            polyFill.alpha = 0x40 * alpha / 255
            canvas.drawPath(path, polyFill)
        }
        lineShadow.pathEffect = null
        linePaint.pathEffect = null
        lineShadow.alpha = alpha * 102 / 255
        linePaint.alpha = alpha
        canvas.drawPath(path, lineShadow)
        canvas.drawPath(path, linePaint)
    }

    fun drawMarker(canvas: Canvas, x: Float, y: Float, alpha: Int) {
        if (alpha <= 0) return
        markerShadow.alpha = alpha * 89 / 255
        markerRing.alpha = alpha
        markerFill.alpha = alpha
        canvas.drawCircle(x, y, dp(13f), markerShadow)
        canvas.drawCircle(x, y, dp(10.5f), markerRing)
        canvas.drawCircle(x, y, dp(6f), markerFill)
    }

    /** A rounded pill with text, centred on (cx, cy) and clamped inside [0, boundsWidth]. */
    fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, boundsWidth: Float, alpha: Int) {
        if (alpha <= 0) return
        val w = labelText.measureText(text) + dp(24f)
        val h = dp(32f)
        val minLeft = dp(8f)
        val left = (cx - w / 2f).coerceIn(minLeft, maxOf(minLeft, boundsWidth - w - minLeft))
        rect.set(left, cy - h / 2f, left + w, cy + h / 2f)

        labelBg.alpha = 0xE6 * alpha / 255
        labelBorder.alpha = 0x33 * alpha / 255
        labelText.alpha = alpha
        canvas.drawRoundRect(rect, h / 2f, h / 2f, labelBg)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, labelBorder)

        val fm = labelText.fontMetrics
        canvas.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, labelText)
    }

    /** Reticle locked onto a surface: ring projected onto the surface plus a centre dot. */
    fun drawSurfaceRing(canvas: Canvas, ring: FloatArray, cx: Float, cy: Float) {
        path.reset()
        val n = ring.size / 2
        for (i in 0 until n) {
            if (i == 0) path.moveTo(ring[0], ring[1]) else path.lineTo(ring[i * 2], ring[i * 2 + 1])
        }
        path.close()
        canvas.drawPath(path, ringFill)
        canvas.drawPath(path, ringShadow)
        canvas.drawPath(path, ringStroke)
        canvas.drawCircle(cx, cy, dp(3f), markerFill)
    }

    /** Reticle when no surface is under the crosshair. */
    fun drawIdleReticle(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(14f)
        val tick = dp(6f)
        canvas.drawCircle(cx, cy, r, idleShadow)
        canvas.drawCircle(cx, cy, r, idleStroke)
        for (i in 0 until 4) {
            val dx = if (i == 0) 1f else if (i == 1) -1f else 0f
            val dy = if (i == 2) 1f else if (i == 3) -1f else 0f
            canvas.drawLine(cx + dx * r, cy + dy * r, cx + dx * (r + tick), cy + dy * (r + tick), idleShadow)
            canvas.drawLine(cx + dx * r, cy + dy * r, cx + dx * (r + tick), cy + dy * (r + tick), idleStroke)
        }
        canvas.drawCircle(cx, cy, dp(2.5f), dotPaint)
    }

    companion object {
        const val ACCENT = 0xFF37DCBE.toInt()
    }
}
