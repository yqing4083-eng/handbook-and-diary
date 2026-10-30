package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class BookClosingGeometryTest {
    @Test fun gatherCompletesBeforeClosedVolumeRebounds() {
        assertEquals(.66f,BookClosingGeometry.opening(0f),0f)
        assertTrue(BookOpeningGeometry.spread(BookClosingGeometry.opening(.04f))<.99f)
        for(step in 0..100) assertTrue(BookClosingGeometry.opening(step/100f)>=BookClosingGeometry.opening((step+1)/100f))
        for(step in 68..100) assertEquals(0f,BookClosingGeometry.opening(step/100f),0f)
        assertTrue(BookClosingGeometry.scale(.87f)>1.025f)
        assertTrue(BookClosingGeometry.scale(.87f)>BookClosingGeometry.scale(.99f))
        assertEquals(1f,BookClosingGeometry.scale(1f),.00001f)
    }
    @Test fun reboundKeepsLowerEdgeFixedWithoutSquashingPaper() {
        val bottom=-BookGeometry.CoverHeight/2
        for(step in 68..100) {
            val p=step/100f
            assertEquals(bottom,bottom*BookClosingGeometry.scale(p)+BookClosingGeometry.bottomOffset(p),.00001f)
        }
    }
    @Test fun phaseJoinsAndSettlementHaveNoScaleJumps() {
        for(p in listOf(.36f,.68f,.872f,1f)) {
            assertEquals(BookClosingGeometry.scale(p-.00001f),BookClosingGeometry.scale(p+.00001f),.0001f)
            assertEquals(BookClosingGeometry.opening(p-.00001f),BookClosingGeometry.opening(p+.00001f),.0001f)
        }
        assertEquals(1f,BookClosingGeometry.scale(-1f),0f)
    }
    @Test fun immediateClosingStartIsTheSameVisibleSpreadAsTheOpenBook() {
        val opening=BookClosingGeometry.opening(0f)
        assertEquals(1f,BookOpeningGeometry.spread(opening),.000001f)
        assertEquals(0f,BookOpeningGeometry.yaw(opening),.000001f)
        for(leaf in -1..10) {
            val before=AlbumLeafGeometry.pose(leaf,4,0f,1,opening=1f)
            val after=AlbumLeafGeometry.pose(leaf,4,0f,1,opening=opening,closing=0f)
            assertEquals(before,after)
        }
    }
}
