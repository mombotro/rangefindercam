package com.mombotro.rangefindercam

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.mombotro.rangefindercam.camera.CameraPreviewView
import com.mombotro.rangefindercam.filters.Look
import com.mombotro.rangefindercam.filters.PhotoFilters
import com.mombotro.rangefindercam.storage.PhotoStorage

class MainActivity : Activity() {

    private companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private lateinit var cameraPreview: CameraPreviewView
    private lateinit var grainLabel: TextView

    private var selectedLook = Look.BLACK_AND_WHITE
    private var grainIntensity = PhotoFilters.DEFAULT_GRAIN_INTENSITY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        cameraPreview.onCameraError = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        grainLabel = findViewById(R.id.grainLabel)
        grainLabel.text = "Grain: $grainIntensity"
        findViewById<SeekBar>(R.id.grainSlider).apply {
            max = 100
            progress = grainIntensity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    grainIntensity = progress
                    grainLabel.text = "Grain: $progress"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
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
                    // Runs on CameraPreviewView's background thread, not the
                    // UI thread - filtering and saving happen here off-thread
                    // too, only the final Toast needs to hop back to the UI
                    // thread.
                    val filtered = PhotoFilters.apply(bitmap, selectedLook, grainIntensity)
                    val saved = PhotoStorage.save(this, filtered)
                    val message = if (saved == null) "Could not save photo" else "Saved"
                    runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                },
                onError = { message ->
                    runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
                }
            )
        }

        findViewById<Button>(R.id.galleryButton).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.dir/image"))
            } catch (e: Exception) {
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
            }
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest declares configChanges for orientation/screenSize so
        // the Activity survives rotation instead of being recreated (which
        // would mean reopening the camera hardware on every turn - slow on
        // this device). The rest of the layout re-wraps on its own; only
        // the camera's rotation needs an explicit recompute.
        cameraPreview.updateOrientation()
    }
}
