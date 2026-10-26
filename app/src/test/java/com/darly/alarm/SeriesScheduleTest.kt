package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class SeriesScheduleTest {
 @Test fun workdaySeries(){
  val slots=SeriesSchedule.slots(4*60+30,10,8)
  assertEquals(listOf("04:30","04:40","04:50","05:00","05:10","05:20","05:30","05:40"),slots.map{it.label})
  assertTrue(slots.all{it.dayOffset==0})
 }
 @Test fun midnightAndWeekdayShift(){
  val slots=SeriesSchedule.slots(23*60+50,10,4)
  assertEquals(listOf("23:50","00:00","00:10","00:20"),slots.map{it.label})
  assertEquals(listOf(0,1,1,1),slots.map{it.dayOffset})
  // Monday after midnight is Tuesday; Sunday rotates to Monday.
  assertEquals(2,SeriesSchedule.shiftDays(1,1))
  assertEquals(1,SeriesSchedule.shiftDays(64,1))
  assertEquals(31,SeriesSchedule.shiftDays(31,0))
  assertEquals(0,SeriesSchedule.shiftDays(0,4))
 }
 @Test fun maxAllowedSeriesDoesNotDuplicateTimes(){
  val slots=SeriesSchedule.slots(0,10,24)
  assertEquals(24,slots.distinctBy{it.dayOffset*1440+it.minuteOfDay}.size)
 }
 @Test(expected=IllegalArgumentException::class)fun rejectZeroInterval(){SeriesSchedule.slots(270,0,8)}
 @Test(expected=IllegalArgumentException::class)fun rejectTooManyAlarms(){SeriesSchedule.slots(270,10,25)}
 @Test(expected=IllegalArgumentException::class)fun rejectInvalidTime(){SeriesSchedule.slots(1440,10,8)}
}
