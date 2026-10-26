package com.darly.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerMathTest {
 @Test fun countdownNeverNegative(){
  assertEquals(10_000L,TimerMath.remaining(90_000L,80_000L))
  assertEquals(0L,TimerMath.remaining(90_000L,95_000L))
 }
 @Test fun formatShortAndLongTimers(){
  assertEquals("00:00:00",TimerMath.format(0L))
  assertEquals("01:01:01",TimerMath.format(3_661_000L))
  assertEquals("25:00:00",TimerMath.format(90_000_000L))
  assertEquals("00:00:03.45",TimerMath.format(3459L,true))
 }
 @Test fun successiveStopwatchMarks(){
  assertEquals(5_000L,TimerMath.split(5_000L,0L))
  assertEquals(3_000L,TimerMath.split(8_000L,5_000L))
 }
}
