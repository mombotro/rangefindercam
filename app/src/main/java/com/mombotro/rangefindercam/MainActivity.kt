package com.mombotro.rangefindercam

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
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
        const val PERMISSION_REQUEST_CODE = 1001
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

        requestPermissionsIfNeeded()
    }

    /**
     * On a real device running API23+ (the runtime permission model
     * applies to any device actually running API23+, not based on this
     * app's own minSdk 16 - which is why this was never needed while
     * testing on the API16 LG Intuition, but is required on the Note 9,
     * which runs Android 10 / API29), CAMERA and WRITE_EXTERNAL_STORAGE
     * are dangerous permissions requiring an explicit runtime grant even
     * though they're already declared in the manifest. Below API23 (or
     * once already granted) this is a no-op.
     */
    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val missing = REQUIRED_PERMISSIONS.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // The camera's initial open attempt (triggered automatically
            // when the SurfaceView's surface was created, before this
            // permission prompt was ever answered) already failed with a
            // permission error and won't retry on its own - it needs an
            // explicit nudge now that permission actually exists.
            cameraPreview.retryAfterPermissionGranted()
        } else {
            Toast.makeText(this, "Camera and storage permission are required", Toast.LENGTH_LONG).show()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest declares configChanges for orientation/screenSize so
        // the Activity survives rotation instead of being recreated (which
        // would mean reopening the camera hardware on every turn - slow on
        // this device). The rest of the layout re-wraps on its own; only
        // the camera's rotation needs an explicit recompute.
        cameraPreview.updateOrientation()
    }

    override fun onDestroy() {
        blinkHandler.removeCallbacks(blinkRunnable)
        super.onDestroy()
    }
}
