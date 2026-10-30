package com.qingyu.personalaios.feature.today

import com.qingyu.personalaios.domain.*
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class TodayRulesTest {
    private val rain = WeatherData("杭州", WeatherTheme.LIGHT_RAIN, "小雨", 13, 11, 88, "东风", 2, 1.2f, Instant.EPOCH)
    @Test fun findsUpcomingPendingItemInsteadOfAnAlreadyCompletedOne() {
        assertEquals("afternoon", TodayRules.upcoming(TodayExamples.tasks, listOf("morning"), LocalTime.NOON)?.id)
        assertEquals("evening", TodayRules.upcoming(TodayExamples.tasks, listOf("morning", "afternoon"), LocalTime.NOON)?.id)
        assertNull(TodayRules.upcoming(TodayExamples.tasks, emptyList(), LocalTime.of(23, 0)))
    }
    @Test fun insightUsesWeatherAndUpcomingItem() {
        val result = TodayRules.insight(rain, TodayExamples.tasks, listOf("morning"), LocalTime.NOON)
        assertTrue(result.contains("带把伞"))
        assertTrue(result.contains("14:00 完成作业"))
        assertFalse(result.contains("08:30"))
    }
    @Test fun coldWeatherAndAllCompletedItemsGetAnAppropriateReminder() {
        val result = TodayRules.insight(rain.copy(theme = WeatherTheme.SNOW, temperature = -2), TodayExamples.tasks,
            TodayExamples.tasks.map { it.id }, LocalTime.of(23, 0))
        assertTrue(result.contains("多穿一点"))
        assertTrue(result.contains("记录今天"))
    }
}
