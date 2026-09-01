package com.mombotro.rangefindercam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Camera
import android.media.ExifInterface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps the classic (Camera1) camera API in a SurfaceView. Camera2 isn't
 * available until API21; this app's minSdk is 16, so Camera1 is the only
 * option. The preview is always plain/unfiltered - filters apply once,
 * after capture, in PhotoFilters. Supports live rotation (the Activity
 * declares configChanges for orientation so it isn't recreated, and calls
 * updateOrientation() itself on each turn) rather than being locked to one
 * orientation.
 */
class CameraPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private companion object {
        // Camera.open() with no argument opens this id - the first
        // back-facing camera - so CameraInfo is looked up for the same id
        // to keep the rotation math and the actually-open camera in sync.
        const val CAMERA_ID = 0
    }

    private var camera: Camera? = null
    var onCameraError: ((String) -> Unit)? = null

    private var onAverageLuma: ((Int) -> Unit)? = null
    private var lastLumaSampleMs = 0L
    private val lumaSampleIntervalMs = 500L

    private val cameraInfo = Camera.CameraInfo().also {
        Camera.getCameraInfo(CAMERA_ID, it)
    }

    // The rotation actually applied to both the live preview and the saved
    // JPEG right now - recomputed by updateOrientation(), cached here so
    // the focus-area math (which needs the same value) doesn't have to
    // requery the display rotation itself.
    private var appliedRotationDegrees = 0

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        openCamera(holder)
    }

    /**
     * Opens the camera and starts the preview against [holder]. Broken out
     * from surfaceCreated() so it can also be called from retryAfterPermissionGranted()
     * - on a real API23+ device (targetSdk 29 means the runtime permission
     * model applies on any device that's actually running API23+, e.g. the
     * Note 9, regardless of this app's own minSdk 16), the surface is
     * usually already created by the time the user answers the runtime
     * CAMERA permission prompt, so the original surfaceCreated() call has
     * already failed with a permission error and won't fire again on its
     * own - the caller has to explicitly retry once permission is granted.
     */
    private fun openCamera(holder: SurfaceHolder) {
        try {
            val opened = Camera.open(CAMERA_ID)
            camera = opened
            opened.setPreviewDisplay(holder)
            opened.startPreview()
            applyPreviewCallback()
            updateOrientation()
        } catch (e: Exception) {
            onCameraError?.invoke("Could not open camera: ${e.message}")
        }
    }

    /** Call after the user grants the CAMERA runtime permission on a real
     * API23+ device, if the initial open attempt failed because it wasn't
     * granted yet. No-op if the camera is already open. */
    fun retryAfterPermissionGranted() {
        if (camera == null) {
            openCamera(holder)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val opened = camera ?: return
        try {
            opened.stopPreview()
            opened.setPreviewDisplay(holder)
            opened.startPreview()
            applyPreviewCallback()
        } catch (e: Exception) {
            onCameraError?.invoke("Could not restart preview: ${e.message}")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.apply {
            setPreviewCallback(null)
            stopPreview()
            release()
        }
        camera = null
    }

    /**
     * Recomputes and applies the correct preview/capture rotation for the
     * device's current physical orientation. Called once right after the
     * camera opens, and again by MainActivity.onConfigurationChanged() on
     * every subsequent rotation (the Activity declares configChanges for
     * orientation specifically so it survives rotation instead of being
     * recreated, which would mean reopening the camera hardware - slow and
     * janky, especially on this old device - on every turn).
     *
     * This is the standard Android Camera1 rotation formula (the same shape
     * published in Android's own camera documentation and used across the
     * ecosystem): combine the sensor's fixed physical mounting angle
     * (CameraInfo.orientation) with the display's current rotation relative
     * to the device's natural orientation. The earlier portrait-only
     * version of this hardcoded both values to 90 degrees, which only
     * happened to be correct for the one orientation it was ever tested in.
     */
    fun updateOrientation() {
        val opened = camera ?: return
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val displayRotation = windowManager.defaultDisplay.rotation
        val degrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        val result = if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            val raw = (cameraInfo.orientation + degrees) % 360
            (360 - raw) % 360 // compensate for front-camera mirroring
        } else {
            (cameraInfo.orientation - degrees + 360) % 360
        }
        appliedRotationDegrees = result

        try {
            opened.setDisplayOrientation(result)
            // setDisplayOrientation only rotates the live preview surface -
            // it has no effect on the saved JPEG, which needs its own
            // rotation set via Parameters (written into the file's EXIF
            // orientation tag, or physically rotated by this HAL - either
            // way it saves sideways without this) or it saves in whatever
            // orientation the sensor is physically mounted at regardless of
            // how the preview looked on screen.
            val parameters = opened.parameters
            parameters.setRotation(result)
            opened.parameters = parameters
        } catch (e: Exception) {
            onCameraError?.invoke("Could not set rotation: ${e.message}")
        }
    }

    /** Takes a photo with the current preview frame. Restarts the preview
     * afterward either way, so the viewfinder keeps working for the next shot. */
    fun capture(onCaptured: (Bitmap) -> Unit, onError: (String) -> Unit) {
        val opened = camera
        if (opened == null) {
            onError("Camera not ready")
            return
        }
        opened.takePicture(null, null) { data, _ ->
            val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (decoded == null) {
                onError("Could not decode photo")
            } else {
                onCaptured(normalizeOrientation(data, decoded))
            }
            try {
                opened.startPreview()
                applyPreviewCallback()
            } catch (e: Exception) {
                onError("Could not restart preview after capture: ${e.message}")
            }
        }
    }

    /**
     * Camera1 HALs disagree about what Parameters.setRotation() actually
     * does to a captured JPEG: this device's LG HAL physically rotates the
     * pixel buffer to match, writing no meaningful EXIF orientation tag -
     * but the Note 9's Samsung HAL does the opposite, leaving pixels in the
     * sensor's native orientation and only recording the rotation in the
     * EXIF tag instead. BitmapFactory.decodeByteArray() ignores EXIF
     * entirely, so on the Note 9 the decoded bitmap came out sideways -
     * looked fine in a raw pixel viewer that also ignores EXIF, but wrong
     * in any real gallery app that respects it, and our own save path
     * re-encodes the bitmap from scratch anyway (dropping the original
     * EXIF along with it), so a device that relied on the tag alone lost
     * the correction entirely. Reading the tag here and applying it to the
     * pixels directly makes the result correct either way, regardless of
     * which behavior a given HAL chose.
     */
    private fun normalizeOrientation(data: ByteArray, bitmap: Bitmap): Bitmap {
        val degrees = try {
            val tempFile = File.createTempFile("capture", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(data) }
            val orientation = ExifInterface(tempFile.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            tempFile.delete()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
        if (degrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Every supported ISO value this hardware's Camera1 vendor parameters
     * report (e.g. "auto", "100", "200", ...), or an empty list if this
     * device doesn't expose the (unofficial, vendor-specific) iso-values key. */
    fun supportedIsoValues(): List<String> {
        val raw = camera?.parameters?.get("iso-values") ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Sets ISO via the vendor-specific "iso" Camera1 parameter key - there's
     * no standardized ISO control in this API, only what individual camera
     * HALs choose to expose this way. */
    fun setIso(value: String) {
        val opened = camera ?: return
        try {
            val parameters = opened.parameters
            parameters.set("iso", value)
            opened.parameters = parameters
        } catch (e: Exception) {
            onCameraError?.invoke("Could not set ISO: ${e.message}")
        }
    }

    fun minExposureCompensation(): Int = camera?.parameters?.minExposureCompensation ?: 0

    fun maxExposureCompensation(): Int = camera?.parameters?.maxExposureCompensation ?: 0

    fun setExposureCompensation(value: Int) {
        val opened = camera ?: return
        try {
            val parameters = opened.parameters
            parameters.exposureCompensation = value
            opened.parameters = parameters
        } catch (e: Exception) {
            onCameraError?.invoke("Could not set exposure compensation: ${e.message}")
        }
    }

    /** Reports a throttled (~2/sec) average luma sample from the live
     * preview, for the exposure meter. Pass null to stop sampling (skips
     * needless per-frame work when the meter isn't shown, e.g. in Auto mode). */
    fun setOnAverageLumaListener(listener: ((Int) -> Unit)?) {
        onAverageLuma = listener
        applyPreviewCallback()
    }

    private fun applyPreviewCallback() {
        val opened = camera ?: return
        if (onAverageLuma == null) {
            opened.setPreviewCallback(null)
            return
        }
        opened.setPreviewCallback { data, cam ->
            val now = System.currentTimeMillis()
            if (now - lastLumaSampleMs < lumaSampleIntervalMs) return@setPreviewCallback
            lastLumaSampleMs = now

            val size = cam.parameters.previewSize
            val lumaLength = size.width * size.height
            if (data.size < lumaLength) return@setPreviewCallback

            // NV21 preview format: the first width*height bytes are the Y
            // (luma) plane, one byte per pixel - the UV chroma bytes that
            // follow don't represent brightness, so only the Y plane is
            // summed. Sampling every 8th byte instead of every byte is
            // plenty for a rough brightness average and keeps this cheap
            // enough to run on this old CPU every 500ms without competing
            // with the UI thread.
            var sum = 0L
            var count = 0
            var i = 0
            while (i < lumaLength) {
                sum += data[i].toInt() and 0xFF
                count++
                i += 8
            }
            if (count > 0) {
                onAverageLuma?.invoke((sum / count).toInt())
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            focusAt(event.x, event.y)
        }
        return true
    }

    private fun focusAt(viewX: Float, viewY: Float) {
        val opened = camera ?: return
        try {
            opened.cancelAutoFocus()
            val parameters = opened.parameters
            if (parameters.maxNumFocusAreas > 0) {
                val focusRect = calculateTapArea(viewX, viewY)
                val areas = listOf(Camera.Area(focusRect, 1000))
                parameters.focusAreas = areas
                if (parameters.maxNumMeteringAreas > 0) {
                    parameters.meteringAreas = areas
                }
                opened.parameters = parameters
            }
            // Even with no focus-area support, a plain untargeted autoFocus()
            // still re-triggers the hardware's focus routine, so tapping
            // does something useful either way.
            opened.autoFocus { _, _ -> }
        } catch (e: Exception) {
            onCameraError?.invoke("Could not focus: ${e.message}")
        }
    }

    /**
     * Maps a tap in view coordinates to Camera1's focus-area coordinate
     * space, which is always -1000..1000 on both axes regardless of preview
     * size or rotation. Builds the same forward transform the driver
     * conceptually applies to go from its own coordinate space to what's
     * shown on screen (mirror for a front camera, then rotate by the
     * currently-applied display orientation, then scale/translate into view
     * pixels) and inverts it, so this works at any of the four rotations
     * updateOrientation() can produce - not just the one this was originally
     * written for.
     */
    private fun calculateTapArea(x: Float, y: Float): Rect {
        val areaSize = 100
        val matrix = Matrix()
        matrix.setScale(
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) -1f else 1f,
            1f
        )
        matrix.postRotate(appliedRotationDegrees.toFloat())
        matrix.postScale(width / 2000f, height / 2000f)
        matrix.postTranslate(width / 2f, height / 2f)
        matrix.invert(matrix)

        val points = floatArrayOf(x, y)
        matrix.mapPoints(points)
        val focusX = points[0]
        val focusY = points[1]

        val left = (focusX - areaSize / 2f).coerceIn(-1000f, 1000f - areaSize)
        val top = (focusY - areaSize / 2f).coerceIn(-1000f, 1000f - areaSize)
        return Rect(left.toInt(), top.toInt(), (left + areaSize).toInt(), (top + areaSize).toInt())
    }
}
