package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerUtilTest {
    @Test fun zoomIsClampedAndSteps() {
        assertEquals(70, Zoom.clamp(10))
        assertEquals(250, Zoom.clamp(900))
        assertEquals(110, Zoom.step(100, 1))
        assertEquals(90, Zoom.step(100, -1))
        assertEquals(250, Zoom.step(250, 1))
        assertEquals(70, Zoom.step(70, -1))
    }

    @Test fun zoomStepsAlignOnMultiplesOfTen() {
        assertEquals(120, Zoom.step(105, 1)) // 105 s'aligne d'abord sur 110
        assertEquals(100, Zoom.step(105, -1))
        assertEquals(110, Zoom.step(114, 0))
    }

    @Test fun timeFormatting() {
        assertEquals("0:00", PlayerFormat.time(Double.NaN))
        assertEquals("0:00", PlayerFormat.time(-5.0))
        assertEquals("0:09", PlayerFormat.time(9.7))
        assertEquals("1:05", PlayerFormat.time(65.0))
        assertEquals("12:34", PlayerFormat.time(754.0))
        assertEquals("1:02:03", PlayerFormat.time(3723.0))
    }

    @Test fun percentIsClamped() {
        assertEquals(0, PlayerFormat.percent(-1f))
        assertEquals(50, PlayerFormat.percent(0.5f))
        assertEquals(100, PlayerFormat.percent(3f))
    }
}
