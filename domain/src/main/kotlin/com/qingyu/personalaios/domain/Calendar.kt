package com.qingyu.personalaios.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

data class CalendarEvent(val id: String, val title: String, val start: Instant, val end: Instant, val allDay: Boolean)
data class CalendarState(val events: List<CalendarEvent> = emptyList(), val granted: Boolean = false,
    val message: String? = null)
interface CalendarRepository {
    fun observe(date: LocalDate, zone: ZoneId): Flow<CalendarState>
}
object CalendarRules {
    fun overlaps(event: CalendarEvent, date: LocalDate, zone: ZoneId): Boolean = if(event.allDay) {
        val start=event.start.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val end=event.end.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        date>=start && date<end
    } else event.start<date.plusDays(1).atStartOfDay(zone).toInstant() && event.end>date.atStartOfDay(zone).toInstant()
    fun topThree(events: List<CalendarEvent>, now: Instant): List<CalendarEvent> {
        val upcoming = events.filter { it.allDay || it.end > now }.sortedWith(compareBy<CalendarEvent> { !it.allDay }.thenBy { it.start })
        return (upcoming + events.filter { it !in upcoming }.sortedByDescending { it.start }).take(3)
    }
}
