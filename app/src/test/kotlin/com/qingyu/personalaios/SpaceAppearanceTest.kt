package com.qingyu.personalaios

import com.qingyu.personalaios.core.theme.*
import com.qingyu.personalaios.data.local.SpaceSettings
import com.qingyu.personalaios.domain.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class SpaceAppearanceTest {
    @Test fun dayBrightensEveryWeatherWhilePreservingTheNightTheme() {
        for(weather in WeatherTheme.entries) for(hour in listOf(6,12,18,23)) {
            val now=ZonedDateTime.parse("2026-10-13T12:00:00+08:00").withHour(hour)
            val data=WeatherData("杭州",weather,"",20,20,70,"北",1,0f,now.toInstant())
            val night=SpaceState(now,WeatherState(data,WeatherSource.OPEN_METEO),
                SpaceSettings(nightMode=true),DevicePower())
            val expected=ThemeEngine.map(ThemeInput(weather,ThemeEngine.period(now),
                ThemeEngine.season(now),systemDark=true)).copy(darkBackground=true,foreground=0xFFF5F7FAL)
            assertEquals(expected,night.theme())
            val day=night.copy(settings=night.settings.copy(nightMode=false)).theme()
            assertEquals(0xFF000000L,day.foreground)
            assertFalse(day.darkBackground)
            assertEquals(expected.particles,day.particles)
            for(background in listOf(day.backgroundTop,day.backgroundBottom)) {
                val channels=listOf(16,8,0).map { ((background shr it) and 255).toInt() }
                assertTrue("Dark daytime background: $weather $hour",channels.min()>195)
            }
        }
    }
}
