package com.akshat.measurear

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

class MeasureActivity : ComponentActivity(), MeasureRenderer.Listener {

    private var session: Session? = null
    private var installRequested = false
    private var permissionRequestInFlight = false
    private var permissionDenied = false

    private var unit = MeasureUnit.CENTIMETERS
    private var lastState = MeasureUiState()
    private var blockingHandler: (() -> Unit)? = null

    private val displayRotationHelper by lazy { DisplayRotationHelper(this) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private lateinit var measureRoot: View
    private lateinit var arView: ARView
    private lateinit var overlayView: OverlayView
    private lateinit var topArea: View
    private lateinit var bottomArea: View
    private lateinit var blockingPanel: View
    private lateinit var blockingTitle: TextView
    private lateinit var blockingMessage: TextView
    private lateinit var blockingAction: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var guidanceText: TextView
    private lateinit var resultLabel: TextView
    private lateinit var resultValue: TextView
    private lateinit var resultCaption: TextView
    private lateinit var unitMeters: TextView
    private lateinit var unitCentimeters: TextView
    private lateinit var unitFeet: TextView
    private lateinit var undoButton: ImageButton
    private lateinit var addButton: ImageButton
    private lateinit var resetButton: ImageButton

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRequestInFlight = false
            // If granted, onResume() runs next and starts AR.
            if (!granted) {
                permissionDenied = true
                showPermissionDenied()
            }
        }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(R.layout.activity_measure)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        bindViews()
        setupInsets()

        unit = MeasureUnit.fromKey(prefs.getString(KEY_UNIT, null))
        overlayView.unit = unit
        setupControls()
        updateUnitSelector()
        applyState(lastState)
    }

    override fun onResume() {
        super.onResume()

        if (!hasCameraPermission()) {
            if (permissionDenied) {
                showPermissionDenied()
            } else if (!permissionRequestInFlight) {
                permissionRequestInFlight = true
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            return
        }
        startAr()
    }

    override fun onPause() {
        // Stop the GL thread first, then pause the session.
        if (arView.isAttached) arView.onPause()
        session?.pause()
        displayRotationHelper.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ AR startup

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startAr() {
        hideBlocking()
        if (!ensureSession()) return
        val s = session ?: return

        try {
            s.resume()
        } catch (e: CameraNotAvailableException) {
            showBlocking(R.string.camera_busy_title, R.string.camera_busy_message, R.string.retry) { startAr() }
            return
        } catch (e: Exception) {
            Log.e(TAG, "session.resume() failed", e)
            showBlocking(R.string.ar_error_title, R.string.ar_error_message, R.string.retry) { startAr() }
            return
        }

        displayRotationHelper.onResume()
        arView.onResume()
    }

    /** Returns true when a session exists and the renderer is attached. */
    private fun ensureSession(): Boolean {
        if (session != null) return true

        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return false // onResume() runs again after the install screen closes.
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }

            val newSession = Session(this)
            configureSession(newSession)
            session = newSession
            arView.attach(MeasureRenderer(newSession, displayRotationHelper, this))
            return true

        } catch (e: UnavailableUserDeclinedInstallationException) {
            showBlocking(R.string.ar_declined_title, R.string.ar_declined_message, R.string.retry) { retryFromScratch() }
        } catch (e: UnavailableDeviceNotCompatibleException) {
            showBlocking(R.string.ar_unsupported_title, R.string.ar_unsupported_message)
        } catch (e: UnavailableApkTooOldException) {
            showBlocking(R.string.ar_update_title, R.string.ar_update_message, R.string.retry) { retryFromScratch() }
        } catch (e: UnavailableSdkTooOldException) {
            showBlocking(R.string.ar_app_old_title, R.string.ar_app_old_message)
        } catch (e: UnavailableException) {
            Log.e(TAG, "ARCore unavailable", e)
            showBlocking(R.string.ar_error_title, R.string.ar_error_message, R.string.retry) { retryFromScratch() }
        } catch (e: Exception) {
            Log.e(TAG, "Session creation failed", e)
            showBlocking(R.string.ar_error_title, R.string.ar_error_message, R.string.retry) { retryFromScratch() }
        }
        return false
    }

    private fun retryFromScratch() {
        installRequested = false
        startAr()
    }

    private fun configureSession(s: Session) {
        val config = Config(s)
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        // BLOCKING ties the render loop to camera frames instead of spinning on duplicates.
        config.updateMode = Config.UpdateMode.BLOCKING
        config.focusMode = Config.FocusMode.AUTO
        // We draw no lit 3D objects, so skip the cost of light estimation.
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        config.depthMode =
            if (MeasureConfig.ENABLE_DEPTH && s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                Config.DepthMode.AUTOMATIC
            } else {
                Config.DepthMode.DISABLED
            }
        s.configure(config)
    }

    // ------------------------------------------------------------------ views

    private fun bindViews() {
        measureRoot = findViewById(R.id.measureRoot)
        arView = findViewById(R.id.arView)
        overlayView = findViewById(R.id.overlayView)
        topArea = findViewById(R.id.topArea)
        bottomArea = findViewById(R.id.bottomArea)
        blockingPanel = findViewById(R.id.blockingPanel)
        blockingTitle = findViewById(R.id.blockingTitle)
        blockingMessage = findViewById(R.id.blockingMessage)
        blockingAction = findViewById(R.id.blockingAction)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        guidanceText = findViewById(R.id.guidanceText)
        resultLabel = findViewById(R.id.resultLabel)
        resultValue = findViewById(R.id.resultValue)
        resultCaption = findViewById(R.id.resultCaption)
        unitMeters = findViewById(R.id.unitMeters)
        unitCentimeters = findViewById(R.id.unitCentimeters)
        unitFeet = findViewById(R.id.unitFeet)
        undoButton = findViewById(R.id.undoButton)
        addButton = findViewById(R.id.addButton)
        resetButton = findViewById(R.id.resetButton)
    }

    private fun setupInsets() {
        fun pads(v: View) = intArrayOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
        val top = pads(topArea)
        val bottom = pads(bottomArea)
        val panel = pads(blockingPanel)

        ViewCompat.setOnApplyWindowInsetsListener(measureRoot) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topArea.setPadding(top[0] + b.left, top[1] + b.top, top[2] + b.right, top[3])
            bottomArea.setPadding(bottom[0] + b.left, bottom[1], bottom[2] + b.right, bottom[3] + b.bottom)
            blockingPanel.setPadding(panel[0] + b.left, panel[1] + b.top, panel[2] + b.right, panel[3] + b.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(measureRoot)
    }

    private fun setupControls() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.blockingBack).setOnClickListener { finish() }
        blockingAction.setOnClickListener { blockingHandler?.invoke() }

        addButton.setOnClickListener { arView.sendCommand(MeasureCommand.ADD_POINT) }
        undoButton.setOnClickListener { arView.sendCommand(MeasureCommand.UNDO) }
        resetButton.setOnClickListener { arView.sendCommand(MeasureCommand.RESET) }

        unitMeters.setOnClickListener { setUnit(MeasureUnit.METERS) }
        unitCentimeters.setOnClickListener { setUnit(MeasureUnit.CENTIMETERS) }
        unitFeet.setOnClickListener { setUnit(MeasureUnit.FEET_INCHES) }
    }

    private fun setUnit(newUnit: MeasureUnit) {
        unit = newUnit
        prefs.edit().putString(KEY_UNIT, newUnit.prefKey).apply()
        overlayView.unit = newUnit
        updateUnitSelector()
        applyState(lastState)
    }

    private fun updateUnitSelector() {
        val selectedColor = ContextCompat.getColor(this, R.color.ma_accent_dark)
        val normalColor = ContextCompat.getColor(this, R.color.ma_text_secondary)
        listOf(
            unitMeters to MeasureUnit.METERS,
            unitCentimeters to MeasureUnit.CENTIMETERS,
            unitFeet to MeasureUnit.FEET_INCHES
        ).forEach { (view, u) ->
            val selected = u == unit
            view.isSelected = selected
            view.setTextColor(if (selected) selectedColor else normalColor)
        }
    }

    // ------------------------------------------------------------------ renderer callbacks (GL thread)

    override fun onUiState(state: MeasureUiState) {
        runOnUiThread { if (!isFinishing) applyState(state) }
    }

    override fun onOverlay(frame: OverlayFrame) {
        overlayView.submit(frame)
    }

    override fun onEvent(event: MeasureEvent) {
        runOnUiThread { if (!isFinishing) handleEvent(event) }
    }

    // ------------------------------------------------------------------ UI updates (UI thread)

    private fun applyState(state: MeasureUiState) {
        lastState = state

        // Status pill
        val (statusRes, colorRes) = when (state.tracking) {
            TrackingStatus.TRACKING -> R.string.status_ready to R.color.ma_accent
            TrackingStatus.INITIALIZING -> R.string.status_starting to R.color.ma_text_secondary
            TrackingStatus.LIMITED -> R.string.status_limited to R.color.ma_warning
        }
        statusText.setText(statusRes)
        statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))

        // Guidance card
        val guidance = getString(guidanceRes(state.guidance))
        if (guidanceText.text.toString() != guidance) {
            guidanceText.text = guidance
            guidanceText.alpha = 0.35f
            guidanceText.animate().alpha(1f).setDuration(180).start()
        }
        val warning = when (state.guidance) {
            Guidance.MOVE_SLOWER, Guidance.MORE_LIGHT, Guidance.MORE_DETAIL,
            Guidance.CAMERA_BUSY, Guidance.TRACKING_LOST -> true
            else -> false
        }
        guidanceText.setTextColor(
            ContextCompat.getColor(this, if (warning) R.color.ma_warning else R.color.ma_text_primary)
        )

        // Result card
        val live = state.liveDistanceMeters
        val shown = live ?: state.lastDistanceMeters
        resultLabel.setText(if (live != null) R.string.result_label_live else R.string.result_label)
        if (shown == null) {
            resultValue.setText(R.string.result_placeholder)
        } else {
            resultValue.text = buildResultText(shown)
        }
        resultCaption.setText(
            when {
                live != null -> R.string.caption_live
                shown != null -> R.string.caption_estimate
                else -> R.string.caption_empty
            }
        )

        // Buttons
        undoButton.isEnabled = state.canUndo
        resetButton.isEnabled = state.canUndo
        undoButton.alpha = if (state.canUndo) 1f else 0.4f
        resetButton.alpha = if (state.canUndo) 1f else 0.4f
        addButton.alpha = if (state.reticleLocked) 1f else 0.55f
    }

    private fun buildResultText(meters: Float): CharSequence {
        val f = DistanceFormatter.format(meters, unit)
        if (f.unit.isEmpty()) return f.number
        val text = SpannableString("${f.number} ${f.unit}")
        val start = f.number.length
        text.setSpan(RelativeSizeSpan(0.5f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.ma_text_secondary)),
            start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }

    private fun guidanceRes(g: Guidance): Int = when (g) {
        Guidance.STARTING -> R.string.guidance_starting
        Guidance.MOVE_SLOWER -> R.string.guidance_move_slower
        Guidance.MORE_LIGHT -> R.string.guidance_more_light
        Guidance.MORE_DETAIL -> R.string.guidance_more_detail
        Guidance.CAMERA_BUSY -> R.string.guidance_camera_busy
        Guidance.TRACKING_LOST -> R.string.guidance_tracking_lost
        Guidance.SCAN_SURFACE -> R.string.guidance_scan_surface
        Guidance.PLACE_FIRST -> R.string.guidance_place_first
        Guidance.SCAN_SECOND -> R.string.guidance_scan_second
        Guidance.PLACE_SECOND -> R.string.guidance_place_second
        Guidance.COMPLETE -> R.string.guidance_complete
    }

    private fun handleEvent(event: MeasureEvent) {
        when (event) {
            MeasureEvent.POINT_PLACED -> {
                haptic(success = true)
                addButton.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction {
                    addButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
            }
            MeasureEvent.NO_SURFACE -> {
                haptic(success = false)
                val d = resources.displayMetrics.density
                ObjectAnimator.ofFloat(
                    guidanceText, View.TRANSLATION_X,
                    0f, -10f * d, 10f * d, -6f * d, 6f * d, 0f
                ).setDuration(280).start()
            }
        }
    }

    private fun haptic(success: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            if (success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            if (success) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS
        }
        addButton.performHapticFeedback(constant)
    }

    // ------------------------------------------------------------------ blocking panel

    private fun showBlocking(
        titleRes: Int,
        messageRes: Int,
        actionRes: Int? = null,
        action: (() -> Unit)? = null
    ) {
        blockingTitle.setText(titleRes)
        blockingMessage.setText(messageRes)
        blockingHandler = action
        if (actionRes != null && action != null) {
            blockingAction.setText(actionRes)
            blockingAction.visibility = View.VISIBLE
        } else {
            blockingAction.visibility = View.GONE
        }
        blockingPanel.visibility = View.VISIBLE
    }

    private fun hideBlocking() {
        blockingPanel.visibility = View.GONE
    }

    private fun showPermissionDenied() {
        val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        if (canAskAgain) {
            showBlocking(R.string.perm_title, R.string.perm_message, R.string.perm_allow) {
                permissionDenied = false
                if (!permissionRequestInFlight) {
                    permissionRequestInFlight = true
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } else {
            showBlocking(R.string.perm_title, R.string.perm_message, R.string.perm_settings) {
                permissionDenied = false
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "MeasureActivity"
        const val PREFS = "measurear_prefs"
        const val KEY_UNIT = "unit"
    }
}
