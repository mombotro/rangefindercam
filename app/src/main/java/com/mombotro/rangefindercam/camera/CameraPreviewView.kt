package com.mombotro.rangefindercam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.Camera
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Wraps the classic (Camera1) camera API in a SurfaceView. Camera2 isn't
 * available until API21; this app's minSdk is 16, so Camera1 is the only
 * option. The preview is always plain/unfiltered - filters apply once,
 * after capture, in PhotoFilters.
 */
class CameraPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var camera: Camera? = null
    var onCameraError: ((String) -> Unit)? = null

    private var onAverageLuma: ((Int) -> Unit)? = null
    private var lastLumaSampleMs = 0L
    private val lumaSampleIntervalMs = 500L

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            val opened = Camera.open()
            camera = opened
            opened.setDisplayOrientation(90)
            // setDisplayOrientation only rotates the live preview surface -
            // it has no effect on the saved JPEG, which needs its own
            // rotation set via Parameters (written into the file's EXIF
            // orientation tag) or it saves sideways regardless of how the
            // preview looked on screen.
            val parameters = opened.parameters
            parameters.setRotation(90)
            opened.parameters = parameters
            opened.setPreviewDisplay(holder)
            opened.startPreview()
            applyPreviewCallback()
        } catch (e: Exception) {
            onCameraError?.invoke("Could not open camera: ${e.message}")
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

    /** Takes a photo with the current preview frame. Restarts the preview
     * afterward either way, so the viewfinder keeps working for the next shot. */
    fun capture(onCaptured: (Bitmap) -> Unit, onError: (String) -> Unit) {
        val opened = camera
        if (opened == null) {
            onError("Camera not ready")
            return
        }
        opened.takePicture(null, null) { data, _ ->
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap == null) {
                onError("Could not decode photo")
            } else {
                onCaptured(bitmap)
            }
            try {
                opened.startPreview()
                applyPreviewCallback()
            } catch (e: Exception) {
                onError("Could not restart preview after capture: ${e.message}")
            }
        }
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

    /** Maps a tap in view coordinates to Camera1's focus-area coordinate
     * space, which is always -1000..1000 on both axes regardless of preview
     * size. The view is rotated 90 degrees relative to the sensor (this is a
     * portrait back-camera preview via setDisplayOrientation(90)), so screen
     * X maps to sensor Y and screen Y maps to sensor X. */
    private fun calculateTapArea(x: Float, y: Float): Rect {
        val areaSize = 100
        val focusX = (y / height * 2000 - 1000)
        val focusY = -(x / width * 2000 - 1000)
        val left = (focusX - areaSize / 2f).coerceIn(-1000f, 1000f - areaSize)
        val top = (focusY - areaSize / 2f).coerceIn(-1000f, 1000f - areaSize)
        return Rect(left.toInt(), top.toInt(), (left + areaSize).toInt(), (top + areaSize).toInt())
    }
}
