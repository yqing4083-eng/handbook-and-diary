package com.qingyu.personalaios.feature.today

import com.qingyu.personalaios.domain.*
import java.time.LocalTime

data class DailyTask(val id: String, val time: LocalTime, val title: String, val allDay: Boolean = false)

object TodayExamples {
    val tasks = listOf(
        DailyTask("morning", LocalTime.of(8, 30), "上午课程"),
        DailyTask("afternoon", LocalTime.of(14, 0), "完成作业"),
        DailyTask("evening", LocalTime.of(22, 30), "整理明天的东西"),
    )
}

object TodayRules {
    fun mood(theme: WeatherTheme): String = when (theme) {
        WeatherTheme.SNOW -> "雪落下来以后，世界安静了一点。"
        WeatherTheme.LIGHT_RAIN, WeatherTheme.HEAVY_RAIN, WeatherTheme.THUNDER -> "雨落下来以后，世界安静了一点。"
        WeatherTheme.CLEAR -> "今天的天空很干净。"
        else -> "云慢慢走，今天也可以慢一点。"
    }
    fun upcoming(tasks: List<DailyTask>, completed: Collection<String>, now: LocalTime): DailyTask? =
        tasks.filter { it.id !in completed && !it.time.isBefore(now) }.minByOrNull { it.time }

    /** Phase-one rules are explicit local examples; no AI network request is implied. */
    fun insight(weather: WeatherData, tasks: List<DailyTask>, completed: Collection<String>, now: LocalTime): String {
        val condition = when {
            weather.temperature <= 5 -> "今天气温比较低，出门可以多穿一点。"
            weather.theme in listOf(WeatherTheme.LIGHT_RAIN, WeatherTheme.HEAVY_RAIN, WeatherTheme.THUNDER) -> "今天有雨，出门记得带把伞。"
            weather.temperature >= 30 -> "今天比较热，记得补充水分。"
            else -> "天气适合走走，也给自己留一点空白。"
        }
        val next = upcoming(tasks, completed, now)
        val reminder = when {
            next != null -> "${next.time} ${next.title}，按自己的节奏来。"
            now.hour >= 19 -> "睡前留一点时间，记录今天。"
            else -> "今天的事项已经告一段落，好好休息。"
        }
        return "$condition\n$reminder"
    }
}
