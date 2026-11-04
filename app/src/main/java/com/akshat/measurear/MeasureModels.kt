package com.akshat.measurear

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

object MeasureConfig {
    /** Reticle sits slightly above screen centre because the bottom panel covers more of the screen. */
    const val RETICLE_Y_FRACTION = 0.45f
    const val MAX_SEGMENTS = 8

    /** Distances are rounded to 5 mm: finer than that is false precision for phone AR. */
    const val DISTANCE_QUANTUM_M = 0.005f

    /** Set to false to compare plane-only hits against depth-assisted hits with a tape measure. */
    const val ENABLE_DEPTH = true

    const val LIVE_UPDATE_INTERVAL_MS = 80L
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
    SCAN_SURFACE, PLACE_FIRST, SCAN_SECOND, PLACE_SECOND, COMPLETE
}

enum class MeasureCommand { ADD_POINT, UNDO, RESET }

enum class MeasureEvent { POINT_PLACED, NO_SURFACE }

data class MeasureUiState(
    val tracking: TrackingStatus = TrackingStatus.INITIALIZING,
    val guidance: Guidance = Guidance.STARTING,
    val reticleLocked: Boolean = false,
    val canUndo: Boolean = false,
    val lastDistanceMeters: Float? = null,
    val liveDistanceMeters: Float? = null
)

// ---- Data handed from the GL thread to the overlay view ----

class OverlayPoint(val x: Float, val y: Float, val visible: Boolean, val stable: Boolean)

class OverlaySegment(val a: OverlayPoint, val b: OverlayPoint, val distanceMeters: Float)

class OverlayFrame(
    val reticleLocked: Boolean,
    val reticleRing: FloatArray?,
    val segments: List<OverlaySegment>,
    val pending: OverlayPoint?,
    val preview: OverlaySegment?
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
}
