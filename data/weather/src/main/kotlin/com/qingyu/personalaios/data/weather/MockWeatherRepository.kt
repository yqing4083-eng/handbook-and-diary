package com.qingyu.personalaios.data.weather

import com.qingyu.personalaios.domain.*
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface WeatherDataSource {
    fun preview(theme: WeatherTheme): WeatherState
}

class MockWeatherDataSource(private val clock: Clock = Clock.systemUTC()) : WeatherDataSource {
    override fun preview(theme: WeatherTheme): WeatherState {
        val data = when (theme) {
            WeatherTheme.CLEAR -> WeatherData("杭州", theme, "晴", 24, 25, 48, "东南风", 2, 0f, clock.instant())
            WeatherTheme.CLOUDY, WeatherTheme.OVERCAST, WeatherTheme.FOG ->
                WeatherData("杭州", theme, "多云", 18, 17, 64, "东风", 2, 0f, clock.instant())
            WeatherTheme.SNOW -> WeatherData("杭州", theme, "小雪", -2, -5, 78, "北风", 2, .4f, clock.instant())
            else -> WeatherData("杭州", theme, "小雨", 13, 11, 88, "东北风", 3, 1.2f, clock.instant())
        }
        return WeatherState(data, WeatherSource.MOCK)
    }
}

class MockWeatherRepository(private val source: WeatherDataSource) : WeatherRepository, WeatherPreviewController {
    private val mutableState = MutableStateFlow(source.preview(WeatherTheme.SNOW))
    override val state: StateFlow<WeatherState> = mutableState.asStateFlow()
    override fun select(theme: WeatherTheme) { mutableState.value = source.preview(theme) }
}
