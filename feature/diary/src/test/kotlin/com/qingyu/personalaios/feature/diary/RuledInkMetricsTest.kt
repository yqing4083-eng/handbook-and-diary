package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class RuledInkMetricsTest {
    @Test fun outerWhitespaceDoesNotSeparateCharactersAndPointsStayIntact() {
        val stroke=InkStroke(points=listOf(InkPoint(.35f,.2f),InkPoint(.65f,.8f)),width=.02f)
        val before=stroke.copy(points=stroke.points.toList())
        val metrics=RuledInkMetrics.of(listOf(stroke))
        assertEquals(before,stroke);assertTrue(metrics.advance(60f)<42f)
        assertEquals(.69f,metrics.extent,.0001f)
    }
    @Test fun dotKeepsSmallExtentAndEraserDoesNotExpandItsAdvance() {
        val dot=InkStroke(points=listOf(InkPoint(.5f,.5f)),width=.01f)
        val erase=InkStroke(points=listOf(InkPoint(0f,0f),InkPoint(1f,1f)),tool=InkTool.ERASER)
        assertEquals(RuledInkMetrics.of(listOf(dot)),RuledInkMetrics.of(listOf(dot,erase)))
        assertTrue(RuledInkMetrics.of(listOf(dot)).advance(60f)<18f)
    }
}
