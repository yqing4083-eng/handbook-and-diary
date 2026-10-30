package com.qingyu.personalaios.feature.today

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nlf.calendar.Solar
import com.qingyu.personalaios.domain.*
import java.time.ZonedDateTime
import kotlin.random.Random

/** Native port of the supplied weather-copy-matcher_v3.js; source JSON stays unchanged. */
object WeatherCopy {
    data class Match(val text:String,val weather:String,val timeSlot:String,val season:String,
        val temperatureTriggered:Boolean,val sourceCount:Int)

    private val data:JsonObject by lazy {
        checkNotNull(WeatherCopy::class.java.getResourceAsStream("/weather_copy_auto_v3.json"))
            .bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }.also { root ->
                val groups=root.getAsJsonObject("copy")
                check(groups.size()==10 && groups.entrySet().all { (_,slots) ->
                    slots.asJsonObject.size()==6 && slots.asJsonObject.entrySet().all { (_,pool) -> pool.asJsonArray.size()==30 }
                }) { "Incomplete automatic weather copy v3" }
            }
    }
    fun normalizeWeather(input:String):String {
        val raw=input.trim()
        return data.getAsJsonObject("weatherAliases").get(raw)?.asString ?: raw.ifBlank { "多云" }
    }
    fun timeSlot(hour:Int,minute:Int=0):String = when(hour*60+minute) {
        in 300..539 -> "清晨"
        in 540..719 -> "上午"
        in 720..839 -> "中午"
        in 840..1049 -> "下午"
        in 1050..1229 -> "傍晚"
        else -> "深夜"
    }
    fun temperatureBand(temperature:Double?):String? = when {
        temperature==null || !temperature.isFinite() -> null
        temperature<=0 -> "极寒"
        temperature<=8 -> "寒冷"
        temperature>=36 -> "酷热"
        temperature>=32 -> "高温"
        else -> null
    }
    fun entries(weather:String,slot:String):List<String> {
        val groups=data.getAsJsonObject("copy")
        val group=groups.getAsJsonObject(normalizeWeather(weather)) ?: groups.getAsJsonObject("多云")
        return (group.getAsJsonArray(slot) ?: groups.getAsJsonObject("多云").getAsJsonArray(slot))
            ?.map { it.asString }.orEmpty()
    }
    private fun phrases(section:String,key:String):List<String> =
        data.getAsJsonObject(section).getAsJsonArray(key)?.map { it.asString }.orEmpty()

    /** Extra priority and 55% / 80% / 20% gates mirror the user's JavaScript matcher. */
    fun match(weather:String,hour:Int,minute:Int=0,temperature:Double?=null,month:Int=1,
        holiday:String="",mood:String="",includeSeason:Boolean=true,includeHoliday:Boolean=true,
        includeMood:Boolean=true,includeTemperature:Boolean=true,random:Random=Random.Default):Match {
        val normalized=normalizeWeather(weather)
        val slot=timeSlot(hour,minute)
        val season=data.getAsJsonObject("seasonByMonth").get(month.toString())?.asString ?: "春季"
        val pool=entries(normalized,slot)
        fun pick(values:List<String>)=if(values.isEmpty()) "" else values[random.nextInt(values.size)]
        val base=pick(pool)
        val extra=mutableListOf<String>()
        val band=temperatureBand(temperature).takeIf { includeTemperature }
        if(band!=null) extra+=pick(data.getAsJsonObject("temperatureRules").getAsJsonObject(band)
            .getAsJsonArray("phrases").map { it.asString })
        val moods=phrases("moodPhrases",mood)
        if(includeMood && moods.isNotEmpty() && random.nextDouble()<.55) extra+=pick(moods)
        val holidays=phrases("holidayPhrases",holiday)
        if(includeHoliday && holidays.isNotEmpty() && random.nextDouble()<.8) extra+=pick(holidays)
        val seasons=phrases("seasonPhrases",season)
        if(includeSeason && seasons.isNotEmpty() && random.nextDouble()<.2) extra+=pick(seasons)
        val text=(base+extra.firstOrNull().orEmpty()).replace(Regex("。。+"),"。").replace("，。","。").trim()
        return Match(text,normalized,slot,season,band!=null,pool.size)
    }
    fun category(weather:WeatherData):String {
        val described=normalizeWeather(weather.description)
        return when {
            weather.theme==WeatherTheme.THUNDER -> "雷阵雨"
            weather.theme==WeatherTheme.SNOW -> "下雪"
            weather.theme==WeatherTheme.HEAVY_RAIN -> "中到大雨"
            weather.theme==WeatherTheme.LIGHT_RAIN -> if(described=="中到大雨") described else "小雨"
            weather.precipitationMm>0f -> "小雨"
            weather.windLevel>=6 -> "大风"
            described=="闷热" || weather.temperature>=32 && weather.humidity>=70 -> "闷热"
            else -> when(weather.theme) {
                WeatherTheme.CLEAR -> "晴朗"
                WeatherTheme.CLOUDY -> "多云"
                WeatherTheme.OVERCAST -> "阴天"
                WeatherTheme.FOG -> "雾"
                else -> "多云"
            }
        }
    }
    fun select(weather:WeatherData,now:ZonedDateTime):String {
        val kind=category(weather)
        val solar=Solar.fromYmd(now.year,now.monthValue,now.dayOfMonth)
        val holiday=(solar.festivals+solar.lunar.festivals)
            .firstOrNull { phrases("holidayPhrases",it).isNotEmpty() }.orEmpty()
        // Keep copy stable through clock recompositions; do not invent an inferred user mood.
        val seed="${now.toLocalDate()}|$kind|${timeSlot(now.hour,now.minute)}|${temperatureBand(weather.temperature.toDouble())}|$holiday".hashCode()
        return match(kind,now.hour,now.minute,weather.temperature.toDouble(),now.monthValue,
            holiday=holiday,random=Random(seed)).text
    }
}
