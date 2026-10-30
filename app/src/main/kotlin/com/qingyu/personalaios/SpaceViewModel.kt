package com.qingyu.personalaios

import android.content.*
import android.os.BatteryManager
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingyu.personalaios.core.theme.*
import com.qingyu.personalaios.data.local.*
import com.qingyu.personalaios.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

data class DevicePower(val percent: Int = 100, val saving: Boolean = false)
data class SpaceState(
    val now: ZonedDateTime,
    val weather: WeatherState,
    val settings: SpaceSettings,
    val power: DevicePower,
    val backgroundWorking: Boolean = false,
    val backgroundError: String? = null,
    val settingsReady: Boolean = false,
) {
    // Keep night colors intact; brighten the default weather environment for
    // manual day mode without changing actual time, weather or custom photos.
    fun theme(): ThemeTokens {
        val night=ThemeEngine.map(ThemeInput(
        weather.data.theme, ThemeEngine.period(now), ThemeEngine.season(now),
        GraphicsQuality.HIGH, 100, false, true, false,
        )).copy(darkBackground=true, foreground=0xFFF5F7FAL)
        return if(settings.nightMode) night else night.copy(
            backgroundTop=mixColor(night.backgroundTop,0xFFF4F7F9L,.84f),
            backgroundBottom=mixColor(night.backgroundBottom,0xFFE3ECF2L,.88f),
            foreground=0xFF000000L,glassTint=0xFFFFFFFFL,darkBackground=false)
    }
}

@HiltViewModel
class SpaceViewModel @Inject constructor(
    private val weather: WeatherRepository,
    private val preferences: SpacePreferences,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val glassDraft = MutableStateFlow<Map<GlassTarget,GlassParameters>>(emptyMap())
    private val settings = combine(preferences.settings, glassDraft) { saved, draft ->
        saved.copy(glass = GlassParameters.Unified,
            glassGroups=draft.entries.fold(saved.glassGroups) { groups,(target,value) -> groups.updated(target,value) })
    }

    @OptIn(FlowPreview::class)
    private fun saveGlassChanges() = viewModelScope.launch {
        // Render immediately; coalesce drag events before writing to disk.
        glassDraft.filter { it.isNotEmpty() }.debounce(200).collect { preferences.setGlassGroups(it) }
    }
    init { saveGlassChanges() }
    fun setGlass(target:GlassTarget,value: GlassParameters) { glassDraft.update { it+(target to value.normalized()) } }
    fun commitGlass() {
        val value = glassDraft.value
        if(value.isNotEmpty()) viewModelScope.launch { preferences.setGlassGroups(value) }
    }
    private val clock = flow {
        while (currentCoroutineContext().isActive) {
            val now = ZonedDateTime.now()
            emit(now)
            delay(60_000L - now.second * 1000L - now.nano / 1_000_000L)
        }
    }
    private val power = callbackFlow {
        var battery = 100
        val manager = context.getSystemService(PowerManager::class.java)
        fun publish() { trySend(DevicePower(battery, manager.isPowerSaveMode)) }
        fun readBattery(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) battery = level * 100 / scale
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { readBattery(intent); publish() }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        readBattery(context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED))
        publish()
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private val backgroundStatus = MutableStateFlow(false to (null as String?))
    val state = combine(clock, weather.state, settings, power, backgroundStatus) { now, weather, settings, power, background ->
        SpaceState(now, weather, settings, power, background.first, background.second,settingsReady=true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        SpaceState(ZonedDateTime.now(), weather.state.value, SpaceSettings(), DevicePower()))

    fun selectWeather(value: WeatherTheme) { viewModelScope.launch { preferences.setWeather(value) } }
    fun selectPeriod(value: DayPeriod?) { viewModelScope.launch { preferences.setPeriod(value) } }
    fun selectQuality(value: GraphicsQuality) { viewModelScope.launch { preferences.setQuality(value) } }
    fun setReducedMotion(value: Boolean) { viewModelScope.launch { preferences.setReducedMotion(value) } }
    fun setNightMode(value:Boolean) { viewModelScope.launch { preferences.setNightMode(value) } }
    private var refreshJob: Job? = null
    private var lastRefresh = 0L
    fun refreshWeather(force: Boolean = false) {
        if(refreshJob?.isActive == true) return
        val now = android.os.SystemClock.elapsedRealtime()
        if(!force && state.value.weather.hasData && !state.value.weather.isStale && now-lastRefresh<15*60_000) return
        refreshJob=viewModelScope.launch { weather.refresh();if(weather.state.value.hasData && weather.state.value.message==null) lastRefresh=android.os.SystemClock.elapsedRealtime() }
    }

    suspend fun refreshWeatherNow() { refreshWeather(true);refreshJob?.join() }
    fun setBackground(source:String?) {
        if(backgroundStatus.value.first) return
        backgroundStatus.value=true to null
        viewModelScope.launch {
            try { preferences.setBackground(source);backgroundStatus.value=false to null }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { backgroundStatus.value=false to "图片未能导入，请选择另一张图片。" }
            finally { if(backgroundStatus.value.first) backgroundStatus.value=false to backgroundStatus.value.second }
        }
    }
}
