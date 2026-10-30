package com.qingyu.personalaios.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingyu.personalaios.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@HiltViewModel class CalendarViewModel @Inject constructor(private val saved:SavedStateHandle,private val repository:CalendarRepository):ViewModel() {
    private val selected=MutableStateFlow(saved.get<String>("day")?.let(LocalDate::parse) ?: LocalDate.now())
    val day=selected.asStateFlow()
    private val reload=MutableStateFlow(0)
    val completed:StateFlow<ArrayList<String>> = saved.getStateFlow("completed",arrayListOf())
    val initialized=MutableStateFlow(false)
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendar=combine(selected,reload) { date,_ -> date }.flatMapLatest { repository.observe(it,ZoneId.systemDefault()) }
        .onEach { initialized.value=true }.catch { initialized.value=true;emit(CalendarState(message="日历暂时无法读取")) }
        .stateIn(viewModelScope,SharingStarted.Eagerly,CalendarState())
    fun select(date:LocalDate) { selected.value=date;saved["day"]=date.toString() }
    fun refresh() { reload.value++ }
    fun toggle(id:String) {
        if(calendar.value.events.none { it.id==id }) return
        val next=ArrayList(completed.value);if(!next.remove(id)) next.add(id);saved["completed"]=next
    }
}
