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
import com.mombotro.rangefindercam.camera.RotaryDialView
import com.mombotro.rangefindercam.filters.Look
import com.mombotro.rangefindercam.filters.PhotoFilters
import com.mombotro.rangefindercam.storage.PhotoStorage

class MainActivity : Activity() {

    private companion object {
        const val ISO_AUTO = "auto"
        // Fallback list used only if the live query (supportedIsoValues())
        // comes back empty - e.g. entered Manual mode before the camera
        // finished opening, or this device's HAL doesn't expose the
        // vendor iso-values key at all. Matches what this exact hardware
        // reports when it IS present.
        val FALLBACK_ISO_VALUES = listOf("auto", "100", "200", "400", "800", "1600")
        const val METER_BLINK_INTERVAL_MS = 400L
    }

    private lateinit var cameraPreview: CameraPreviewView
    private lateinit var modeToggle: TextView
    private lateinit var manualControlsRow: View
    private lateinit var isoLabel: TextView
    private lateinit var evLabel: TextView
    private lateinit var isoDial: RotaryDialView
    private lateinit var evDial: RotaryDialView
    private lateinit var meterUnder: TextView
    private lateinit var meterOver: TextView

    private var selectedLook = Look.BLACK_AND_WHITE
    private var isManualMode = false
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
        isoLabel = findViewById(R.id.isoLabel)
        evLabel = findViewById(R.id.evLabel)
        isoDial = findViewById(R.id.isoDial)
        evDial = findViewById(R.id.evDial)
        meterUnder = findViewById(R.id.meterUnder)
        meterOver = findViewById(R.id.meterOver)

        modeToggle.setOnClickListener { toggleMode() }

        isoDial.setOnValueChangedListener { _, value ->
            isoLabel.text = "ISO: ${value.uppercase()}"
            cameraPreview.setIso(value)
        }
        evDial.setOnValueChangedListener { _, value ->
            evLabel.text = "EV: $value"
            cameraPreview.setExposureCompensation(value.toInt())
        }

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

            val isoValues = cameraPreview.supportedIsoValues().ifEmpty { FALLBACK_ISO_VALUES }
            val isoStartIndex = isoValues.indexOf(ISO_AUTO).let { if (it < 0) 0 else it }
            isoDial.setValues(isoValues, isoStartIndex)
            isoLabel.text = "ISO: ${isoValues[isoStartIndex].uppercase()}"
            cameraPreview.setIso(isoValues[isoStartIndex])

            val min = cameraPreview.minExposureCompensation()
            val max = cameraPreview.maxExposureCompensation()
            val evValues = if (max > min) (min..max).map { it.toString() } else listOf("0")
            val evStartIndex = evValues.indexOf("0").let { if (it < 0) 0 else it }
            evDial.setValues(evValues, evStartIndex)
            evLabel.text = "EV: ${evValues[evStartIndex]}"
            cameraPreview.setExposureCompensation(evValues[evStartIndex].toInt())

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
