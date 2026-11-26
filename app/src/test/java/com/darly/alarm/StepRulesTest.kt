package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StepRulesTest {
 private val today="2026-11-26"
 private fun baseline()=StepRules.record(StepLedger(days=listOf(StepDay(today))),42000,7,100,today,8000)
 @Test fun initialCounterDoesNotInventTodaysSteps(){assertEquals(0,baseline().days.single().steps)}
 @Test fun firstOldEventDoesNotCreateHistoricalData(){assertTrue(StepRules.record(StepLedger(),42000,7,100,"2026-11-25",8000).days.isEmpty())}
 @Test fun deltasSurviveProcessRestart(){val one=StepRules.record(baseline(),42020,7,200,today,8000);assertEquals(30,StepRules.record(one.copy(),42030,7,300,today,8000).days.single().steps)}
 @Test fun rebootDoesNotSubtractOrAddLifetimeCounter(){val one=StepRules.record(baseline(),42020,7,200,today,8000);val reboot=StepRules.record(one,100,8,10,today,8000);assertEquals(20,reboot.days.single().steps);assertEquals(25,StepRules.record(reboot,105,8,20,today,8000).days.single().steps)}
 @Test fun staleAndDuplicateSamplesAreIgnored(){val first=baseline();assertEquals(first,StepRules.record(first,42010,7,100,today,8000));assertEquals(first,StepRules.record(first,41990,7,90,today,8000))}
 @Test fun counterResetNeverCreatesNegativeSteps(){val one=StepRules.record(baseline(),42020,7,200,today,8000);val reset=StepRules.record(one,0,7,300,today,8000);assertEquals(20,reset.days.single().steps);assertEquals(23,StepRules.record(reset,3,7,400,today,8000).days.single().steps)}
 @Test fun midnightKeepsEachDaySeparate(){val one=StepRules.record(baseline(),42020,7,200,today,8000);val next=StepRules.record(one,42025,7,300,"2026-11-27",9000);assertEquals(20,next.days.first().steps);assertEquals(5,next.days.last().steps);assertEquals(9000,next.days.last().goal)}
 @Test fun pausedStepsAreExcluded(){val one=StepRules.record(baseline(),42020,7,200,today,8000).copy(counter=-1,eventNanos=-1);val resumed=StepRules.record(one,43000,7,300,today,8000);assertEquals(20,resumed.days.single().steps);assertEquals(25,StepRules.record(resumed,43005,7,400,today,8000).days.single().steps)}
 @Test fun invalidSamplesAreIgnored(){assertEquals(baseline(),StepRules.record(baseline(),-1,7,200,today,8000))}
 @Test fun streakUsesHistoricalDailyGoals(){val days=listOf(StepDay("2026-11-24",5000,5000),StepDay("2026-11-25",8000,8000),StepDay(today,100,9000));assertEquals(2,StepRules.streak(days,LocalDate.parse(today)))}
 @Test fun weeklyChallengeIncludesOnlyItsSevenDays(){val c=StepChallenge("week","2026-11-20",8000);val p=StepRules.challenge(c,listOf(StepDay("2026-11-19",99999),StepDay("2026-11-20",20000),StepDay(today,15000),StepDay("2026-11-27",99999)),LocalDate.parse(today));assertEquals(35000,p.value);assertTrue(p.completed)}
 @Test fun dailyChallengeExpiresWithoutFakeCompletion(){val p=StepRules.challenge(StepChallenge("walk","2026-11-25",8000),listOf(StepDay("2026-11-25",2999),StepDay(today,9999)),LocalDate.parse(today));assertTrue(p.expired);assertFalse(p.completed);assertEquals(2999,p.value)}
 @Test fun activeDaysChallengeFreezesItsGoal(){val c=StepChallenge("streak","2026-11-20",8000);val p=StepRules.challenge(c,listOf(StepDay("2026-11-20",8000,10000),StepDay("2026-11-21",8500,10000),StepDay(today,9000,10000)),LocalDate.parse(today));assertTrue(p.completed);assertEquals(3,p.value)}
}
