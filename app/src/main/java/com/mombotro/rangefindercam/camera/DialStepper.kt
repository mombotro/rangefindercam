package com.mombotro.rangefindercam.camera

/**
 * Pure index-clamping logic for RotaryDialView, kept separate from the View
 * itself so it's testable without Robolectric. A real camera dial has hard
 * stops at both ends - it never wraps back around - so stepping past
 * either end just returns the same index it started at, letting the
 * caller detect that (same in, same out) as an end-stop.
 */
object DialStepper {
    fun step(currentIndex: Int, direction: Int, size: Int): Int {
        if (size <= 1) return 0
        return (currentIndex + direction).coerceIn(0, size - 1)
    }
}
