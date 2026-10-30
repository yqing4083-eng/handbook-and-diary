package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.InkTool
import org.junit.Assert.*
import org.junit.Test

class PaperLiftGeometryTest {
    @Test fun rotatingOnlyTheLeafPreservesItsSizeThroughAQuarterTurn() {
        val rect=PaperRect(100f,300f,240f,160f)
        val start=PaperLiftGeometry.flat(rect,-90f);val end=PaperLiftGeometry.flat(rect)
        for(i in 0..100) {
            val q=PaperLiftGeometry.project(start,end,i/100f).points
            fun edge(a:Int,b:Int)=kotlin.math.hypot(q[a*2]-q[b*2],q[a*2+1]-q[b*2+1])
            assertEquals(240f,edge(0,1),.001f);assertEquals(160f,edge(1,2),.001f)
        }
        assertArrayEquals(start.points,PaperLiftGeometry.project(start,end,0f).points,0f)
        assertArrayEquals(end.points,PaperLiftGeometry.project(start,end,1f).points,0f)
    }
    @Test fun orientationRoundTripRetainsAllFourPhysicalCorners() {
        val q=PaperLiftGeometry.flat(PaperRect(40f,120f,300f,210f),12f)
        val wide=PaperLiftGeometry.Source(q,400f,900f,false).viewport(900f,400f,true)
        val back=PaperLiftGeometry.Source(wide,900f,400f,true).viewport(400f,900f,false)
        assertArrayEquals(q.points,back.points,.0001f)
    }
    @Test fun bothOrientationsHaveContinuousMotionAndExactEndpoints() {
        for(landscape in listOf(false,true)) for(aspect in listOf(.675f,1.35f)) {
            val start=PaperLiftGeometry.pose(0f,landscape,aspect)
            val end=PaperLiftGeometry.pose(1f,landscape,aspect)
            assertEquals(0f,start.pitch,.0001f);assertEquals(0f,end.pitch,.0001f)
            assertEquals(0f,start.backdrop,0f);assertEquals(1f,end.backdrop,0f)
            var previous=start
            for(i in 1..1000) {
                val current=PaperLiftGeometry.pose(i/1000f,landscape,aspect)
                assertTrue(current.backdrop>=previous.backdrop-.0001f)
                assertTrue(kotlin.math.abs(current.pitch-previous.pitch)<.1f)
                previous=current
            }
            assertTrue(kotlin.math.abs(PaperLiftGeometry.pose(.5f,landscape,aspect).pitch)>1f)
        }
    }
    @Test fun stalePageOrRotatedViewportCannotHandOffEditorArtwork() {
        val frame=BookPresentedFrame(2400,1080,"photo",4)
        assertTrue(frame.matches("photo",4,2400,1080))
        assertFalse(frame.matches("photo",5,2400,1080))
        assertFalse(frame.matches("other",4,2400,1080))
        assertFalse(frame.matches("photo",4,1080,2400))
    }
    @Test fun allPensDefaultToFourthAndThirdIsSlightlyThicker() {
        for(tool in InkTool.entries.filter { it!=InkTool.ERASER && it!=InkTool.GLYPH_ERASER })
            assertEquals(PaperBrushPresets.widths[3],PaperBrushPresets.initialWidth(tool),0f)
        assertTrue(PaperBrushPresets.widths[2]>.012f)
        assertTrue(PaperBrushPresets.widths[2]<PaperBrushPresets.widths[3])
        assertTrue(PaperBrushPresets.widths.zipWithNext().all { (a,b)->a<b })
        assertEquals(.035f,PaperBrushPresets.initialWidth(InkTool.ERASER),0f)
    }
}
