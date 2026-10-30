package com.qingyu.personalaios.domain

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

enum class WeatherTheme { CLEAR, CLOUDY, OVERCAST, LIGHT_RAIN, HEAVY_RAIN, THUNDER, SNOW, FOG }
enum class WeatherFamily { CLEAR, CLOUDY, RAIN, SNOW }
enum class WeatherSource { MOCK, QWEATHER, OPEN_METEO }

data class WeatherData(
    val city: String,
    val theme: WeatherTheme,
    val description: String,
    val temperature: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windDirection: String,
    val windLevel: Int,
    val precipitationMm: Float,
    val observedAt: Instant,
    val alerts: List<String> = emptyList(),
    val cityLevel: String? = null,
    val countryCode: String? = null,
)

/** Source and freshness belong to the state, never inferred from a pretty weather icon. */
data class WeatherState(val data: WeatherData, val source: WeatherSource, val isStale: Boolean = false,
    val hasData: Boolean = true, val loading: Boolean = false, val message: String? = null)

interface WeatherRepository {
    val state: StateFlow<WeatherState>
    val countryCode:String? get()=state.value.data.countryCode
    suspend fun refresh() = Unit
}

data class GeoPosition(val latitude: Double, val longitude: Double, val city: String, val cityLevel: String? = null,val countryCode:String?=null)
interface LocationSource { suspend fun current(): GeoPosition }

interface WeatherPreviewController {
    fun select(theme: WeatherTheme)
}

enum class GraphicsQuality { HIGH, MEDIUM, LOW }
enum class DayPeriod { MORNING, DAY, SUNSET, NIGHT }
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
enum class ParticleType { LIGHT, MIST, RAIN, SNOW }

data class ParticleConfig(
    val type: ParticleType,
    val count: Int,
    val minSize: Float,
    val maxSize: Float,
    val minSpeed: Float,
    val maxSpeed: Float,
    val blurAmount: Float,
    val windX: Float,
)
