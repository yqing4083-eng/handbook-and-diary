package com.qingyu.personalaios.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.qingyu.personalaios.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@HiltViewModel class TodayViewModel @Inject constructor(private val savedState: SavedStateHandle,
    private val repository: CalendarRepository): ViewModel() {
    private val day=MutableStateFlow(LocalDate.now() to ZoneId.systemDefault())
    private val reload=MutableStateFlow(0)
    val completed: StateFlow<ArrayList<String>> = savedState.getStateFlow("calendar-completed",arrayListOf())
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendar=combine(day,reload) { value,_ -> value }.flatMapLatest { (date,zone) -> repository.observe(date,zone) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),CalendarState())
    fun date(value: LocalDate,zone: ZoneId) {
        val key=value.toString()
        if(savedState.get<String>("calendar-date")!=key) { savedState["calendar-completed"]=arrayListOf<String>();savedState["calendar-date"]=key }
        day.value=value to zone
    }
    fun refreshCalendar() { reload.value++ }
    fun toggle(id: String) {
        if(calendar.value.events.none { it.id==id }) return
        val next=ArrayList(completed.value);if(!next.remove(id)) next.add(id)
        savedState["calendar-completed"]=next
    }
}
