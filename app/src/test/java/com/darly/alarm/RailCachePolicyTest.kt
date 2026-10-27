package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class RailCachePolicyTest {
 @Test fun firstLaunchNeedsFetch(){assertTrue(RailCachePolicy.shouldRefresh(0L,10_000L))}
 @Test fun freshCacheSurvivesRestart(){
  assertFalse(RailCachePolicy.shouldRefresh(1_000L,1_000L+RailCachePolicy.MAX_AGE_MS-1))
 }
 @Test fun cacheExpiresAfterSevenDays(){
  assertTrue(RailCachePolicy.shouldRefresh(1_000L,1_000L+RailCachePolicy.MAX_AGE_MS))
 }
 @Test fun clockWentBackTriggersRefresh(){assertTrue(RailCachePolicy.shouldRefresh(1_001L,1_000L))}
}
