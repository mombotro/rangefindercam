# RangefinderCam — design spec

Date: 2026-08-29
Target device: LG Intuition (VS950, codename `batman_vzw`), Android 4.1.2, API16, rooted, no Camera2 API available.

## Purpose

A small standalone camera app for the LG Intuition. Shoot photos with an
"arty" look baked in — high-contrast black & white, sepia/faded, or heavy
grain — chosen before the shutter press, rangefinder-style: dial in a look,
then shoot. Personal-use toy app, not a general product.

## Non-goals

- No live-filtered viewfinder (see Approach below for why).
- No editing/undo/compare screen after capture.
- No multiple photos per look comparison, no burst mode, no video.
- No cloud/sharing integration beyond what the stock Gallery app already
  provides once the photo is media-scanned.

## Approach

### Capture

Classic `android.hardware.Camera` (Camera1) API — this is the only camera
API available at API16; Camera2 arrived in API21. Preview renders into a
`SurfaceView` (required by Camera1), full-screen, landscape sensor
orientation corrected for portrait display via `setDisplayOrientation`.

The live preview shows the **plain, unfiltered** camera feed. A real-time
filtered viewfinder would need a GLES shader pipeline (SurfaceTexture +
GLSurfaceView + fragment shader) to stay smooth; this session already hit a
hard wall running a modern GL-heavy app (RetroArch) on this phone's Adreno
200 GPU — it hung indefinitely. A plain `SurfaceView` preview carries none
of that risk, and the one-shot post-capture processing (below) still lands
before you ever see the saved photo, so there's no live-vs-final mismatch to
manage.

### UI

Single full-screen `Activity`. Bottom overlay bar: three look chips (B&W /
Sepia / Grain) and a shutter button, styled flat/monochrome to match this
session's existing 1-bit aesthetic (chikins, the dither work). Exactly one
chip is selected at a time (default: B&W). Whichever chip is highlighted
when the shutter is tapped is the look baked into that capture. No settings
screen, no menu.

### Filter processing

Runs once per capture, on the decoded JPEG `Bitmap`, immediately after
`Camera.PictureCallback` delivers the raw bytes — never per-frame, so cost
is one-shot and CPU headroom isn't a concern the way it would be for a live
filter:

- **High-contrast B&W** — `ColorMatrix` desaturate (saturation 0) composed
  with a contrast-boost matrix (scale about the midpoint), applied via a
  single `Canvas`/`Paint` draw with `ColorMatrixColorFilter`.
- **Sepia/faded** — standard sepia `ColorMatrix` coefficients, composed with
  a matrix that lifts blacks and compresses whites slightly for the faded
  look. Same single-draw approach.
- **Heavy grain** — `ColorMatrix` can't express per-pixel noise (it's a
  fixed linear transform), so this one needs a real pixel pass:
  `Bitmap.getPixels()` into an `int[]`, add random per-channel noise to each
  pixel, `Bitmap.setPixels()` back. One-shot per photo; at this device's
  camera resolution (~5MP) this is a bounded, single Java loop that runs
  once per shutter press, not every frame — acceptable even on this
  hardware.

### Storage

Saves to `/storage/sdcard0/Pictures/RangefinderCam/<timestamp>.jpg` (public
external storage — API16 needs only the `WRITE_EXTERNAL_STORAGE` manifest
permission, no runtime permission flow). After writing the file, calls
`MediaScannerConnection.scanFile()` so it appears in the stock Gallery app
immediately, same as any other camera app's output.

### Error handling

- Camera open failure (in use by another app, hardware unavailable): show a
  simple message, don't crash.
- Storage write failure (SD card full/missing): show a message, don't lose
  the in-memory captured bitmap silently — surface the failure.
- No permission-request flow needed beyond the manifest declaration at this
  API level.

## Build & delivery

New standalone Gradle project (matches the `chikins` project in this same
session — proven to build and deploy cleanly on this machine already), new
GitHub repo. Package name: `com.mombotro.rangefindercam`.

## Testing

- Unit tests for the three `ColorMatrix`/pixel-noise transforms, run against
  a small in-memory test bitmap, asserting expected pixel-level properties
  (e.g., B&W output has R==G==B per pixel; grain output differs from input
  at some threshold of pixels; sepia output matches expected hue shift).
- Manual on-device verification for capture → filter → save → Gallery
  visibility, and for camera-open/storage-failure error paths where
  feasible to simulate.
