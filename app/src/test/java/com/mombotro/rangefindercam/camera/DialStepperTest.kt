package com.mombotro.rangefindercam.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class DialStepperTest {

    @Test
    fun `stepping forward within range advances by one`() {
        assertEquals(3, DialStepper.step(currentIndex = 2, direction = 1, size = 6))
    }

    @Test
    fun `stepping backward within range retreats by one`() {
        assertEquals(1, DialStepper.step(currentIndex = 2, direction = -1, size = 6))
    }

    @Test
    fun `stepping forward past the last index hits a hard stop, no wraparound`() {
        assertEquals(5, DialStepper.step(currentIndex = 5, direction = 1, size = 6))
    }

    @Test
    fun `stepping backward past the first index hits a hard stop, no wraparound`() {
        assertEquals(0, DialStepper.step(currentIndex = 0, direction = -1, size = 6))
    }

    @Test
    fun `a single-value dial always stays at index zero`() {
        assertEquals(0, DialStepper.step(currentIndex = 0, direction = 1, size = 1))
        assertEquals(0, DialStepper.step(currentIndex = 0, direction = -1, size = 1))
    }
}
