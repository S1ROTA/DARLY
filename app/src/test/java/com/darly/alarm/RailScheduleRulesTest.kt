package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class RailScheduleRulesTest {
 @Test fun validCodes(){
  assertEquals("s9600213",RailScheduleRules.stationCode(" S9600213 "))
  assertNull(RailScheduleRules.stationCode("c213"))
  assertNull(RailScheduleRules.stationCode("s1"))
  assertNull(RailScheduleRules.stationCode("s99abc"))
 }
 @Test fun routesAreDistinct(){
  assertTrue(RailScheduleRules.validRoute("s9600213","s9600214"))
  assertFalse(RailScheduleRules.validRoute("s9600213","s9600213"))
  assertFalse(RailScheduleRules.validRoute("oops","s9600213"))
 }
 @Test fun datesAreIso(){
  assertNotNull(RailScheduleRules.date("2026-10-27"))
  assertNull(RailScheduleRules.date("27-10-2026"))
  assertNull(RailScheduleRules.date("2026-02-30"))
 }
}
