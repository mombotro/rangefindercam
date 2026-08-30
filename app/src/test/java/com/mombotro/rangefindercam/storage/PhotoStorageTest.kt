package com.mombotro.rangefindercam.storage

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `save writes a jpeg into a RangefinderCam subfolder of the given directory`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        val saved = PhotoStorage.save(context, bitmap, tempFolder.root)

        assertNotNull("expected save() to return the written file", saved)
        assertTrue(saved!!.exists())
        assertEquals("RangefinderCam", saved.parentFile?.name)
        assertTrue(saved.name.endsWith(".jpg"))
        assertTrue("expected a non-empty JPEG file", saved.length() > 0)
    }

    @Test
    fun `save returns null when the target directory cannot be created`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        // Point at a path that can never become a directory: a plain file
        // sits where PhotoStorage would need to mkdir.
        val blockingFile = File(tempFolder.root, "not-a-directory")
        blockingFile.writeText("blocking")

        val saved = PhotoStorage.save(context, bitmap, blockingFile)

        assertEquals(null, saved)
    }
}
