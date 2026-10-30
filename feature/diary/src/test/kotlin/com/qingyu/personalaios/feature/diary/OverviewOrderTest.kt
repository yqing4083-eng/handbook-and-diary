package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.DiaryPage
import org.junit.Assert.*
import org.junit.Test

class OverviewOrderTest {
    private val pages=List(8) { DiaryPage(id="page-$it",order=it,body="content-$it") }
    @Test fun movingAcrossRowsRetainsEveryIdentityAndBothFaces() {
        for(from in pages.indices) for(to in pages.indices) {
            val moved=OverviewOrder.move(pages,pages[from].id,to)
            assertEquals(pages[from],moved[to]);assertEquals(pages.toSet(),moved.toSet())
            assertEquals(pages.filterIndexed { i,_ -> i!=from },moved.filter { it.id!=pages[from].id })
        }
    }
    @Test fun dragTargetAndRenderedSlotShareHeaderAndScrollCoordinates() {
        for(scroll in listOf(0f,-220f,-600f)) for(i in pages.indices) {
            val slot=BookOverviewGeometry.slot(i,1080f,2200f,4,scroll,410f)
            assertEquals(i,OverviewOrder.target(slot.centreX,slot.centreY,1080f,2200f,pages.size,4,scroll,410f))
        }
        assertEquals(410f,BookOverviewGeometry.slot(0,1080f,2200f,7,top=410f).top,0f)
        assertEquals(0f,BookOverviewGeometry.pitch(1f),0f)
    }
    @Test fun viewportCullingAndHeaderBoundsRemainFiniteForLargeLibraries() {
        for(count in listOf(1,2,10,10000)) {
            val bounds=BookOverviewGeometry.scrollBounds(1080f,2200f,count,0,410f)
            assertEquals(0f,bounds.endInclusive,0f)
            val visible=BookOverviewGeometry.visible(1080f,2200f,count,0,bounds.start,410f)
            assertTrue(visible.count()<32)
        }
    }
    @Test fun pixelGridUsesTheSameWholePixelStepForEveryLine() {
        assertEquals(90f,PaperRules.gap(1080.0002f),0f)
        for(width in listOf(720f,996f,1080f,1260f,1440f)) {
            val gap=PaperRules.gap(width)
            assertEquals(gap.toInt().toFloat(),gap,0f)
            assertEquals(gap,PaperRules.title(width),0f)
            assertEquals(gap*2,PaperRules.body(width),0f)
        }
    }
}
