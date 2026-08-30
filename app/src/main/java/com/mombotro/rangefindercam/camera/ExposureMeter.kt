package com.mombotro.rangefindercam.camera

enum class MeterState {
    UNDER,
    OVER,
    CORRECT
}

/**
 * Pure light-meter logic: compares a sampled average preview brightness
 * against a middle-gray target, independent of any camera plumbing. This
 * app's ISO/exposure controls bias an auto-exposure loop that's still
 * running underneath (Camera1 has no true manual shutter), so this reads
 * the actual resulting brightness under the current settings rather than
 * a value the camera reports directly - closer to how a real handheld
 * light meter works than to a camera-reported exposure value.
 */
object ExposureMeter {
    fun evaluate(averageLuma: Int, target: Int = 128, tolerance: Int = 15): MeterState {
        return when {
            averageLuma > target + tolerance -> MeterState.OVER
            averageLuma < target - tolerance -> MeterState.UNDER
            else -> MeterState.CORRECT
        }
    }
}
