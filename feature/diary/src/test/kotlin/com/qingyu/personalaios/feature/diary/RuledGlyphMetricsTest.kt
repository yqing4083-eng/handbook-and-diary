package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class RuledGlyphMetricsTest {
    @Test fun adjacentCharactersTrimRepeatedLeadingWhitespaceWithoutRescalingInk() {
        val g=RuledGlyph(0,listOf(InkStroke(width=.012f,points=listOf(InkPoint(.25f,.5f),InkPoint(.8f,.5f)))),true)
        val first=RuledGlyphMetrics(g,60f,1080f)
        val adjacent=RuledGlyphMetrics(g,60f,1080f).compactLeading()
        assertEquals(first.side,adjacent.side,0f)
        assertEquals(first.width,adjacent.width,0f);assertEquals(first.height,adjacent.height,0f)
        assertEquals(first.baselineOffset,adjacent.baselineOffset,0f)
        assertEquals(0f,adjacent.bitmapLeftOffset,.001f)
        assertTrue(first.advance(60f)-adjacent.advance(60f)>12f)
        val visibleGap=first.advance(60f)+adjacent.bitmapLeftOffset-first.bounds.right*first.side
        assertTrue(visibleGap in 2.7f..3.8f)
    }
    @Test fun shortAndLongMarksKeepTheirLengthsAndShareOneNibScale() {
        fun measure(end:Float)=RuledGlyphMetrics(RuledGlyph(0,listOf(InkStroke(width=.012f,
            points=listOf(InkPoint(.15f,.5f),InkPoint(end,.5f)))),true,2.4f),60f,1080f)
        val short=measure(.35f);val long=measure(.85f)
        assertEquals(60f,short.side,0f);assertEquals(short.side,long.side,0f)
        assertEquals(30f,long.width-short.width,.001f)
        assertEquals(short.height,long.height,.001f)
        assertEquals(short.inkReferenceWidth,long.inkReferenceWidth,0f)
    }
    @Test fun sourceCoordinatesSurviveBitmapCroppingInBothAxes() {
        for(x in listOf(.15f,.5f,.85f)) for(y in listOf(.15f,.5f,.85f)) {
            val m=RuledGlyphMetrics(RuledGlyph(0,listOf(InkStroke(width=.012f,
                points=listOf(InkPoint(x,y)))),true),60f,1080f)
            val cursor=100f;val baseline=200f
            assertEquals(cursor+x*60f,m.sourceLeft(cursor)+x*m.side,.001f)
            assertEquals(baseline-60f+y*60f,baseline-m.baselineOffset+y*m.side,.001f)
            assertEquals(cursor+m.bounds.left*m.side,cursor+m.bitmapLeftOffset,.001f)
        }
    }
    @Test fun writingABroadCharacterDoesNotResizeAnyOfItsEarlierStrokes() {
        val first=InkStroke(width=.018f,points=listOf(InkPoint(.3f,.5f),InkPoint(.5f,.5f)))
        val added=first.copy(points=listOf(InkPoint(.1f,.1f),InkPoint(.9f,.9f)))
        val one=RuledGlyph(0,listOf(first),true)
        val a=RuledGlyphMetrics(one,60f,1080f);val b=RuledGlyphMetrics(one.copy(strokes=one.strokes+added),60f,1080f)
        assertEquals(a.side,b.side,0f)
        assertEquals(a.inkReferenceWidth,b.inkReferenceWidth,0f)
        assertEquals(a.sourceLeft(100f),b.sourceLeft(100f),.001f)
        assertEquals(a.baselineOffset,b.baselineOffset,.001f)
    }
    @Test fun punctuationRetainsItsPadHeightWithoutChangingInkSizeOrAdvance() {
        for(physical in listOf(false,true)) {
            fun metrics(y:Float)=RuledGlyphMetrics(RuledGlyph(0,listOf(InkStroke(width=.012f,
                points=listOf(InkPoint(.48f,y-.03f),InkPoint(.50f,y),InkPoint(.46f,y+.03f)))),physical),60f,1000f)
            val middle=metrics(.5f);val low=metrics(.85f);val high=metrics(.15f)
            fun centre(m:RuledGlyphMetrics)=(m.bounds.top+m.bounds.bottom)*m.side/2-m.baselineOffset
            assertEquals(21f,centre(low)-centre(middle),.6f)
            assertEquals(-21f,centre(high)-centre(middle),.6f)
            assertEquals(middle.height,low.height,.001f)
            assertEquals(middle.width,low.width,.001f)
            assertEquals(middle.inkReferenceWidth,low.inkReferenceWidth,.001f)
            assertEquals(middle.advance(60f),low.advance(60f),.001f)
        }
    }
    @Test fun erasedPunctuationKeepsItsPositionAndScale() {
        val stroke=InkStroke(width=.012f,points=listOf(InkPoint(.5f,.85f)))
        for(physical in listOf(false,true)) {
            val glyph=RuledGlyph(0,listOf(stroke),physical)
            val before=RuledGlyphMetrics(glyph,60f,1000f)
            val after=RuledGlyphMetrics(glyph.copy(strokes=glyph.strokes+GlyphInkEraser.encode(listOf(listOf(.5f to .85f)),.05f,physical)),60f,1000f)
            assertEquals(before.baselineOffset,after.baselineOffset,0f)
            assertEquals(before.topFromBaseline,after.topFromBaseline,0f)
            assertEquals(before.width,after.width,0f)
        }
    }
    @Test fun horizontalAndTallCharactersShareCellCentreWithEraserCoordinates() {
        for(physical in listOf(false,true)) for(points in listOf(
            listOf(InkPoint(.2f,.5f),InkPoint(.8f,.5f)),
            listOf(InkPoint(.5f,.2f),InkPoint(.5f,.8f)),
            listOf(InkPoint(.2f,.2f),InkPoint(.8f,.8f)))) {
            val m=RuledGlyphMetrics(RuledGlyph(0,listOf(InkStroke(width=.008f,points=points)),physical),60f,1000f)
            val centre=(m.bounds.top+m.bounds.bottom)*m.side/2-m.baselineOffset
            assertEquals(-30f,centre,.6f)
            assertEquals(m.topFromBaseline,m.baselineOffset-m.bounds.top*m.side,.001f)
        }
    }
    @Test fun glyphAndNibShrinkByExactlyTheSameRatio() {
        val stroke=InkStroke(width=.008f,points=listOf(InkPoint(.2f,.5f),InkPoint(.8f,.5f)))
        val m=RuledGlyphMetrics(RuledGlyph(0,listOf(stroke),true),60f,1000f)
        val larger=RuledGlyphMetrics(m.glyph,120f,1000f)
        assertEquals(m.height*2,larger.height,.001f)
        assertEquals(m.width*2,larger.width,.001f)
        assertEquals(m.inkReferenceWidth*2,larger.inkReferenceWidth,.001f)
        assertTrue(stroke.width*m.inkReferenceWidth<stroke.width*1000)
        assertTrue(m.width>.6f*m.side)
    }
    @Test fun broadWetTipsKeepTheirFullFootprintAndMasksDoNotChangeLayout() {
        val stroke=InkStroke(width=.04f,tool=InkTool.WATERCOLOR,points=listOf(InkPoint(.2f,.5f),InkPoint(.8f,.5f)))
        val glyph=RuledGlyph(0,listOf(stroke),true)
        val m=RuledGlyphMetrics(glyph,60f,1000f)
        assertTrue(m.height<=60f && m.width<=60f)
        val erased=glyph.copy(strokes=glyph.strokes+GlyphInkEraser.encode(listOf(listOf(.5f to .5f)),.2f))
        assertEquals(m.width,RuledGlyphMetrics(erased,60f,1000f).width,0f)
    }
    @Test fun sourcePadSizeChangesNibAndDoesNotIntroducePhysicalPaperWidth() {
        val stroke=InkStroke(width=.016f,points=listOf(InkPoint(.2f,.2f),InkPoint(.8f,.8f)))
        val glyph=RuledGlyph(0,listOf(stroke),true,2.2f)
        val a=RuledGlyphMetrics(glyph,60f,1000f);val b=RuledGlyphMetrics(glyph,60f,1500f)
        assertEquals(a.inkReferenceWidth,b.inkReferenceWidth,0f)
        assertEquals(a.height,b.height,0f)
        assertEquals(2.2f,a.inkReferenceWidth/a.side,.0001f)
    }
}
