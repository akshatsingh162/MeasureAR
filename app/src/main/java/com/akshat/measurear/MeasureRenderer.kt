package com.akshat.measurear

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Owns the ARCore frame loop. Everything except [enqueue] runs on the GL thread.
 *
 *  - draws the camera image
 *  - hit-tests the reticle position every frame
 *  - stores measurement points as Anchors (so they follow ARCore's map refinements)
 *  - projects everything to screen space for the Canvas overlay
 */
class MeasureRenderer(
    private val session: Session,
    private val displayRotationHelper: DisplayRotationHelper,
    private val listener: Listener
) : GLSurfaceView.Renderer {

    interface Listener {
        fun onUiState(state: MeasureUiState)
        fun onOverlay(frame: OverlayFrame)
        fun onEvent(event: MeasureEvent)
    }

    private class Segment(val a: Anchor) {
        var b: Anchor? = null
    }

    private val background = BackgroundRenderer()
    private val commands = ConcurrentLinkedQueue<MeasureCommand>()
    private val segments = ArrayList<Segment>()

    private var viewWidth = 0
    private var viewHeight = 0

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val projected = FloatArray(2)
    private val axisX = FloatArray(3)
    private val axisZ = FloatArray(3)
    private val normal = FloatArray(3)

    private var lastState: MeasureUiState? = null
    private var lastStateEmitMs = 0L
    private var loggedUpdateError = false

    /** Thread-safe: may be called from the UI thread. */
    fun enqueue(command: MeasureCommand) {
        commands.add(command)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        session.setCameraTextureName(background.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (viewWidth == 0 || viewHeight == 0) return

        displayRotationHelper.updateSessionIfNeeded(session)

        val frame: Frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            publishState(MeasureUiState(TrackingStatus.LIMITED, Guidance.CAMERA_BUSY))
            return
        } catch (e: Exception) {
            if (!loggedUpdateError) {
                Log.e(TAG, "session.update() failed", e)
                loggedUpdateError = true
            }
            return
        }

        background.draw(frame)

        val camera = frame.camera
        val isTracking = camera.trackingState == TrackingState.TRACKING

        val centerHit: HitResult? =
            if (isTracking) {
                pickHit(frame, camera, viewWidth / 2f, viewHeight * MeasureConfig.RETICLE_Y_FRACTION)
            } else {
                null
            }

        processCommands(centerHit)
        publishFrame(camera, isTracking, centerHit)
    }

    // ---------------------------------------------------------------- commands

    private fun processCommands(centerHit: HitResult?) {
        while (true) {
            val command = commands.poll() ?: break
            when (command) {
                MeasureCommand.ADD_POINT -> addPoint(centerHit)
                MeasureCommand.UNDO -> undo()
                MeasureCommand.RESET -> reset()
            }
        }
    }

    private fun addPoint(hit: HitResult?) {
        if (hit == null) {
            listener.onEvent(MeasureEvent.NO_SURFACE)
            return
        }

        val anchor = try {
            hit.createAnchor()
        } catch (e: Exception) {
            Log.w(TAG, "createAnchor failed", e)
            listener.onEvent(MeasureEvent.NO_SURFACE)
            return
        }

        val last = segments.lastOrNull()
        if (last != null && last.b == null) {
            last.b = anchor
        } else {
            if (segments.size >= MeasureConfig.MAX_SEGMENTS) {
                val oldest = segments.removeAt(0)
                oldest.a.detach()
                oldest.b?.detach()
            }
            segments.add(Segment(anchor))
        }
        listener.onEvent(MeasureEvent.POINT_PLACED)
    }

    private fun undo() {
        val last = segments.lastOrNull() ?: return
        val second = last.b
        if (second != null) {
            second.detach()
            last.b = null
        } else {
            last.a.detach()
            segments.removeAt(segments.lastIndex)
        }
    }

    private fun reset() {
        for (segment in segments) {
            segment.a.detach()
            segment.b?.detach()
        }
        segments.clear()
    }

    // ---------------------------------------------------------------- hit testing

    /**
     * Returns the nearest hit that is trustworthy enough to measure:
     *  - a tracked Plane, inside its detected polygon, with the camera in front of it
     *  - or a tracked DepthPoint (only produced when depth mode is enabled)
     */
    private fun pickHit(frame: Frame, camera: Camera, x: Float, y: Float): HitResult? {
        val cameraPose = camera.pose
        for (hit in frame.hitTest(x, y)) {
            val trackable = hit.trackable
            if (trackable.trackingState != TrackingState.TRACKING) continue
            when (trackable) {
                is Plane -> {
                    if (trackable.isPoseInPolygon(hit.hitPose) &&
                        signedDistanceToPlane(hit.hitPose, cameraPose) > 0f
                    ) {
                        return hit
                    }
                }
                is DepthPoint -> return hit
            }
        }
        return null
    }

    private fun signedDistanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        planePose.getTransformedAxis(1, 1f, normal, 0)
        val dx = cameraPose.tx() - planePose.tx()
        val dy = cameraPose.ty() - planePose.ty()
        val dz = cameraPose.tz() - planePose.tz()
        return dx * normal[0] + dy * normal[1] + dz * normal[2]
    }

    // ---------------------------------------------------------------- publishing

    private fun publishFrame(camera: Camera, isTracking: Boolean, centerHit: HitResult?) {
        val reason = camera.trackingFailureReason
        val status = when {
            isTracking -> TrackingStatus.TRACKING
            camera.trackingState == TrackingState.PAUSED && reason == TrackingFailureReason.NONE ->
                TrackingStatus.INITIALIZING
            else -> TrackingStatus.LIMITED
        }

        var ring: FloatArray? = null
        val completed = ArrayList<OverlaySegment>(segments.size)
        var pendingPoint: OverlayPoint? = null
        var pendingAnchor: Anchor? = null
        var lastDistance: Float? = null
        var preview: OverlaySegment? = null
        var live: Float? = null

        if (isTracking) {
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, NEAR_PLANE, FAR_PLANE)
            Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

            for (segment in segments) {
                val b = segment.b
                if (b == null) {
                    pendingAnchor = segment.a
                    pendingPoint = overlayPoint(segment.a)
                } else {
                    val d = quantize(distance(segment.a.pose, b.pose))
                    completed.add(OverlaySegment(overlayPoint(segment.a), overlayPoint(b), d))
                    lastDistance = d
                }
            }

            if (centerHit != null) {
                ring = buildRing(centerHit, camera)
                val anchor = pendingAnchor
                val pending = pendingPoint
                if (anchor != null && pending != null) {
                    val hitPose = centerHit.hitPose
                    val d = quantize(distance(anchor.pose, hitPose))
                    preview = OverlaySegment(pending, overlayPoint(hitPose, true), d)
                    live = d
                }
            }
        }

        listener.onOverlay(
            OverlayFrame(
                reticleLocked = centerHit != null,
                reticleRing = ring,
                segments = completed,
                pending = pendingPoint,
                preview = preview
            )
        )

        val last = segments.lastOrNull()
        val hasPending = last != null && last.b == null
        val guidance = when {
            !isTracking -> guidanceForFailure(camera.trackingState, reason)
            segments.isEmpty() ->
                if (centerHit != null) Guidance.PLACE_FIRST else Guidance.SCAN_SURFACE
            hasPending ->
                if (centerHit != null) Guidance.PLACE_SECOND else Guidance.SCAN_SECOND
            else -> Guidance.COMPLETE
        }

        publishState(
            MeasureUiState(
                tracking = status,
                guidance = guidance,
                reticleLocked = centerHit != null,
                canUndo = segments.isNotEmpty(),
                lastDistanceMeters = lastDistance,
                liveDistanceMeters = live
            )
        )
    }

    private fun guidanceForFailure(state: TrackingState, reason: TrackingFailureReason): Guidance =
        when (reason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> Guidance.MORE_LIGHT
            TrackingFailureReason.EXCESSIVE_MOTION -> Guidance.MOVE_SLOWER
            TrackingFailureReason.INSUFFICIENT_FEATURES -> Guidance.MORE_DETAIL
            TrackingFailureReason.CAMERA_UNAVAILABLE -> Guidance.CAMERA_BUSY
            else -> if (state == TrackingState.PAUSED) Guidance.STARTING else Guidance.TRACKING_LOST
        }

    /** Emits only on change; rapidly-changing live distance is throttled. */
    private fun publishState(state: MeasureUiState) {
        val previous = lastState
        val now = SystemClock.uptimeMillis()
        val structuralChange = previous == null ||
            state.copy(liveDistanceMeters = null) != previous.copy(liveDistanceMeters = null)
        val liveChange = state.liveDistanceMeters != previous?.liveDistanceMeters

        if (structuralChange ||
            (liveChange && now - lastStateEmitMs >= MeasureConfig.LIVE_UPDATE_INTERVAL_MS)
        ) {
            lastState = state
            lastStateEmitMs = now
            listener.onUiState(state)
        }
    }

    // ---------------------------------------------------------------- maths

    private fun distance(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun quantize(meters: Float): Float {
        val q = MeasureConfig.DISTANCE_QUANTUM_M
        return (meters / q).roundToInt() * q
    }

    /** Projects a world position to view pixels. Returns false if behind the camera. */
    private fun project(x: Float, y: Float, z: Float): Boolean {
        val m = viewProjection
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (cw <= 0.0001f) return false
        projected[0] = (cx / cw * 0.5f + 0.5f) * viewWidth
        projected[1] = (1f - (cy / cw * 0.5f + 0.5f)) * viewHeight
        return true
    }

    private fun overlayPoint(anchor: Anchor): OverlayPoint =
        overlayPoint(anchor.pose, anchor.trackingState == TrackingState.TRACKING)

    private fun overlayPoint(pose: Pose, stable: Boolean): OverlayPoint {
        val visible = project(pose.tx(), pose.ty(), pose.tz())
        return OverlayPoint(
            if (visible) projected[0] else 0f,
            if (visible) projected[1] else 0f,
            visible,
            stable
        )
    }

    /** A ring lying on the surface, so the reticle visibly follows the surface orientation. */
    private fun buildRing(hit: HitResult, camera: Camera): FloatArray? {
        val pose = hit.hitPose
        val radius = (RING_RADIUS_PER_METER * distance(pose, camera.pose)).coerceIn(0.01f, 0.15f)
        pose.getTransformedAxis(0, 1f, axisX, 0)
        pose.getTransformedAxis(2, 1f, axisZ, 0)

        val out = FloatArray(RING_SEGMENTS * 2)
        for (i in 0 until RING_SEGMENTS) {
            val angle = (2.0 * PI * i / RING_SEGMENTS).toFloat()
            val c = cos(angle) * radius
            val s = sin(angle) * radius
            val x = pose.tx() + c * axisX[0] + s * axisZ[0]
            val y = pose.ty() + c * axisX[1] + s * axisZ[1]
            val z = pose.tz() + c * axisX[2] + s * axisZ[2]
            if (!project(x, y, z)) return null
            out[i * 2] = projected[0]
            out[i * 2 + 1] = projected[1]
        }
        return out
    }

    private companion object {
        const val TAG = "MeasureRenderer"
        const val NEAR_PLANE = 0.05f
        const val FAR_PLANE = 100f
        const val RING_SEGMENTS = 32
        const val RING_RADIUS_PER_METER = 0.05f
    }
}
