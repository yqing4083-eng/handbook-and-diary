package com.qingyu.personalaios.feature.today
import org.junit.Assert.*
import org.junit.Test
class TodayPullRulesTest {
 @Test fun ordinaryScrollingDoesNotBecomeRefresh() { assertEquals(0f,TodayPullRules.consume(0f,120f,false,false),0f);assertEquals(0f,TodayPullRules.consume(0f,-120f,true,false),0f) }
 @Test fun reversingBeforeReleaseConsumesOnlyTheOutstandingPull() { assertEquals(-70f,TodayPullRules.consume(70f,-140f,true,false),0f) }
 @Test fun inFlightRequestCannotBeTriggeredTwice() { assertEquals(0f,TodayPullRules.consume(90f,30f,true,true),0f);assertFalse(TodayPullRules.shouldRefresh(90f,60f,true)) }
 @Test fun belowThresholdReleaseCancelsAndThresholdReleaseRefreshes() { assertFalse(TodayPullRules.shouldRefresh(59f,60f,false));assertTrue(TodayPullRules.shouldRefresh(60f,60f,false)) }
}
