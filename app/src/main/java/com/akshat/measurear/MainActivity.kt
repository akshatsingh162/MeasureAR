package com.akshat.measurear

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ar.core.ArCoreApk

class MainActivity : ComponentActivity() {

    private lateinit var startButton: TextView
    private lateinit var deviceMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val root: View = findViewById(R.id.homeRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        startButton = findViewById(R.id.startButton)
        deviceMessage = findViewById(R.id.deviceMessage)
        startButton.setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }

        if (savedInstanceState == null) {
            playEntrance(
                listOf(
                    findViewById(R.id.brandRow),
                    findViewById(R.id.homeHeading),
                    findViewById(R.id.homeSubtitle),
                    findViewById(R.id.demoView),
                    findViewById(R.id.stepsBlock),
                    findViewById(R.id.bottomBar)
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        checkArAvailability()
    }

    private fun checkArAvailability() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        when {
            availability.isUnsupported -> {
                startButton.isEnabled = false
                startButton.alpha = 0.4f
                deviceMessage.visibility = View.VISIBLE
            }
            availability.isTransient -> {
                // Still checking; ask again shortly.
                startButton.postDelayed({ checkArAvailability() }, 300)
            }
            else -> {
                startButton.isEnabled = true
                startButton.alpha = 1f
                deviceMessage.visibility = View.GONE
            }
        }
    }

    private fun playEntrance(views: List<View>) {
        val animationsEnabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        if (!animationsEnabled) return

        val offset = 16f * resources.displayMetrics.density
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offset
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L * index)
                .setDuration(380)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
