package com.qingyu.personalaios.data.weather

import com.google.gson.JsonObject
import com.qingyu.personalaios.domain.*
import java.time.Instant
import kotlin.math.roundToInt
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface CurrentWeatherDataSource { suspend fun fetch(position: GeoPosition): WeatherData }
interface OpenMeteoApi {
    @GET("v1/forecast") suspend fun current(@Query("latitude") latitude: Double, @Query("longitude") longitude: Double,
        @Query("current") variables: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m",
        @Query("timeformat") time: String = "unixtime", @Query("timezone") zone: String = "UTC"): JsonObject
}
class OpenMeteoDataSource(private val api: OpenMeteoApi = Retrofit.Builder()
    .baseUrl("https://api.open-meteo.com/")
    .client(OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build())
    .addConverterFactory(GsonConverterFactory.create()).build().create(OpenMeteoApi::class.java)) : CurrentWeatherDataSource {
    override suspend fun fetch(position: GeoPosition) = OpenMeteoMapper.map(api.current(position.latitude, position.longitude), position.city, position.cityLevel).copy(countryCode=position.countryCode)
}
object OpenMeteoMapper {
    fun condition(code: Int): Pair<WeatherTheme, String> = when(code) {
        0 -> WeatherTheme.CLEAR to "晴"
        1, 2 -> WeatherTheme.CLOUDY to "多云"
        3 -> WeatherTheme.OVERCAST to "阴"
        45, 48 -> WeatherTheme.FOG to "雾"
        51, 53, 55, 56, 57 -> WeatherTheme.LIGHT_RAIN to "毛毛雨"
        61, 80 -> WeatherTheme.LIGHT_RAIN to "小雨"
        63, 81 -> WeatherTheme.LIGHT_RAIN to "中雨"
        65, 82 -> WeatherTheme.HEAVY_RAIN to "大雨"
        66, 67 -> WeatherTheme.LIGHT_RAIN to "冻雨"
        71, 73, 75, 77, 85, 86 -> WeatherTheme.SNOW to "雪"
        95, 96, 99 -> WeatherTheme.THUNDER to "雷雨"
        else -> WeatherTheme.CLOUDY to "天气未知"
    }
    fun windLevel(kmh: Float): Int = floatArrayOf(1f,6f,12f,20f,29f,39f,50f,62f,75f,89f,103f,118f)
        .indexOfFirst { kmh < it }.let { if(it < 0) 12 else it }
    fun map(json: JsonObject, city: String, cityLevel: String? = null): WeatherData {
        val c = json.getAsJsonObject("current") ?: error("Missing current weather")
        fun number(name: String) = c.get(name)?.takeUnless { it.isJsonNull }?.asFloat?.also { require(it.isFinite()) } ?: error("Missing $name")
        val (theme, description) = condition(number("weather_code").roundToInt())
        val directions = listOf("北风","东北风","东风","东南风","南风","西南风","西风","西北风")
        val wind = number("wind_speed_10m")
        return WeatherData(city,theme,description,number("temperature_2m").roundToInt(),number("apparent_temperature").roundToInt(),
            number("relative_humidity_2m").roundToInt().coerceIn(0,100),
            directions[((number("wind_direction_10m")+22.5f)/45).toInt().mod(8)],windLevel(wind),
            number("precipitation").coerceAtLeast(0f), Instant.ofEpochSecond(c.get("time").asLong),cityLevel=WeatherLocality.city(cityLevel))
    }
}
