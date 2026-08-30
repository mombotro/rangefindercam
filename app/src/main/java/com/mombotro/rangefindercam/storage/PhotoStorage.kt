package com.mombotro.rangefindercam.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoStorage {

    private const val FOLDER_NAME = "RangefinderCam"

    /**
     * Saves [bitmap] as a JPEG under a "RangefinderCam" subfolder of
     * [picturesDir] (defaults to the public Pictures directory) and
     * media-scans it so it shows up in the stock Gallery app immediately.
     * Returns the saved file, or null if the write failed (e.g. target
     * directory couldn't be created, or the SD card is full/missing).
     */
    fun save(
        context: Context,
        bitmap: Bitmap,
        picturesDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    ): File? {
        val targetDir = File(picturesDir, FOLDER_NAME)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return null
        }

        val filename = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val targetFile = File(targetDir, filename)

        return try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("image/jpeg"), null)
            targetFile
        } catch (e: Exception) {
            null
        }
    }
}
