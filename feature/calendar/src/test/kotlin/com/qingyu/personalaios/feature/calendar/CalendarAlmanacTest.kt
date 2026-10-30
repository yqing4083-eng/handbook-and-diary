package com.qingyu.personalaios.feature.calendar
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class CalendarAlmanacTest {
    @Test fun solarAndLunarFestivalsMatchKnown2026Dates() {
        assertTrue(CalendarAlmanac.festival(LocalDate.of(2026,1,1)).contains("元旦"))
        assertTrue(CalendarAlmanac.festival(LocalDate.of(2026,2,17)).contains("春节"))
        assertTrue(CalendarAlmanac.festival(LocalDate.of(2026,9,25)).contains("中秋"))
        assertTrue(CalendarAlmanac.festival(LocalDate.of(2026,10,1)).contains("国庆"))
    }
    @Test fun dailyReflectionIsStableAndWorksAcrossYearBoundaries() {
        val day=LocalDate.of(2026,12,31)
        assertEquals(DailyFortunes.forDate(day),DailyFortunes.forDate(day))
        assertNotEquals(DailyFortunes.forDate(day),DailyFortunes.forDate(day.plusDays(1)))
        assertTrue(CalendarAlmanac.lunarDate(day).isNotBlank())
    }
}
