package com.mombotro.rangefindercam.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureMeterTest {

    @Test
    fun `brightness well above target reads as overexposed`() {
        assertEquals(MeterState.OVER, ExposureMeter.evaluate(averageLuma = 220))
    }

    @Test
    fun `brightness well below target reads as underexposed`() {
        assertEquals(MeterState.UNDER, ExposureMeter.evaluate(averageLuma = 30))
    }

    @Test
    fun `brightness at the target reads as correct`() {
        assertEquals(MeterState.CORRECT, ExposureMeter.evaluate(averageLuma = 128))
    }

    @Test
    fun `brightness just inside the tolerance band on either side reads as correct`() {
        assertEquals(MeterState.CORRECT, ExposureMeter.evaluate(averageLuma = 128 + 15))
        assertEquals(MeterState.CORRECT, ExposureMeter.evaluate(averageLuma = 128 - 15))
    }

    @Test
    fun `brightness just outside the tolerance band reads as over or under`() {
        assertEquals(MeterState.OVER, ExposureMeter.evaluate(averageLuma = 128 + 16))
        assertEquals(MeterState.UNDER, ExposureMeter.evaluate(averageLuma = 128 - 16))
    }

    @Test
    fun `custom target and tolerance are respected`() {
        assertEquals(MeterState.CORRECT, ExposureMeter.evaluate(averageLuma = 100, target = 100, tolerance = 5))
        assertEquals(MeterState.OVER, ExposureMeter.evaluate(averageLuma = 106, target = 100, tolerance = 5))
        assertEquals(MeterState.UNDER, ExposureMeter.evaluate(averageLuma = 94, target = 100, tolerance = 5))
    }
}
