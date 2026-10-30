package com.qingyu.personalaios.feature.calendar
import androidx.lifecycle.*
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private class Repository:CalendarRepository {
        val observed=mutableListOf<LocalDate>()
        override fun observe(date:LocalDate,zone:ZoneId):Flow<CalendarState> {
            observed+=date
            val start=date.atTime(9,0).atZone(zone).toInstant()
            return flowOf(CalendarState((1..6).map { CalendarEvent("$date:$it","event $it",start.plusSeconds(it*3600L),start.plusSeconds(it*3600L+1800),false) },true))
        }
    }
    @Test fun selectingDatesQueriesThoseDatesAndRetainsEveryEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository();val saved=SavedStateHandle();val model=CalendarViewModel(saved,repo)
        val store=ViewModelStore().apply { put("calendar",model) }
        val collect=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.calendar.collect() }
        try {
            runCurrent();val date=LocalDate.of(2028,2,29);model.select(date);runCurrent()
            assertEquals(date,repo.observed.last());assertEquals(6,model.calendar.value.events.size)
            assertEquals("2028-02-29",saved.get<String>("day"))
            model.toggle("missing");assertTrue(model.completed.value.isEmpty())
            model.toggle("$date:1");assertEquals(listOf("$date:1"),model.completed.value)
            model.refresh();runCurrent();assertEquals(6,model.calendar.value.events.size)
            model.select(date.plusDays(1));runCurrent();assertEquals(LocalDate.of(2028,3,1),repo.observed.last())
        } finally { collect.cancel();store.clear();runCurrent();Dispatchers.resetMain() }
    }
    @Test fun selectedDateRestoresWithoutChangingCalendarRecords() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved=SavedStateHandle(mapOf("day" to "2027-12-31"))
        val model=CalendarViewModel(saved,Repository())
        val store=ViewModelStore().apply { put("calendar",model) }
        try { assertEquals(LocalDate.of(2027,12,31),model.day.value) }
        finally { store.clear();runCurrent();Dispatchers.resetMain() }
    }
}
