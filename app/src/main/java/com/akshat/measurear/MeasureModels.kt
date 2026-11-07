package com.akshat.measurear

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

object MeasureConfig {
    /** Reticle sits slightly above screen centre because the bottom panel covers more of the screen. */
    const val RETICLE_Y_FRACTION = 0.45f
    const val MAX_SEGMENTS = 8
    const val MAX_AREA_POINTS = 12

    /** Distances are rounded to 5 mm: finer than that is false precision for phone AR. */
    const val DISTANCE_QUANTUM_M = 0.005f
    const val AREA_QUANTUM_M2 = 0.001f

    /** If area corners sit further than this from a common plane, warn that the result is rough. */
    const val AREA_PLANARITY_WARN_M = 0.05f

    /** Set to false to compare plane-only hits against depth-assisted hits with a tape measure. */
    const val ENABLE_DEPTH = true

    const val LIVE_UPDATE_INTERVAL_MS = 80L
}

enum class MeasureMode {
    DISTANCE, HEIGHT, AREA;

    companion object {
        fun fromName(name: String?): MeasureMode =
            values().firstOrNull { it.name == name } ?: DISTANCE
    }
}

enum class MeasureUnit(val prefKey: String) {
    METERS("m"),
    CENTIMETERS("cm"),
    FEET_INCHES("ft_in");

    companion object {
        fun fromKey(key: String?): MeasureUnit =
            values().firstOrNull { it.prefKey == key } ?: CENTIMETERS
    }
}

enum class Guidance {
    STARTING, MOVE_SLOWER, MORE_LIGHT, MORE_DETAIL, CAMERA_BUSY, TRACKING_LOST,
    SCAN_SURFACE, PLACE_FIRST, SCAN_SECOND, PLACE_SECOND, COMPLETE,
    HEIGHT_BASE, HEIGHT_TOP,
    AREA_FIRST, AREA_NEXT, AREA_SCAN, AREA_MORE, AREA_FULL
}

enum class MeasureCommand { ADD_POINT, UNDO, RESET }

enum class MeasureEvent { POINT_PLACED, NO_SURFACE }

/** "value" fields are metres for DISTANCE/HEIGHT and square metres for AREA. */
data class MeasureUiState(
    val tracking: TrackingStatus = TrackingStatus.INITIALIZING,
    val guidance: Guidance = Guidance.STARTING,
    val reticleLocked: Boolean = false,
    val canUndo: Boolean = false,
    val lastValue: Float? = null,
    val liveValue: Float? = null,
    val perimeterMeters: Float? = null,
    val areaWarning: Boolean = false
)

// ---- Data handed from the GL thread to the overlay view ----

class OverlayPoint(val x: Float, val y: Float, val visible: Boolean, val stable: Boolean)

/** [corner] is only used by HEIGHT: the point on the first point's level directly below/above the second. */
class OverlaySegment(
    val a: OverlayPoint,
    val b: OverlayPoint,
    val distanceMeters: Float,
    val corner: OverlayPoint? = null
)

class OverlayFrame(
    val reticleLocked: Boolean,
    val reticleRing: FloatArray?,
    val segments: List<OverlaySegment>,
    val pending: OverlayPoint?,
    val preview: OverlaySegment?,
    val polygon: List<OverlayPoint> = emptyList(),
    val polygonPreview: OverlayPoint? = null,
    val areaValue: Float? = null
)

// ---- Formatting ----

data class FormattedDistance(val number: String, val unit: String) {
    val full: String get() = if (unit.isEmpty()) number else "$number $unit"
}

object DistanceFormatter {
    fun format(meters: Float, unit: MeasureUnit): FormattedDistance {
        val safe = if (meters.isNaN() || meters < 0f) 0f else meters
        val locale = Locale.getDefault()
        return when (unit) {
            MeasureUnit.METERS ->
                FormattedDistance(String.format(locale, "%.2f", safe), "m")

            MeasureUnit.CENTIMETERS ->
                FormattedDistance(String.format(locale, "%.1f", safe * 100f), "cm")

            MeasureUnit.FEET_INCHES -> {
                val totalInches = safe / 0.0254f
                if (totalInches < 12f) {
                    FormattedDistance(String.format(locale, "%.1f", totalInches), "in")
                } else {
                    var feet = floor(totalInches / 12f).toInt()
                    var inches = ((totalInches - feet * 12f) * 10f).roundToInt() / 10f
                    if (inches >= 12f) {
                        feet += 1
                        inches = 0f
                    }
                    FormattedDistance(
                        "$feet ft " + String.format(locale, "%.1f", inches),
                        "in"
                    )
                }
            }
        }
    }

    /** Area from square metres. */
    fun formatArea(squareMeters: Float, unit: MeasureUnit): FormattedDistance {
        val safe = if (squareMeters.isNaN() || squareMeters < 0f) 0f else squareMeters
        val locale = Locale.getDefault()
        return when (unit) {
            MeasureUnit.METERS ->
                FormattedDistance(String.format(locale, "%.2f", safe), "m²")

            MeasureUnit.CENTIMETERS ->
                FormattedDistance(String.format(locale, "%.0f", safe * 10000f), "cm²")

            MeasureUnit.FEET_INCHES ->
                FormattedDistance(String.format(locale, "%.2f", safe * 10.7639f), "ft²")
        }
    }
}
