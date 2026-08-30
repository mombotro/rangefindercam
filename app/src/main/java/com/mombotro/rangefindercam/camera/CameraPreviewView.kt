package com.mombotro.rangefindercam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.util.AttributeSet
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

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            val opened = Camera.open()
            camera = opened
            opened.setDisplayOrientation(90)
            opened.setPreviewDisplay(holder)
            opened.startPreview()
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
        } catch (e: Exception) {
            onCameraError?.invoke("Could not restart preview: ${e.message}")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.apply {
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
            } catch (e: Exception) {
                onError("Could not restart preview after capture: ${e.message}")
            }
        }
    }
}
