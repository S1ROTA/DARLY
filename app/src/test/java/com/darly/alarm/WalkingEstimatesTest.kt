package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class WalkingEstimatesTest {
 @Test fun zeroAndNegativeStepsDoNotCreateActivity(){
  for(steps in listOf(0L,-1L))assertEquals(WalkingEstimate(0.0,0.0,0.0),WalkingEstimates.calculate(steps,70,70))
 }
 @Test fun distanceAndDurationUseExplicitWalkingAssumptions(){
  val result=WalkingEstimates.calculate(10000,80,75)
  assertEquals(8000.0,result.meters,0.0001)
  assertEquals(120.0,result.minutes,0.0001)
  assertEquals(300.0,result.activeKcal,0.0001)
 }
 @Test fun weightOnlyChangesCaloriesAndStrideChangesDistance(){
  val a=WalkingEstimates.calculate(1000,70,70)
  val b=WalkingEstimates.calculate(1000,70,140)
  assertEquals(a.meters,b.meters,0.0);assertEquals(a.minutes,b.minutes,0.0);assertEquals(a.activeKcal*2,b.activeKcal,0.0001)
  assertEquals(a.meters*2,WalkingEstimates.calculate(1000,140,70).meters,0.0001)
 }
 @Test fun invalidProfilesAreRejected(){
  for((stride,weight) in listOf(19 to 70,151 to 70,70 to 19,70 to 301)){
   try{WalkingEstimates.calculate(1,stride,weight);fail("Invalid profile accepted")}catch(_:IllegalArgumentException){}
  }
 }
}
