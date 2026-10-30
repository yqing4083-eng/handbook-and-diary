package com.qingyu.personalaios.core.theme

import com.qingyu.personalaios.domain.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ThemeEngineTest {
    private val input = ThemeInput(WeatherTheme.SNOW, DayPeriod.NIGHT, Season.AUTUMN)
    @Test fun allWeatherStatesHaveAVisualFamilyAndParticles() {
        WeatherTheme.entries.forEach {
            val theme = ThemeEngine.map(input.copy(weather = it))
            assertTrue(theme.particles.count > 0)
            assertTrue(theme.particles.minSpeed <= theme.particles.maxSpeed)
        }
        assertEquals(WeatherFamily.RAIN, WeatherThemeMapper.family(WeatherTheme.THUNDER))
        assertEquals(WeatherFamily.CLOUDY, WeatherThemeMapper.family(WeatherTheme.FOG))
    }
    @Test fun weatherTimeAndSeasonEachChangePalette() {
        val rain = input.copy(weather = WeatherTheme.LIGHT_RAIN)
        assertNotEquals(ThemeEngine.map(rain).backgroundTop, ThemeEngine.map(rain.copy(period = DayPeriod.DAY)).backgroundTop)
        assertNotEquals(ThemeEngine.map(rain).backgroundTop, ThemeEngine.map(rain.copy(season = Season.SUMMER)).backgroundTop)
        assertNotEquals(ThemeEngine.map(input).backgroundTop, ThemeEngine.map(rain).backgroundTop)
    }
    @Test fun qualityAndPowerSavingReduceWorkWithoutRemovingAtmosphere() {
        val high = ThemeEngine.map(input)
        val medium = ThemeEngine.map(input.copy(quality = GraphicsQuality.MEDIUM))
        val low = ThemeEngine.map(input.copy(quality = GraphicsQuality.LOW))
        assertTrue(high.particles.count > medium.particles.count)
        assertTrue(medium.particles.count > low.particles.count)
        assertTrue(high.distortionSamples > low.distortionSamples)
        assertTrue(low.glassRefraction > 0f)
        assertEquals(medium.effectiveQuality, ThemeEngine.map(input.copy(powerSave = true)).effectiveQuality)
        assertEquals(GraphicsQuality.LOW, ThemeEngine.map(input.copy(quality = GraphicsQuality.LOW, powerSave = true)).effectiveQuality)
        assertEquals(GraphicsQuality.MEDIUM, ThemeEngine.map(input.copy(batteryPercent = 15)).effectiveQuality)
    }
    @Test fun clockBoundariesAndSeasonsUseLocalDateTime() {
        val date = ZonedDateTime.parse("2026-10-04T04:59:00+08:00")
        assertEquals(DayPeriod.NIGHT, ThemeEngine.period(date))
        assertEquals(DayPeriod.MORNING, ThemeEngine.period(date.plusMinutes(1)))
        assertEquals(DayPeriod.DAY, ThemeEngine.period(date.withHour(9)))
        assertEquals(DayPeriod.SUNSET, ThemeEngine.period(date.withHour(18)))
        assertEquals(DayPeriod.NIGHT, ThemeEngine.period(date.withHour(19)))
        assertEquals(Season.AUTUMN, ThemeEngine.season(date))
        assertEquals(Season.WINTER, ThemeEngine.season(date.withMonth(1)))
    }
    @Test fun reducedMotionIsStaticAndColorMixKeepsEndpoints() {
        assertFalse(ThemeEngine.map(input.copy(reducedMotion = true)).motionEnabled)
        assertEquals(0xFF000000L, mixColor(0xFF000000L, 0xFFFFFFFFL, 0f))
        assertEquals(0xFFFFFFFFL, mixColor(0xFF000000L, 0xFFFFFFFFL, 1f))
    }
    @Test fun primaryTextHasContrastOnBothGradientEndpointsInEveryTheme() {
        fun luminance(color: Long): Double {
            fun channel(shift: Int): Double {
                val c = ((color shr shift) and 255) / 255.0
                return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        WeatherTheme.entries.forEach { weather ->
            DayPeriod.entries.forEach { period ->
                Season.entries.forEach { season ->
                    listOf(false, true).forEach { dark ->
                        val tokens = ThemeEngine.map(input.copy(weather = weather, period = period, season = season, systemDark = dark))
                        listOf(tokens.backgroundTop, tokens.backgroundBottom).forEach { background ->
                            val a = luminance(tokens.foreground)
                            val b = luminance(background)
                            assertTrue("$weather / $period / $season / dark=$dark", (maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 4.5)
                        }
                    }
                }
            }
        }
    }
}
