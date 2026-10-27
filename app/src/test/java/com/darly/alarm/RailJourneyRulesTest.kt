package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class RailJourneyRulesTest {
 @Test fun faresPreserveMinorUnits(){
  assertEquals("139 ₽",RailJourneyRules.fare("","RUB",139,0)!!.label)
  assertEquals("139,05 ₽",RailJourneyRules.fare("","RUB",139,5)!!.label)
  assertEquals("0 ₽",RailJourneyRules.fare("","RUB",0,0)!!.label)
  assertEquals("20,5 €",RailJourneyRules.fare("","EUR",20,50)!!.label)
  assertEquals("500 JPY",RailJourneyRules.fare("","JPY",500,0)!!.label)
 }
 @Test fun invalidOrAbsentPricesStayUnavailable(){
  assertNull(RailJourneyRules.fare("","RUB",-1,0))
  assertNull(RailJourneyRules.fare("","",139,0))
  assertNull(RailJourneyRules.fare("","RUB",139,100))
  assertNull(RailJourneyRules.fare("","RUB",Long.MAX_VALUE,99))
 }
 @Test fun segmentUsesSelectedStationsInTravelOrder(){
  assertEquals(1..3,RailJourneyRules.segment(listOf("s0","s1","s2","s3","s4"),"s1","s3"))
  assertNull(RailJourneyRules.segment(listOf("s0","s1"),"s1","s0"))
  assertNull(RailJourneyRules.segment(listOf("s0","s1"),"s0","missing"))
 }
 @Test fun stopTimesAcceptBothUpstreamFormats(){
  assertEquals("00:02",RailJourneyRules.time("2026-10-28 00:02:00"))
  assertEquals("23:59",RailJourneyRules.time("2026-10-28T23:59:00+03:00"))
  assertEquals("—",RailJourneyRules.time(null))
  assertEquals("—",RailJourneyRules.time("not available"))
  assertEquals("—",RailJourneyRules.time("2026-10-28 29:10:00"))
 }
}
