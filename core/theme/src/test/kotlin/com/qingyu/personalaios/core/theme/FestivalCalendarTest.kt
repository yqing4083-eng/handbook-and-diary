package com.qingyu.personalaios.core.theme

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FestivalCalendarTest {
    @Test fun christmasAndNewYearHaveDistinctThemesAndIntroductions() {
        val christmas=FestivalCalendar.events(LocalDate.of(2026,12,25)).first { "圣诞" in it.name }
        val newYear=FestivalCalendar.events(LocalDate.of(2027,1,1)).first { "元旦" in it.name }
        assertEquals(FestivalMotif.CHRISTMAS,christmas.motif)
        assertNotEquals(christmas.motif,newYear.motif)
        assertTrue(christmas.introduction.length>20)
    }
    @Test fun lunarFestivalsFollowTheLunarCalendarAcrossYears() {
        assertTrue(FestivalCalendar.events(LocalDate.of(2025,1,29)).any { "春节" in it.name })
        assertTrue(FestivalCalendar.events(LocalDate.of(2026,2,17)).any { "春节" in it.name })
        assertTrue(FestivalCalendar.events(LocalDate.of(2025,10,6)).any { "中秋" in it.name })
        assertFalse(FestivalCalendar.events(LocalDate.of(2026,1,29)).any { "春节" in it.name })
    }
    @Test fun ordinaryDatesDoNotAcquireDecorationsAndResolutionIsStable() {
        val date=LocalDate.of(2026,10,13)
        assertNull(FestivalCalendar.theme(date))
        assertEquals(FestivalCalendar.events(date),FestivalCalendar.events(date))
    }
    @Test fun supportedFestivalsHaveDifferentMotifsAndUsefulText() {
        val dates=listOf(2 to 14,3 to 8,5 to 1,6 to 1,9 to 10,10 to 1,10 to 31,12 to 25)
        val events=dates.map { (month,day) -> FestivalCalendar.events(LocalDate.of(2026,month,day)).first() }
        assertTrue(events.map { it.motif }.distinct().size>=6)
        assertTrue(events.all { it.introduction.length>20 })
    }
}
