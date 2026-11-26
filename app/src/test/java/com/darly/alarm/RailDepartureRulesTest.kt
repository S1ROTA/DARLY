package com.darly.alarm
import org.junit.Assert.*
import org.junit.Test
class RailDepartureRulesTest {
 @Test fun hidePastKeepFutureAndBoundary(){assertFalse(RailDepartureRules.upcoming(999,1000));assertTrue(RailDepartureRules.upcoming(1000,1000));assertTrue(RailDepartureRules.upcoming(1001,1000))}
}
