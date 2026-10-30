package com.qingyu.personalaios.data.weather

import com.google.gson.JsonParser
import com.qingyu.personalaios.domain.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OpenMeteoTest {
    private val fixture="""{"current":{"time":1791218700,"temperature_2m":15.1,"relative_humidity_2m":73,"apparent_temperature":14.2,"precipitation":0,"weather_code":0,"wind_speed_10m":6.4,"wind_direction_10m":324}}"""
    private val data get()=OpenMeteoMapper.map(JsonParser.parseString(fixture).asJsonObject,"杭州")
    private class Cache(var value: WeatherData?=null): WeatherCache {
        override fun read()=value
        override suspend fun save(data: WeatherData) { value=data }
    }
    @Test fun parsesLiveResponseUnitsAndObservationTime() {
        assertEquals(15,data.temperature);assertEquals(14,data.feelsLike);assertEquals(73,data.humidity)
        assertEquals("西北风",data.windDirection);assertEquals(2,data.windLevel)
        assertEquals(Instant.ofEpochSecond(1791218700),data.observedAt)
        assertEquals(WeatherTheme.CLEAR,data.theme)
    }
    @Test fun mapsSnowStormFogAndRainCodes() {
        assertEquals(WeatherTheme.SNOW,OpenMeteoMapper.condition(85).first)
        assertEquals(WeatherTheme.THUNDER,OpenMeteoMapper.condition(99).first)
        assertEquals(WeatherTheme.FOG,OpenMeteoMapper.condition(48).first)
        assertEquals(WeatherTheme.HEAVY_RAIN,OpenMeteoMapper.condition(82).first)
        assertEquals(12,OpenMeteoMapper.windLevel(130f))
    }
    @Test fun missingFieldsAreNotConvertedToFakeZeroWeather() {
        assertThrows(IllegalStateException::class.java) { OpenMeteoMapper.map(JsonParser.parseString("{}").asJsonObject,"杭州") }
    }
    @Test fun successfulFetchReplacesCacheAndChangesTheTheme()=runTest {
        val cache=Cache(data.copy(theme=WeatherTheme.SNOW));val source=object: CurrentWeatherDataSource { override suspend fun fetch(position: GeoPosition)=data }
        val location=object: LocationSource { override suspend fun current()=GeoPosition(30.0,120.0,"杭州") }
        val repo=LiveWeatherRepository(location,source,cache)
        assertTrue(repo.state.value.isStale);repo.refresh()
        assertFalse(repo.state.value.isStale);assertEquals(data,cache.value);assertEquals(WeatherSource.OPEN_METEO,repo.state.value.source)
    }
    @Test fun deniedPermissionNeverMakesANetworkRequest()=runTest {
        var calls=0
        val source=object: CurrentWeatherDataSource { override suspend fun fetch(position: GeoPosition): WeatherData { calls++;return data } }
        val location=object: LocationSource { override suspend fun current(): GeoPosition { throw SecurityException() } }
        val repo=LiveWeatherRepository(location,source,Cache());repo.refresh()
        assertEquals(0,calls);assertFalse(repo.state.value.hasData);assertFalse(repo.state.value.loading);assertNotNull(repo.state.value.message)
    }
    @Test fun networkFailurePreservesPreviousWeatherWithStaleLabel()=runTest {
        val source=object: CurrentWeatherDataSource { override suspend fun fetch(position: GeoPosition): WeatherData { throw java.io.IOException() } }
        val location=object: LocationSource { override suspend fun current()=GeoPosition(30.0,120.0,"杭州") }
        val repo=LiveWeatherRepository(location,source,Cache(data));repo.refresh()
        assertEquals(data,repo.state.value.data);assertTrue(repo.state.value.isStale);assertNotNull(repo.state.value.message)
    }
    @Test fun successfulLocationIsAvailableForRoutingEvenIfForecastFails()=runTest {
        val source=object:CurrentWeatherDataSource {override suspend fun fetch(position:GeoPosition):WeatherData=throw java.io.IOException()}
        val location=object:LocationSource {override suspend fun current()=GeoPosition(0.0,0.0,"测试城市",countryCode="JP")}
        val repo=LiveWeatherRepository(location,source,Cache(data.copy(countryCode="CN")))
        repo.refresh();assertEquals("JP",repo.countryCode);assertTrue(repo.state.value.isStale)
    }
    @Test fun countryFromExistingLocationIsPersistedWithoutCoordinates()=runTest {
        val cache=Cache();val source=object:CurrentWeatherDataSource {override suspend fun fetch(position:GeoPosition)=data}
        val location=object:LocationSource {override suspend fun current()=GeoPosition(0.0,0.0,"测试城市",countryCode="cn")}
        val repo=LiveWeatherRepository(location,source,cache);repo.refresh()
        assertEquals("CN",repo.countryCode);assertEquals("CN",cache.value?.countryCode)
    }
}
