# RangefinderCam Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A standalone Android camera app for the LG Intuition (API16) that lets you pick an arty look (high-contrast B&W / sepia-faded / heavy grain) before shooting, bakes it into the photo on capture, and saves it where the stock Gallery app can see it.

**Architecture:** Classic `android.hardware.Camera` (Camera1) preview in a `SurfaceView`, plain/unfiltered live view. A bottom overlay bar holds 3 look chips (radio-button group) and a shutter button. On shutter tap, the captured JPEG is decoded to a `Bitmap`, run through a one-shot filter transform (`ColorMatrix` for B&W/sepia, a per-pixel noise pass for grain), then saved to public `Pictures/RangefinderCam/` and media-scanned.

**Tech Stack:** Kotlin, Gradle (AGP 8.5.0, Kotlin 1.9.24, Gradle 8.7 — matching the known-working setup from the `chikins` project on this same machine), JUnit 4.13.2 + Robolectric 4.13 for the filter/storage unit tests (both touch `android.graphics.Bitmap` / `android.content.Context`, which need Robolectric's shadow framework to run on the JVM).

**Spec:** `docs/superpowers/specs/2026-08-29-rangefindercam-design.md`

## Global Constraints

- `minSdk` must be 16 (target device is Android 4.1.2, API16) — no Camera2, no runtime permission requests, no APIs above API16 without a guard.
- Package name: `com.mombotro.rangefindercam`.
- No live-filtered viewfinder — the preview is always the plain camera feed; filters apply once, after capture, on the decoded `Bitmap`. (This device's GPU already hung a modern GL app earlier this session — a live shader pipeline is out of scope.)
- Photos save to `/storage/sdcard0/Pictures/RangefinderCam/<timestamp>.jpg` and must be media-scanned so they appear in the stock Gallery app.
- Exactly one look chip selected at a time; default is high-contrast B&W.

---

### Task 1: Project scaffold

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/mipmap/ic_launcher.png`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/java/com/mombotro/rangefindercam/MainActivity.kt`
- Create: `.gitignore`

**Interfaces:**
- Produces: a buildable, installable app with a blank `MainActivity` (no camera/filter logic yet — later tasks wire those in). Package `com.mombotro.rangefindercam`, `R` class with `R.layout.activity_main`, `R.string.app_name`.

- [ ] **Step 1: Create the Gradle project files**

`build.gradle`:
```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.0'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

`settings.gradle`:
```groovy
rootProject.name = "RangefinderCam"
include ':app'
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
```

`gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

`.gitignore`:
```
.gradle/
build/
app/build/
local.properties
.idea/
*.iml
```

- [ ] **Step 2: Generate the Gradle wrapper jar**

Run: `gradle wrapper --gradle-version 8.7` (uses a system Gradle install to bootstrap the wrapper jar/script, matching the `chikins` project's setup).
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`.

- [ ] **Step 3: Create the app module build file**

`app/build.gradle`:
```groovy
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

android {
    namespace 'com.mombotro.rangefindercam'
    compileSdk 35

    defaultConfig {
        applicationId "com.mombotro.rangefindercam"
        minSdk 16
        targetSdk 35
        versionCode 1
        versionName "0.1"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }
}

dependencies {
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.24'
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.13'
    testImplementation 'androidx.test:core:1.6.1'
}
```

- [ ] **Step 4: Create the manifest**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

- [ ] **Step 5: Create strings and a minimal launcher icon**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">RangefinderCam</string>
    <string name="look_black_and_white">B&amp;W</string>
    <string name="look_sepia">Sepia</string>
    <string name="look_grain">Grain</string>
</resources>
```

Run:
```bash
mkdir -p app/src/main/res/mipmap
magick -size 192x192 xc:black -fill white -draw "circle 96,96 96,40" app/src/main/res/mipmap/ic_launcher.png
```
Expected: a black square with a white ring (aperture-style), placed in the density-less `mipmap/` folder so it's used as the fallback icon at every density — good enough for a personal-use app icon.

- [ ] **Step 6: Create a minimal layout and MainActivity**

`app/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:textColor="#FFFFFF"
        android:text="RangefinderCam" />

</FrameLayout>
```

`app/src/main/java/com/mombotro/rangefindercam/MainActivity.kt`:
```kotlin
package com.mombotro.rangefindercam

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

- [ ] **Step 7: Build and verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 8: Commit**

```bash
git add build.gradle settings.gradle gradle.properties gradle gradlew gradlew.bat .gitignore app
git commit -m "Scaffold RangefinderCam project"
```

---

### Task 2: PhotoFilters — high-contrast black & white

**Files:**
- Create: `app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt`
- Test: `app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt`

**Interfaces:**
- Produces: `enum class Look { BLACK_AND_WHITE, SEPIA, GRAIN }` and `object PhotoFilters { fun applyBlackAndWhite(source: Bitmap): Bitmap }` in package `com.mombotro.rangefindercam.filters`. Later tasks (3, 4) add `applySepia`, `applyGrain`, and `apply(source, look)` to this same object.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt`:
```kotlin
package com.mombotro.rangefindercam.filters

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoFiltersTest {

    private fun coloredBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    @Test
    fun `black and white output has equal RGB channels per pixel`() {
        val source = coloredBitmap(Color.rgb(200, 50, 100))
        val result = PhotoFilters.applyBlackAndWhite(source)

        val pixel = result.getPixel(0, 0)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))
    }

    @Test
    fun `black and white boosts contrast versus plain desaturation`() {
        // A mid-gray input desaturates to itself; the contrast boost should
        // push a bright input brighter and a dark input darker than a plain
        // desaturate would, proving the contrast step actually ran.
        val brightSource = coloredBitmap(Color.rgb(200, 200, 200))
        val result = PhotoFilters.applyBlackAndWhite(brightSource)
        val resultBrightness = Color.red(result.getPixel(0, 0))

        assertTrue("expected contrast-boosted brightness > plain input (200), was $resultBrightness",
            resultBrightness > 200)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.filters.PhotoFiltersTest"`
Expected: FAIL — `PhotoFilters` / `applyBlackAndWhite` unresolved reference (file doesn't exist yet).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt`:
```kotlin
package com.mombotro.rangefindercam.filters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class Look {
    BLACK_AND_WHITE,
    SEPIA,
    GRAIN
}

object PhotoFilters {

    fun applyBlackAndWhite(source: Bitmap): Bitmap {
        val desaturate = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.6f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        desaturate.postConcat(contrastMatrix)
        return drawWithMatrix(source, desaturate)
    }

    internal fun drawWithMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.filters.PhotoFiltersTest"`
Expected: PASS (both tests). If Robolectric's Canvas shadow doesn't apply the `ColorMatrixColorFilter` (a known risk with legacy Robolectric graphics shadows), the first test will fail with unequal R/G/B — if so, add `@GraphicsMode(GraphicsMode.NATIVE)` from `org.robolectric.annotation.GraphicsMode` to the test class to force Robolectric's real-pixel native rendering path, then rerun.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt
git commit -m "Add high-contrast black & white filter"
```

---

### Task 3: PhotoFilters — sepia/faded

**Files:**
- Modify: `app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt`
- Modify: `app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt`

**Interfaces:**
- Consumes: `PhotoFilters.drawWithMatrix(source, matrix)` from Task 2.
- Produces: `PhotoFilters.applySepia(source: Bitmap): Bitmap`.

- [ ] **Step 1: Write the failing test**

Add to `PhotoFiltersTest.kt`:
```kotlin
    @Test
    fun `sepia output has a warm tint (red channel exceeds blue)`() {
        val source = coloredBitmap(Color.rgb(128, 128, 128))
        val result = PhotoFilters.applySepia(source)

        val pixel = result.getPixel(0, 0)
        assertTrue("expected red > blue for a sepia tint, red=${Color.red(pixel)} blue=${Color.blue(pixel)}",
            Color.red(pixel) > Color.blue(pixel))
    }

    @Test
    fun `sepia reduces dynamic range for a faded look`() {
        val brightSource = coloredBitmap(Color.rgb(255, 255, 255))
        val result = PhotoFilters.applySepia(brightSource)
        val resultBrightness = Color.red(result.getPixel(0, 0))

        assertTrue("expected faded white < 255, was $resultBrightness", resultBrightness < 255)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.filters.PhotoFiltersTest"`
Expected: FAIL — `applySepia` unresolved reference.

- [ ] **Step 3: Write the implementation**

Add to `PhotoFilters.kt` (inside the `object PhotoFilters { ... }` block, after `applyBlackAndWhite`):
```kotlin
    fun applySepia(source: Bitmap): Bitmap {
        val sepia = ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        // Faded look: compress the dynamic range and lift the floor slightly,
        // so pure white lands below 255 and pure black lands above 0.
        val fade = 0.85f
        val fadeTranslate = 255f * (1f - fade) / 2f
        val fadeMatrix = ColorMatrix(floatArrayOf(
            fade, 0f, 0f, 0f, fadeTranslate,
            0f, fade, 0f, 0f, fadeTranslate,
            0f, 0f, fade, 0f, fadeTranslate,
            0f, 0f, 0f, 1f, 0f
        ))
        sepia.postConcat(fadeMatrix)
        return drawWithMatrix(source, sepia)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.filters.PhotoFiltersTest"`
Expected: PASS (all four tests so far).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt
git commit -m "Add sepia/faded filter"
```

---

### Task 4: PhotoFilters — heavy grain, plus the look dispatcher

**Files:**
- Modify: `app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt`
- Modify: `app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt`

**Interfaces:**
- Consumes: `Look` enum from Task 2.
- Produces: `PhotoFilters.applyGrain(source: Bitmap): Bitmap` and `PhotoFilters.apply(source: Bitmap, look: Look): Bitmap` — the dispatcher `MainActivity` (Task 7) calls.

- [ ] **Step 1: Write the failing tests**

Add to `PhotoFiltersTest.kt`:
```kotlin
    @Test
    fun `grain output differs from input at many pixels`() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                source.setPixel(x, y, Color.rgb(128, 128, 128))
            }
        }
        val result = PhotoFilters.applyGrain(source)

        var changedCount = 0
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                if (result.getPixel(x, y) != source.getPixel(x, y)) changedCount++
            }
        }
        assertTrue("expected most of the 400 pixels to be perturbed by noise, only $changedCount changed",
            changedCount > 300)
    }

    @Test
    fun `grain keeps channel values within valid 0 to 255 range`() {
        // Pixels near the edges of the valid range are the ones most likely
        // to reveal an unclamped overflow/underflow bug in the noise step.
        val source = coloredBitmap(Color.rgb(2, 253, 128))
        val result = PhotoFilters.applyGrain(source)

        val pixel = result.getPixel(0, 0)
        assertTrue(Color.red(pixel) in 0..255)
        assertTrue(Color.green(pixel) in 0..255)
        assertTrue(Color.blue(pixel) in 0..255)
    }

    @Test
    fun `apply dispatches to the matching filter for each look`() {
        val source = coloredBitmap(Color.rgb(200, 50, 100))

        val bwPixel = PhotoFilters.apply(source, Look.BLACK_AND_WHITE).getPixel(0, 0)
        assertEquals(Color.red(bwPixel), Color.green(bwPixel))

        val sepiaPixel = PhotoFilters.apply(source, Look.SEPIA).getPixel(0, 0)
        assertTrue(Color.red(sepiaPixel) > Color.blue(sepiaPixel))

        // GRAIN's output is randomized, so just confirm apply() routes to it
        // without throwing and returns a same-sized bitmap.
        val grainResult = PhotoFilters.apply(source, Look.GRAIN)
        assertEquals(source.width, grainResult.width)
        assertEquals(source.height, grainResult.height)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.filters.PhotoFiltersTest"`
Expected: FAIL — `applyGrain` and `apply` unresolved references.

- [ ] **Step 3: Write the implementation**

Add to `PhotoFilters.kt` imports:
```kotlin
import kotlin.random.Random
```

Add to `PhotoFilters.kt` (inside the `object PhotoFilters { ... }` block, after `applySepia`):
```kotlin
    fun applyGrain(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val random = Random(System.nanoTime())
        val noiseRange = 40 // max +/- per channel

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            val r = addNoise((pixel shr 16) and 0xFF, random, noiseRange)
            val g = addNoise((pixel shr 8) and 0xFF, random, noiseRange)
            val b = addNoise(pixel and 0xFF, random, noiseRange)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun addNoise(channel: Int, random: Random, range: Int): Int {
        val noise = random.nextInt(range * 2 + 1) - range
        return (channel + noise).coerceIn(0, 255)
    }

    fun apply(source: Bitmap, look: Look): Bitmap = when (look) {
        Look.BLACK_AND_WHITE -> applyBlackAndWhite(source)
        Look.SEPIA -> applySepia(source)
        Look.GRAIN -> applyGrain(source)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.filters.PhotoFiltersTest"`
Expected: PASS (all seven tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mombotro/rangefindercam/filters/PhotoFilters.kt app/src/test/java/com/mombotro/rangefindercam/filters/PhotoFiltersTest.kt
git commit -m "Add heavy grain filter and look dispatcher"
```

---

### Task 5: PhotoStorage — save and media-scan

**Files:**
- Create: `app/src/main/java/com/mombotro/rangefindercam/storage/PhotoStorage.kt`
- Test: `app/src/test/java/com/mombotro/rangefindercam/storage/PhotoStorageTest.kt`

**Interfaces:**
- Produces: `object PhotoStorage { fun save(context: Context, bitmap: Bitmap, picturesDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)): File? }` in package `com.mombotro.rangefindercam.storage`. `MainActivity` (Task 7) calls `PhotoStorage.save(context, bitmap)` — the two-argument form, relying on the default `picturesDir`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/mombotro/rangefindercam/storage/PhotoStorageTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.storage.PhotoStorageTest"`
Expected: FAIL — `PhotoStorage` unresolved reference (file doesn't exist yet).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/mombotro/rangefindercam/storage/PhotoStorage.kt`:
```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.mombotro.rangefindercam.storage.PhotoStorageTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mombotro/rangefindercam/storage/PhotoStorage.kt app/src/test/java/com/mombotro/rangefindercam/storage/PhotoStorageTest.kt
git commit -m "Add photo storage with media-scan"
```

---

### Task 6: CameraPreviewView — Camera1 capture

**Files:**
- Create: `app/src/main/java/com/mombotro/rangefindercam/camera/CameraPreviewView.kt`

**Interfaces:**
- Produces: `class CameraPreviewView(context: Context, attrs: AttributeSet?) : SurfaceView` with:
  - `var onCameraError: ((String) -> Unit)?` — set by `MainActivity` (Task 7) to show a Toast on camera-open/preview failure.
  - `fun capture(onCaptured: (Bitmap) -> Unit, onError: (String) -> Unit)` — `MainActivity`'s shutter button handler calls this.

This class talks to the real `android.hardware.Camera` API and a real display surface — it isn't meaningfully unit-testable (Robolectric's camera shadows don't exercise real hardware behavior), so this task has no unit test. Task 8 covers on-device manual verification of capture behavior end to end.

- [ ] **Step 1: Write the implementation**

`app/src/main/java/com/mombotro/rangefindercam/camera/CameraPreviewView.kt`:
```kotlin
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
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mombotro/rangefindercam/camera/CameraPreviewView.kt
git commit -m "Add Camera1 preview and capture wrapper"
```

---

### Task 7: Wire the UI together

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/drawable/shutter_button_bg.xml`
- Modify: `app/src/main/java/com/mombotro/rangefindercam/MainActivity.kt`

**Interfaces:**
- Consumes: `CameraPreviewView` (Task 6), `PhotoFilters.apply(source, look)` + `Look` enum (Task 4), `PhotoStorage.save(context, bitmap)` (Task 5).

- [ ] **Step 1: Replace the placeholder layout with the real UI**

`app/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.mombotro.rangefindercam.camera.CameraPreviewView
        android:id="@+id/cameraPreview"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:background="#CC000000"
        android:padding="12dp">

        <RadioGroup
            android:id="@+id/lookChips"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center">

            <RadioButton
                android:id="@+id/chipBlackAndWhite"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="24dp"
                android:textColor="#FFFFFF"
                android:checked="true"
                android:text="@string/look_black_and_white" />

            <RadioButton
                android:id="@+id/chipSepia"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="24dp"
                android:textColor="#FFFFFF"
                android:text="@string/look_sepia" />

            <RadioButton
                android:id="@+id/chipGrain"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#FFFFFF"
                android:text="@string/look_grain" />

        </RadioGroup>

        <Button
            android:id="@+id/shutterButton"
            android:layout_width="80dp"
            android:layout_height="80dp"
            android:layout_marginTop="12dp"
            android:layout_gravity="center_horizontal"
            android:background="@drawable/shutter_button_bg"
            android:text="" />

    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 2: Add the shutter button drawable**

`app/src/main/res/drawable/shutter_button_bg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#FFFFFF" />
    <stroke android:width="4dp" android:color="#000000" />
</shape>
```

- [ ] **Step 3: Wire MainActivity**

`app/src/main/java/com/mombotro/rangefindercam/MainActivity.kt`:
```kotlin
package com.mombotro.rangefindercam

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import com.mombotro.rangefindercam.camera.CameraPreviewView
import com.mombotro.rangefindercam.filters.Look
import com.mombotro.rangefindercam.filters.PhotoFilters
import com.mombotro.rangefindercam.storage.PhotoStorage

class MainActivity : Activity() {

    private lateinit var cameraPreview: CameraPreviewView
    private var selectedLook = Look.BLACK_AND_WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        cameraPreview.onCameraError = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
}
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all `PhotoFiltersTest` and `PhotoStorageTest` tests still PASS (this task didn't touch their files, but confirms nothing else broke).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/res/drawable/shutter_button_bg.xml app/src/main/java/com/mombotro/rangefindercam/MainActivity.kt
git commit -m "Wire camera preview, look chips, and shutter together"
```

---

### Task 8: On-device verification, README, and push

**Files:**
- Create: `README.md`

- [ ] **Step 1: Install on the LG Intuition and smoke-test capture**

Run:
```bash
adb -s 4c077791 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 4c077791 shell am start -n com.mombotro.rangefindercam/.MainActivity
```
On the device: confirm the live camera preview shows (plain, unfiltered). Tap each look chip in turn and confirm only one stays selected at a time. With B&W selected, tap the shutter; confirm a "Saved" toast appears.

- [ ] **Step 2: Verify the saved file and Gallery visibility**

Run:
```bash
adb -s 4c077791 shell ls /storage/sdcard0/Pictures/RangefinderCam/
```
Expected: one `.jpg` file with a timestamp filename. Pull it and inspect visually:
```bash
adb -s 4c077791 pull /storage/sdcard0/Pictures/RangefinderCam/<filename>.jpg /Users/juleah/.claude/jobs/dfc7f9a8/tmp/rangefindercam_test.jpg
```
Read the pulled file as an image and confirm it looks like a real high-contrast B&W photo, not a black/corrupt frame. Then open the stock Gallery app on-device and confirm the same photo appears there (proves the media-scan call worked).

- [ ] **Step 3: Repeat for sepia and grain looks**

Select the Sepia chip, shoot, verify a new file appears and looks warm-toned/faded. Select the Grain chip, shoot, verify a new file appears and visibly shows noise texture versus the other two.

- [ ] **Step 4: Verify the camera-busy error path**

While RangefinderCam is in the foreground with the camera open, start the now-restored LG stock camera app in another window/task (or use `adb shell am start -n com.lge.camera/.CameraApp`) to force a camera-hardware contention, then switch back to RangefinderCam and tap the shutter. Expected: a Toast reporting the camera error, not a crash.

- [ ] **Step 5: Write the README**

`README.md`:
```markdown
# RangefinderCam

A small camera app for the LG Intuition (Android 4.1.2, API16). Pick a look
before you shoot - high-contrast black & white, sepia/faded, or heavy grain -
and it's baked into the photo on capture. Photos save to
`Pictures/RangefinderCam/` and show up in the stock Gallery app.

Built with the classic `android.hardware.Camera` API (this device predates
Camera2) and a plain, unfiltered live preview - the look is applied once to
the captured JPEG, not rendered live, to avoid the GPU cost of a real-time
shader pipeline on this phone's old Adreno 200.

See `docs/superpowers/specs/2026-08-29-rangefindercam-design.md` for the full
design.
```

- [ ] **Step 6: Commit and push**

```bash
git add README.md
git commit -m "Add README"
gh repo create mombotro/rangefindercam --public --source=. --remote=origin --push
```

## Self-Review Notes

- **Spec coverage:** plain preview + post-capture filter (Task 6/7), all three looks (Tasks 2-4), rangefinder-style UI with one-look-at-a-time chips + shutter (Task 7), public-folder save + media-scan (Task 5), camera-open and storage-write error handling (Task 6/8), Gradle project matching `chikins`'s known-working versions (Task 1) — all covered.
- **Placeholder scan:** none found.
- **Type consistency:** `Look` enum (Task 2) used identically in Task 4's `apply()`, Task 7's `MainActivity`. `PhotoFilters.apply(source: Bitmap, look: Look): Bitmap` signature matches its one call site in Task 7. `PhotoStorage.save(context, bitmap, picturesDir = ...)` signature matches both its test call (3-arg) and its `MainActivity` call site (2-arg, using the default).
