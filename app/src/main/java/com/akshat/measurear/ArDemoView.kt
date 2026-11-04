package com.akshat.measurear

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Locale

/** Looping illustration for the home screen: a reticle places two points on a floor grid. */
class ArDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val painter = OverlayPainter(context)
    private val d = resources.displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        color = 0xFF2A323D.toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        color = Color.WHITE
    }
    private val reticleShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        color = 0x66000000
    }
    private val reticleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        color = OverlayPainter.ACCENT
    }

    private val clip = Path()
    private val bounds = RectF()
    private val oval = RectF()
    private var floorTop = 0f
    private var progress = 0.85f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bounds.set(d * 0.5f, d * 0.5f, w - d * 0.5f, h - d * 0.5f)
        val r = 24f * d
        clip.reset()
        clip.addRoundRect(bounds, r, r, Path.Direction.CW)

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xFF1D2630.toInt(), 0xFF11161C.toInt(), Shader.TileMode.CLAMP
        )
        floorTop = h * 0.34f
        gridPaint.shader = LinearGradient(
            0f, floorTop, 0f, h.toFloat(),
            0x00FFFFFF, 0x30FFFFFF, Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val animationsEnabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        if (!animationsEnabled) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun smooth(p: Float, from: Float, to: Float): Float {
        val t = ((p - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 24f * d

        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(bounds, bgPaint)
        drawGrid(canvas, w, h)
        drawScene(canvas, w, h)
        canvas.restore()

        canvas.drawRoundRect(bounds, r, r, borderPaint)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val rows = 7
        for (i in 1..rows) {
            val frac = Math.pow(i.toDouble() / rows, 1.7).toFloat()
            val y = floorTop + (h - floorTop) * frac
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        val vx = w / 2f
        val vy = h * 0.10f
        val k = (floorTop - vy) / (h - vy)
        for (j in -4..14) {
            val xb = w * j / 10f
            val xh = vx + (xb - vx) * k
            canvas.drawLine(xh, floorTop, xb, h, gridPaint)
        }
    }

    private fun drawScene(canvas: Canvas, w: Float, h: Float) {
        val ax = w * 0.27f
        val ay = h * 0.80f
        val bx = w * 0.74f
        val by = h * 0.56f
        val sx = w * 0.50f
        val sy = h * 0.66f

        val p = progress
        val fade = 1f - smooth(p, 0.93f, 1f)
        val aIn = smooth(p, 0.16f, 0.24f)
        val moveA = smooth(p, 0f, 0.16f)
        val moveB = smooth(p, 0.26f, 0.56f)
        val bIn = smooth(p, 0.56f, 0.62f)
        val labelIn = smooth(p, 0.30f, 0.38f)

        // Current end of the line / reticle position.
        val curX: Float
        val curY: Float
        if (p < 0.26f) {
            curX = lerp(sx, ax, moveA)
            curY = lerp(sy, ay, moveA)
        } else {
            curX = lerp(ax, bx, moveB)
            curY = lerp(ay, by, moveB)
        }

        if (p >= 0.26f) {
            painter.drawLine(canvas, ax, ay, curX, curY, (255 * fade).toInt(), dashed = bIn < 1f)
        }
        painter.drawMarker(canvas, ax, ay, (255 * aIn * fade).toInt())
        painter.drawMarker(canvas, bx, by, (255 * bIn * fade).toInt())

        val reticleAlpha = (1f - bIn) * fade
        if (reticleAlpha > 0.01f) {
            val rx = 17f * d
            val ry = 7f * d
            oval.set(curX - rx, curY - ry, curX + rx, curY + ry)
            reticleShadow.alpha = (102 * reticleAlpha).toInt()
            reticleStroke.alpha = (255 * reticleAlpha).toInt()
            canvas.drawOval(oval, reticleShadow)
            canvas.drawOval(oval, reticleStroke)
        }

        if (p >= 0.30f) {
            val value = if (p >= 0.56f) 1.24f else 1.24f * moveB
            painter.drawLabel(
                canvas,
                String.format(Locale.getDefault(), "%.2f m", value),
                (ax + curX) / 2f,
                (ay + curY) / 2f - 28f * d,
                w,
                (255 * labelIn * fade).toInt()
            )
        }
    }
}
