package com.qingyu.personalaios.feature.today

import com.qingyu.personalaios.domain.*
import java.time.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WeatherCopyTest {
    private val weather=WeatherData("杭州",WeatherTheme.CLEAR,"晴",12,10,60,"北风",2,0f,Instant.EPOCH)
    private val now=ZonedDateTime.parse("2026-10-06T20:00:00+08:00")
    private val kinds=listOf("晴朗","多云","阴天","小雨","中到大雨","雷阵雨","下雪","雾","大风","闷热")
    private val slots=listOf("清晨","上午","中午","下午","傍晚","深夜")
    private fun first()=object:Random() { override fun nextBits(bitCount:Int)=0 }

    @Test fun all60SuppliedGroupsHave30EntriesAndOnlyRainContainsTravelAdvice() {
        val travel=Regex("骑行|驾车|驾驶|路况|积水|湿滑|过路口|长途出行|提前出门|预留.{0,4}时间")
        for(kind in kinds) for(slot in slots) {
            val entries=WeatherCopy.entries(kind,slot)
            assertEquals(30,entries.size)
            if(kind !in listOf("小雨","中到大雨","雷阵雨")) assertFalse("$kind / $slot",entries.any { travel.containsMatchIn(it) })
        }
        assertTrue(WeatherCopy.entries("中到大雨","下午").any { travel.containsMatchIn(it) })
    }
    @Test fun minuteBoundariesAndOvernightRangeMatchTheSuppliedMatcher() {
        for((time,expected) in mapOf("04:59" to "深夜","05:00" to "清晨","08:59" to "清晨",
            "09:00" to "上午","11:59" to "上午","12:00" to "中午","13:59" to "中午",
            "14:00" to "下午","17:29" to "下午","17:30" to "傍晚","20:29" to "傍晚","20:30" to "深夜","00:00" to "深夜")) {
            val parts=time.split(':').map(String::toInt)
            assertEquals(time,expected,WeatherCopy.timeSlot(parts[0],parts[1]))
        }
    }
    @Test fun temperatureOnlyTriggersAtTheNewExtremeThresholds() {
        assertEquals("极寒",WeatherCopy.temperatureBand(0.0))
        assertEquals("寒冷",WeatherCopy.temperatureBand(.01))
        assertEquals("寒冷",WeatherCopy.temperatureBand(8.0))
        assertNull(WeatherCopy.temperatureBand(8.1));assertNull(WeatherCopy.temperatureBand(31.9))
        assertEquals("高温",WeatherCopy.temperatureBand(32.0))
        assertEquals("高温",WeatherCopy.temperatureBand(35.99))
        assertEquals("酷热",WeatherCopy.temperatureBand(36.0))
        assertNull(WeatherCopy.temperatureBand(null));assertNull(WeatherCopy.temperatureBand(Double.NaN))
        val ordinary=WeatherCopy.match("晴",12,temperature=20.0,includeSeason=false,random=first())
        assertFalse(ordinary.temperatureTriggered);assertEquals(WeatherCopy.entries("晴朗","中午").first(),ordinary.text)
    }
    @Test fun oneAddonUsesTemperatureThenMoodThenHolidayThenSeason() {
        val base=WeatherCopy.entries("小雨","傍晚").first()
        fun matched(temp:Double?,mood:String="",holiday:String="")=WeatherCopy.match("小雨",18,40,temp,10,holiday,mood,random=first())
        assertEquals(base+"今天不用太用力，平静本身就很好。",matched(12.0,mood="平静",holiday="国庆节").text)
        assertEquals(base+"气温已经跌到零度附近，别让冷风钻进衣领。",matched(-1.0,mood="平静",holiday="国庆节").text)
        assertEquals(base+"假日的天气最适合放慢节奏，好好看看路上的风景。",matched(12.0,holiday="国庆节").text)
        assertEquals(base+"秋天的空气开始有一点清透感。",matched(12.0).text)
        val disabled=WeatherCopy.match("小雨",18,40,-1.0,10,"国庆节","平静",false,false,false,false,first())
        assertEquals(base,disabled.text);assertFalse(disabled.temperatureTriggered)
    }
    @Test fun aliasesFallbackAndActualRainIntensityAreRespected() {
        assertEquals("中到大雨",WeatherCopy.normalizeWeather("heavy rain"))
        assertEquals("小雨",WeatherCopy.normalizeWeather("毛毛雨"))
        assertEquals(WeatherCopy.entries("多云","上午"),WeatherCopy.entries("未知天气","上午"))
        assertEquals("雷阵雨",WeatherCopy.category(weather.copy(theme=WeatherTheme.THUNDER,windLevel=7)))
        assertEquals("下雪",WeatherCopy.category(weather.copy(theme=WeatherTheme.SNOW,windLevel=7)))
        assertEquals("中到大雨",WeatherCopy.category(weather.copy(theme=WeatherTheme.LIGHT_RAIN,description="中雨")))
        assertEquals("小雨",WeatherCopy.category(weather.copy(precipitationMm=1f,windLevel=7)))
        assertEquals("大风",WeatherCopy.category(weather.copy(windLevel=7)))
        assertEquals("闷热",WeatherCopy.category(weather.copy(temperature=34,humidity=80)))
    }
    @Test fun clockRecompositionDoesNotReshuffleAndAllConditionsMatchNonEmptyPools() {
        assertEquals(WeatherCopy.select(weather,now),WeatherCopy.select(weather,now.plusMinutes(12)))
        assertNotEquals(WeatherCopy.select(weather,now.withHour(17).withMinute(29)),WeatherCopy.select(weather,now.withHour(17).withMinute(30)))
        for(theme in WeatherTheme.entries) for(hour in listOf(0,5,9,12,14,18,21)) {
            assertTrue(WeatherCopy.select(weather.copy(theme=theme),now.withHour(hour)).isNotBlank())
        }
    }
}
