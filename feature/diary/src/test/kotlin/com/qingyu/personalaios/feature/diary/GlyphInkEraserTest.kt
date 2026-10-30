package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class GlyphInkEraserTest {
    @Test fun physicalMaskRetainsWideRadiusAndOutsideCentre() {
        val mask=GlyphInkEraser.encode(listOf(listOf(-12f to .5f,20f to .5f)),8f,true).single()
        assertEquals(-12f,mask.points.first().x*65-32,.0001f)
        assertEquals(20f,mask.points.last().x*65-32,.0001f)
        assertEquals(8f,GlyphInkEraser.radius(mask.width,true),.001f)
    }
    @Test fun outsideCentresAndRadiusRoundTripWithoutClampingToGlyphEdge() {
        val mask=GlyphInkEraser.encode(listOf(listOf(-.3f to .5f,.8f to .5f)),.2f).single()
        assertEquals(InkTool.GLYPH_ERASER,mask.tool)
        assertEquals(-.3f,mask.points.first().x*5-2,.00001f)
        assertEquals(.2f,mask.width*50,.00001f)
    }
    @Test fun maskDoesNotChangeGlyphSizeOrSpacingAndOriginalInkIsUntouched() {
        val ink=InkStroke(points=listOf(InkPoint(.2f,.2f),InkPoint(.8f,.8f)))
        val masks=GlyphInkEraser.encode(listOf(listOf(-.2f to .5f,1.2f to .5f)),.3f)
        assertEquals(RuledInkMetrics.of(listOf(ink)),RuledInkMetrics.of(listOf(ink)+masks))
        assertEquals(.2f,ink.points.first().x,0f)
    }
    @Test fun fastStrokeCrossingEntireGlyphIsClippedInsteadOfShifted() {
        val mask=GlyphInkEraser.encode(listOf(listOf(-10f to .5f,10f to .5f)),.1f).single()
        assertEquals(0f,mask.points.first().x,.00001f);assertEquals(1f,mask.points.last().x,.00001f)
    }
}
