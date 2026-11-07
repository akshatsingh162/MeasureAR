package com.akshat.measurear

import android.animation.ValueAnimator
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ar.core.ArCoreApk
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Home screen. The top shows live conditions (heading, altitude, pressure, light); each tile
 * opens its full tool. Below is a grid of field tools. The AR measure tool is only enabled on
 * devices that support ARCore.
 */
class MainActivity : ComponentActivity(), SensorEventListener {

    /** Either [iconRes] (a vector drawable) or [glyph] (a short text) is shown in the tile. */
    private class ToolEntry(
        val titleRes: Int,
        val descRes: Int,
        val iconRes: Int,
        val glyph: String?,
        val needsAr: Boolean,
        val intentFactory: () -> Intent
    )

    private class Tile(val view: View, val value: TextView, val sub: TextView)

    private lateinit var ui: UiKit
    private lateinit var deviceMessage: TextView
    private lateinit var sensorManager: SensorManager
    private val arCards = ArrayList<View>()
    private var arSupported = true

    private lateinit var headingTile: Tile
    private lateinit var altitudeTile: Tile
    private lateinit var pressureTile: Tile
    private lateinit var lightTile: Tile

    private var rotationSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var lightSensor: Sensor? = null

    private val tracker = HeadingTracker()
    private var heading = 0f
    private var haveHeading = false
    private var pressure = 0f
    private var havePressure = false
    private var lux = 0f
    private var haveLux = false
    private var lastRefresh = 0L

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

        ui = UiKit(this)
        deviceMessage = findViewById(R.id.deviceMessage)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        buildDashboard()
        buildTools()

        val bottomBar = findViewById<LinearLayout>(R.id.bottomBar)
        bottomBar.addView(
            ui.segment(listOf("Metric units", "Imperial units"), if (Units.isImperial(this)) 1 else 0) { index ->
                Units.setImperial(this, index == 1)
                refreshTiles()
            },
            0,
            ui.params(bottom = 12)
        )

        refreshTiles()

        if (savedInstanceState == null) {
            playEntrance(
                listOf(
                    findViewById(R.id.brandRow),
                    findViewById(R.id.homeHeading),
                    findViewById(R.id.homeSubtitle),
                    findViewById(R.id.dashContainer),
                    findViewById(R.id.toolsContainer),
                    findViewById(R.id.bottomBar)
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        pressureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        checkArAvailability()
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    // ------------------------------------------------------------------ live conditions

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                heading = tracker.update(event.values)
                haveHeading = true
            }
            Sensor.TYPE_PRESSURE -> {
                val v = event.values[0]
                pressure = if (!havePressure) v else pressure + 0.1f * (v - pressure)
                havePressure = true
                PressureLog.add(this, pressure)
            }
            Sensor.TYPE_LIGHT -> {
                val v = event.values[0]
                lux = if (!haveLux) v else lux + 0.3f * (v - lux)
                haveLux = true
            }
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastRefresh >= 250L) {
            lastRefresh = now
            refreshTiles()
        }
    }

    private fun refreshTiles() {
        val locale = Locale.getDefault()

        if (haveHeading) {
            headingTile.value.text = "${HeadingTracker.cardinal(heading)} ${heading.roundToInt() % 360}°"
            headingTile.sub.text = "Magnetic north"
        } else {
            headingTile.value.text = "—"
            headingTile.sub.text = if (rotationSensor == null) "No compass sensor" else "Reading…"
        }

        if (havePressure) {
            val altitude = SensorManager.getAltitude(Units.altitudeReference(this), pressure).toDouble()
            altitudeTile.value.text = Units.altitude(this, altitude)
            altitudeTile.sub.text = "From barometer"
            pressureTile.value.text = Units.pressure(this, pressure)
            pressureTile.sub.text = PressureLog.label(PressureLog.trendPer3h(this, pressure))
        } else {
            val missing = pressureSensor == null
            altitudeTile.value.text = "—"
            altitudeTile.sub.text = if (missing) "No barometer" else "Reading…"
            pressureTile.value.text = "—"
            pressureTile.sub.text = if (missing) "No barometer" else "Reading…"
        }

        if (haveLux) {
            lightTile.value.text = String.format(locale, "%,d lx", lux.roundToInt())
            lightTile.sub.text = when {
                lux < 10f -> "Dark"
                lux < 50f -> "Dim"
                lux < 500f -> "Indoor light"
                lux < 10000f -> "Bright"
                else -> "Daylight"
            }
        } else {
            lightTile.value.text = "—"
            lightTile.sub.text = if (lightSensor == null) "No light sensor" else "Reading…"
        }
    }

    // ------------------------------------------------------------------ building the screen

    private fun dp(v: Int): Int = ui.dp(v)

    private fun createTile(labelRes: Int, target: Class<*>): Tile {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_tool_card)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(108)
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, target)) }
        }
        card.addView(ui.sectionLabel(getString(labelRes)))
        val value = ui.bigNumber("—", 26f).apply { setPadding(0, dp(8), 0, 0) }
        val sub = ui.text("", 12f, R.color.ma_text_secondary).apply { setPadding(0, dp(4), 0, 0) }
        card.addView(value)
        card.addView(sub)
        return Tile(card, value, sub)
    }

    private fun buildDashboard() {
        val dash = findViewById<LinearLayout>(R.id.dashContainer)
        addSectionHeader(dash, R.string.section_now)

        headingTile = createTile(R.string.tile_heading, CompassActivity::class.java)
        altitudeTile = createTile(R.string.tile_altitude, AltimeterActivity::class.java)
        pressureTile = createTile(R.string.tile_pressure, WeatherActivity::class.java)
        lightTile = createTile(R.string.tile_light, LightMeterActivity::class.java)

        addRow(dash, listOf(headingTile.view, altitudeTile.view))
        addRow(dash, listOf(pressureTile.view, lightTile.view))
    }

    private fun iconTool(title: Int, desc: Int, iconRes: Int, ar: Boolean = false, launch: () -> Intent) =
        ToolEntry(title, desc, iconRes, null, ar, launch)

    private fun glyphTool(title: Int, desc: Int, symbol: String, launch: () -> Intent) =
        ToolEntry(title, desc, 0, symbol, false, launch)

    private fun buildTools() {
        val container = findViewById<LinearLayout>(R.id.toolsContainer)

        val tools = listOf(
            iconTool(R.string.tool_location, R.string.tool_location_desc, R.drawable.ic_tool_location) {
                Intent(this, LocationActivity::class.java)
            },
            iconTool(R.string.tool_sun, R.string.tool_sun_desc, R.drawable.ic_tool_sun) {
                Intent(this, SunActivity::class.java)
            },
            iconTool(R.string.tool_slope, R.string.tool_slope_desc, R.drawable.ic_tool_slope) {
                Intent(this, SlopeActivity::class.java)
            },
            iconTool(R.string.tool_level, R.string.tool_level_desc, R.drawable.ic_tool_level) {
                Intent(this, LevelActivity::class.java)
            },
            iconTool(R.string.tool_flashlight, R.string.tool_flashlight_desc, R.drawable.ic_tool_flashlight) {
                Intent(this, FlashlightActivity::class.java)
            },
            glyphTool(R.string.tool_converter, R.string.tool_converter_desc, "↔") {
                CalculatorActivity.intent(this, "converter")
            },
            iconTool(R.string.tool_distance, R.string.tool_distance_desc, R.drawable.ic_tool_distance, true) {
                MeasureActivity.intent(this, MeasureMode.DISTANCE)
            }
        )

        addSectionHeader(container, R.string.section_field)
        for (pair in tools.chunked(2)) {
            addRow(container, pair.map { createCard(it) })
        }
    }

    private fun addSectionHeader(container: LinearLayout, titleRes: Int) {
        val header = ui.sectionLabel(getString(titleRes))
        header.setPadding(0, dp(24), 0, dp(12))
        container.addView(header)
    }

    private fun addRow(container: LinearLayout, cards: List<View>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cards.forEachIndexed { index, card -> row.addView(card, cardParams(index)) }
        if (cards.size == 1) row.addView(Space(this), cardParams(1))
        container.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        )
    }

    private fun cardParams(index: Int) = LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
    ).apply {
        if (index == 0) marginEnd = dp(6) else marginStart = dp(6)
    }

    private fun createCard(tool: ToolEntry): View {
        val card = layoutInflater.inflate(R.layout.item_tool, null, false)
        val iconView = card.findViewById<ImageView>(R.id.cardIcon)
        val glyphView = card.findViewById<TextView>(R.id.cardGlyph)
        if (tool.glyph != null) {
            iconView.visibility = View.GONE
            glyphView.visibility = View.VISIBLE
            glyphView.text = tool.glyph
        } else {
            iconView.setImageResource(tool.iconRes)
        }
        card.findViewById<TextView>(R.id.cardTitle).setText(tool.titleRes)
        card.findViewById<TextView>(R.id.cardDesc).setText(tool.descRes)
        card.setOnClickListener { open(tool) }
        if (tool.needsAr) arCards.add(card)
        return card
    }

    private fun open(tool: ToolEntry) {
        if (tool.needsAr && !arSupported) {
            deviceMessage.visibility = View.VISIBLE
            return
        }
        startActivity(tool.intentFactory())
    }

    // ------------------------------------------------------------------ AR availability and animation

    private fun checkArAvailability() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        when {
            availability.isUnsupported -> setArSupported(false)
            availability.isTransient -> {
                // Still checking; ask again shortly.
                deviceMessage.postDelayed({ checkArAvailability() }, 300)
            }
            else -> setArSupported(true)
        }
    }

    private fun setArSupported(supported: Boolean) {
        arSupported = supported
        deviceMessage.visibility = if (supported) View.GONE else View.VISIBLE
        for (card in arCards) card.alpha = if (supported) 1f else 0.4f
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
