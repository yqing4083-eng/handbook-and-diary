package com.qingyu.personalaios.core.theme

import com.qingyu.personalaios.domain.*
import java.time.ZonedDateTime

/** Linear channel interpolation; colors are platform independent ARGB tokens. */
fun mixColor(a: Long, b: Long, amount: Float): Long {
    val t = amount.coerceIn(0f, 1f)
    var result = 0L
    for (shift in listOf(24, 16, 8, 0)) {
        val from = ((a shr shift) and 255).toInt()
        val to = ((b shr shift) and 255).toInt()
        result = result or ((from + (to - from) * t).toLong() shl shift)
    }
    return result
}

object WeatherThemeMapper {
    fun family(theme: WeatherTheme): WeatherFamily = when (theme) {
        WeatherTheme.CLEAR -> WeatherFamily.CLEAR
        WeatherTheme.CLOUDY, WeatherTheme.OVERCAST, WeatherTheme.FOG -> WeatherFamily.CLOUDY
        WeatherTheme.LIGHT_RAIN, WeatherTheme.HEAVY_RAIN, WeatherTheme.THUNDER -> WeatherFamily.RAIN
        WeatherTheme.SNOW -> WeatherFamily.SNOW
    }
}

data class ThemeInput(
    val weather: WeatherTheme,
    val period: DayPeriod,
    val season: Season,
    val quality: GraphicsQuality = GraphicsQuality.HIGH,
    val batteryPercent: Int = 100,
    val powerSave: Boolean = false,
    val systemDark: Boolean = false,
    val reducedMotion: Boolean = false,
)

data class ThemeTokens(
    val backgroundTop: Long,
    val backgroundBottom: Long,
    val foreground: Long,
    val atmosphere: Long,
    val glassTint: Long,
    val glassRefraction: Float,
    val glassHighlight: Float,
    val blurRadius: Float,
    val chromaticAberration: Float,
    val distortionSamples: Int,
    val particles: ParticleConfig,
    val glowIntensity: Float,
    val darkBackground: Boolean,
    val effectiveQuality: GraphicsQuality,
    val motionEnabled: Boolean,
)

object ThemeEngine {
    fun period(now: ZonedDateTime): DayPeriod = when (now.hour) {
        in 5..8 -> DayPeriod.MORNING
        in 9..16 -> DayPeriod.DAY
        in 17..18 -> DayPeriod.SUNSET
        else -> DayPeriod.NIGHT
    }

    /** Calendar seasons for the current northern-hemisphere mock city, Hangzhou. */
    fun season(now: ZonedDateTime): Season = when (now.monthValue) {
        in 3..5 -> Season.SPRING
        in 6..8 -> Season.SUMMER
        in 9..11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    fun map(input: ThemeInput): ThemeTokens {
        val family = WeatherThemeMapper.family(input.weather)
        val dark = input.period == DayPeriod.NIGHT || input.systemDark || family == WeatherFamily.RAIN
        val base = when (family) {
            WeatherFamily.CLEAR -> if (dark) 0xFF172B47L to 0xFF091729L else 0xFFBAD9E9L to 0xFFF3EEE2L
            WeatherFamily.CLOUDY -> if (dark) 0xFF283D51L to 0xFF111D2BL else 0xFFACBFCCL to 0xFFE0E5E7L
            WeatherFamily.RAIN -> 0xFF304959L to 0xFF0D1C2DL
            WeatherFamily.SNOW -> if (dark) 0xFF294D72L to 0xFF0C1C34L else 0xFFBDD5E8L to 0xFFE6EEF2L
        }
        val seasonalTint = when (input.season) {
            Season.SPRING -> 0xFFB1D6CCL
            Season.SUMMER -> 0xFF95C9E6L
            Season.AUTUMN -> 0xFFD8B99BL
            Season.WINTER -> 0xFF8DB1DCL
        }
        val daylightTint = when (input.period) {
            DayPeriod.MORNING -> 0xFFFFE4C3L
            DayPeriod.DAY -> 0xFFD8F1FFL
            DayPeriod.SUNSET -> 0xFFE8B297L
            DayPeriod.NIGHT -> 0xFF152B4AL
        }
        val quality = when {
            input.quality == GraphicsQuality.LOW -> GraphicsQuality.LOW
            input.powerSave || input.batteryPercent <= 15 -> GraphicsQuality.MEDIUM
            else -> input.quality
        }
        val factor = when (quality) { GraphicsQuality.HIGH -> 1f; GraphicsQuality.MEDIUM -> .58f; GraphicsQuality.LOW -> .26f }
        val type = when (family) {
            WeatherFamily.CLEAR -> ParticleType.LIGHT
            WeatherFamily.CLOUDY -> ParticleType.MIST
            WeatherFamily.RAIN -> ParticleType.RAIN
            WeatherFamily.SNOW -> ParticleType.SNOW
        }
        val maxCount = when (family) { WeatherFamily.CLEAR -> 14; WeatherFamily.CLOUDY -> 5; WeatherFamily.RAIN -> 100; WeatherFamily.SNOW -> 140 }
        val particles = ParticleConfig(type, (maxCount * factor).toInt().coerceAtLeast(2),
            .7f, if (family == WeatherFamily.SNOW) 4.6f else 2.2f,
            if (family == WeatherFamily.RAIN) .35f else .025f,
            if (family == WeatherFamily.RAIN) .8f else .09f,
            if (quality == GraphicsQuality.LOW) 0f else .55f,
            if (family == WeatherFamily.RAIN) -.03f else .016f)
        return ThemeTokens(
            mixColor(mixColor(base.first, seasonalTint, if (dark) .035f else .08f), daylightTint, if (dark) .045f else .12f),
            mixColor(base.second, seasonalTint, if (dark) .02f else .025f),
            if (dark) 0xFFF0F5F9L else 0xFF233B4CL,
            mixColor(seasonalTint, daylightTint, .3f),
            if (dark) 0xFFB4D3ECL else 0xFFFFFFFFL,
            .045f * factor, .42f, 18f * factor, .75f * factor,
            when (quality) { GraphicsQuality.HIGH -> 12; GraphicsQuality.MEDIUM -> 8; GraphicsQuality.LOW -> 4 },
            particles, if (family == WeatherFamily.CLEAR) .5f else .24f,
            dark, quality, !input.reducedMotion,
        )
    }
}
