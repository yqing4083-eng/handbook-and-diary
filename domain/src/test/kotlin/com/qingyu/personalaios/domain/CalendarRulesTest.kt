package com.qingyu.personalaios.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CalendarRulesTest {
    private val date=LocalDate.of(2026,10,6)
    private fun event(id:String,start:String,end:String,all:Boolean=false)=CalendarEvent(id,id,Instant.parse(start),Instant.parse(end),all)
    @Test fun allDayDatesUseUtcCalendarDaysAcrossTimeZones() {
        val e=event("all","2026-10-06T00:00:00Z","2026-10-07T00:00:00Z",true)
        for(zone in listOf("Asia/Shanghai","America/Los_Angeles")) {
            assertTrue(CalendarRules.overlaps(e,date,ZoneId.of(zone)))
            assertFalse(CalendarRules.overlaps(e,date.minusDays(1),ZoneId.of(zone)))
            assertFalse(CalendarRules.overlaps(e,date.plusDays(1),ZoneId.of(zone)))
        }
    }
    @Test fun overnightEventIncludesBothDaysButExclusiveEndDoesNot() {
        val e=event("night","2026-10-05T15:00:00Z","2026-10-05T17:00:00Z")
        assertTrue(CalendarRules.overlaps(e,date,ZoneId.of("Asia/Shanghai")))
        assertTrue(CalendarRules.overlaps(e,date.minusDays(1),ZoneId.of("Asia/Shanghai")))
        assertFalse(CalendarRules.overlaps(e.copy(end=Instant.parse("2026-10-05T16:00:00Z")),date,ZoneId.of("Asia/Shanghai")))
    }
    @Test fun todayShowsAtMostThreeAndPrioritizesUpcomingOverPastEvents() {
        val now=Instant.parse("2026-10-06T12:00:00Z")
        val items=listOf(event("past","2026-10-06T09:00:00Z","2026-10-06T10:00:00Z"),
            event("next","2026-10-06T13:00:00Z","2026-10-06T14:00:00Z"),
            event("all","2026-10-06T00:00:00Z","2026-10-07T00:00:00Z",true),
            event("active","2026-10-06T11:00:00Z","2026-10-06T15:00:00Z"))
        assertEquals(listOf("all","active","next"),CalendarRules.topThree(items,now).map { it.id })
    }
    @Test fun dstDayUsesItsActualLocalMidnightBoundaries() {
        val zone=ZoneId.of("America/New_York");val day=LocalDate.of(2026,3,8)
        val e=event("dst","2026-03-09T03:30:00Z","2026-03-09T04:00:00Z")
        assertTrue(CalendarRules.overlaps(e,day,zone));assertFalse(CalendarRules.overlaps(e,day.plusDays(1),zone))
    }
}
