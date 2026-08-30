package com.mombotro.rangefindercam

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.mombotro.rangefindercam.camera.CameraPreviewView
import com.mombotro.rangefindercam.camera.ExposureMeter
import com.mombotro.rangefindercam.camera.MeterState
import com.mombotro.rangefindercam.filters.Look
import com.mombotro.rangefindercam.filters.PhotoFilters
import com.mombotro.rangefindercam.storage.PhotoStorage

class MainActivity : Activity() {

    private companion object {
        const val ISO_AUTO = "auto"
        // Fallback list used only if the live query (supportedIsoValues())
        // comes back empty - e.g. tapped before the camera finished opening,
        // or this device's HAL doesn't expose the vendor iso-values key at
        // all. Matches what this exact hardware reports when it IS present.
        val FALLBACK_ISO_VALUES = listOf("auto", "100", "200", "400", "800", "1600")
        const val METER_BLINK_INTERVAL_MS = 400L
    }

    private lateinit var cameraPreview: CameraPreviewView
    private lateinit var modeToggle: TextView
    private lateinit var manualControlsRow: View
    private lateinit var isoButton: TextView
    private lateinit var evButton: TextView
    private lateinit var meterUnder: TextView
    private lateinit var meterOver: TextView

    private var selectedLook = Look.BLACK_AND_WHITE
    private var isManualMode = false
    private var currentIso = ISO_AUTO
    private var currentEv = 0
    private var currentMeterState = MeterState.CORRECT

    private val blinkHandler = Handler(Looper.getMainLooper())
    private var blinkOn = true
    private val blinkRunnable = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            updateMeterVisibility()
            blinkHandler.postDelayed(this, METER_BLINK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        cameraPreview.onCameraError = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        modeToggle = findViewById(R.id.modeToggle)
        manualControlsRow = findViewById(R.id.manualControlsRow)
        isoButton = findViewById(R.id.isoButton)
        evButton = findViewById(R.id.evButton)
        meterUnder = findViewById(R.id.meterUnder)
        meterOver = findViewById(R.id.meterOver)

        modeToggle.setOnClickListener { toggleMode() }
        isoButton.setOnClickListener { cycleIso() }
        evButton.setOnClickListener { cycleExposureCompensation() }

        findViewById<RadioGroup>(R.id.lookChips).setOnCheckedChangeListener { _, checkedId ->
            selectedLook = when (checkedId) {
                R.id.chipSepia -> Look.SEPIA
                R.id.chipGrain -> Look.GRAIN
                else -> Look.BLACK_AND_WHITE
            }
        }

        findViewById<Button>(R.id.shutterButton).setOnClickListener {
            cameraPreview.capture(
                onCaptured = { bitmap ->
                    val filtered = PhotoFilters.apply(bitmap, selectedLook)
                    val saved = PhotoStorage.save(this, filtered)
                    val message = if (saved == null) "Could not save photo" else "Saved"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                },
                onError = { message ->
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun toggleMode() {
        isManualMode = !isManualMode
        if (isManualMode) {
            modeToggle.text = getString(R.string.mode_manual)
            manualControlsRow.visibility = View.VISIBLE
            cameraPreview.setIso(currentIso)
            cameraPreview.setExposureCompensation(currentEv)
            cameraPreview.setOnAverageLumaListener { luma ->
                currentMeterState = ExposureMeter.evaluate(luma)
            }
            blinkHandler.post(blinkRunnable)
        } else {
            modeToggle.text = getString(R.string.mode_auto)
            manualControlsRow.visibility = View.GONE
            blinkHandler.removeCallbacks(blinkRunnable)
            cameraPreview.setOnAverageLumaListener(null)
            // Hand exposure fully back to the camera's own auto-exposure -
            // manual mode's ISO/EV choices shouldn't linger once you switch
            // back to Auto.
            cameraPreview.setIso(ISO_AUTO)
            cameraPreview.setExposureCompensation(0)
        }
    }

    private fun cycleIso() {
        val values = cameraPreview.supportedIsoValues().ifEmpty { FALLBACK_ISO_VALUES }
        val currentIndex = values.indexOf(currentIso).let { if (it < 0) 0 else it }
        currentIso = values[(currentIndex + 1) % values.size]
        isoButton.text = "ISO: ${currentIso.uppercase()}"
        cameraPreview.setIso(currentIso)
    }

    private fun cycleExposureCompensation() {
        val min = cameraPreview.minExposureCompensation()
        val max = cameraPreview.maxExposureCompensation()
        if (max <= min) return
        currentEv = if (currentEv >= max) min else currentEv + 1
        evButton.text = "EV: $currentEv"
        cameraPreview.setExposureCompensation(currentEv)
    }

    private fun updateMeterVisibility() {
        val showUnder = when (currentMeterState) {
            MeterState.UNDER, MeterState.CORRECT -> blinkOn
            MeterState.OVER -> false
        }
        val showOver = when (currentMeterState) {
            MeterState.OVER, MeterState.CORRECT -> blinkOn
            MeterState.UNDER -> false
        }
        meterUnder.visibility = if (showUnder) View.VISIBLE else View.INVISIBLE
        meterOver.visibility = if (showOver) View.VISIBLE else View.INVISIBLE
    }

    override fun onDestroy() {
        blinkHandler.removeCallbacks(blinkRunnable)
        super.onDestroy()
    }
}
