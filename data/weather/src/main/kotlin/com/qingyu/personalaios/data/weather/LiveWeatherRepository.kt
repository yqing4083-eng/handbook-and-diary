package com.qingyu.personalaios.data.weather

import android.content.Context
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.Instant
import org.json.JSONObject

interface WeatherCache { fun read(): WeatherData?; suspend fun save(data: WeatherData) }
class DeviceWeatherCache(context: Context): WeatherCache {
    private val prefs = context.getSharedPreferences("current-weather",Context.MODE_PRIVATE)
    override fun read(): WeatherData? = runCatching {
        val v = JSONObject(prefs.getString("data", null) ?: return null)
        WeatherData(v.getString("city"),WeatherTheme.valueOf(v.getString("theme")),v.getString("description"),
            v.getInt("temp"),v.getInt("feels"),v.getInt("humidity"),v.getString("wind"),v.getInt("level"),
            v.getDouble("precipitation").toFloat(),Instant.ofEpochSecond(v.getLong("observed")),cityLevel=v.optString("cityLevel").takeIf { it.isNotBlank() && it!="null" }?.let(WeatherLocality::city),countryCode=WeatherCountry.normalize(v.optString("countryCode")))
    }.getOrNull()
    override suspend fun save(data: WeatherData) = withContext(Dispatchers.IO) {
        val v = JSONObject().put("city",data.city).put("theme",data.theme.name).put("description",data.description)
            .put("temp",data.temperature).put("feels",data.feelsLike).put("humidity",data.humidity)
            .put("wind",data.windDirection).put("level",data.windLevel).put("precipitation",data.precipitationMm)
            .put("observed",data.observedAt.epochSecond)
            .put("cityLevel",data.cityLevel ?: JSONObject.NULL)
            .put("countryCode",data.countryCode ?: JSONObject.NULL)
        check(prefs.edit().putString("data",v.toString()).commit())
    }
}
class LiveWeatherRepository(private val location: LocationSource, private val source: CurrentWeatherDataSource,
    private val cache: WeatherCache): WeatherRepository {
    private val saved = cache.read()
    @Volatile private var locatedCountry= saved?.countryCode
    override val countryCode:String? get()=locatedCountry
    private val empty = WeatherData("当前位置",WeatherTheme.CLOUDY,"等待定位",0,0,0,"",0,0f,Instant.EPOCH)
    private val mutable = MutableStateFlow(WeatherState(saved ?: empty,WeatherSource.OPEN_METEO,isStale=saved!=null,
        hasData=saved!=null,message=if(saved==null) "开启定位，获取当前天气" else null))
    override val state: StateFlow<WeatherState> = mutable.asStateFlow()
    private val mutex = Mutex()
    override suspend fun refresh() {
        if(!mutex.tryLock()) return
        mutable.value=mutable.value.copy(loading=true,message=null)
        try {
            val position=location.current()
            // Routing learns the latest location even if the weather provider then fails.
            locatedCountry=WeatherCountry.normalize(position.countryCode)
            val data = source.fetch(position).copy(countryCode=locatedCountry)
            mutable.value=WeatherState(data,WeatherSource.OPEN_METEO)
            try { cache.save(data) } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { /* Fresh weather still remains usable. */ }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            val message = when(error) {
                is SecurityException -> "未授权定位，请开启定位天气"
                is IllegalStateException -> error.message ?: "暂时无法获取位置"
                else -> "天气暂时未能同步，下拉可重试"
            }
            mutable.value=mutable.value.copy(isStale=mutable.value.hasData,message=message)
        } finally { mutable.value=mutable.value.copy(loading=false);mutex.unlock() }
    }
}
