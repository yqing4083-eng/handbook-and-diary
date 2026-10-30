package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class BookOverviewGeometryTest {
    @Test fun everyAnchorRowStartsAtScreenCentreInBothOrientations() {
        for((w,h) in listOf(1080f to 2200f,2200f to 1080f)) for(anchor in 0..32) {
            assertEquals(h/2,BookOverviewGeometry.slot(anchor,w,h,anchor).centreY,.001f)
            assertTrue(0f in BookOverviewGeometry.scrollBounds(w,h,33,anchor))
        }
    }
    @Test fun columnsAreBalancedAndEachCellKeepsTheOriginalSpreadAspect() {
        val left=BookOverviewGeometry.slot(0,720f,1562f,4)
        val right=BookOverviewGeometry.slot(1,720f,1562f,4)
        assertEquals(left.left,720-right.left-right.width,1f)
        assertEquals(BookGeometry.HalfWidth/BookGeometry.HalfHeight,left.width/left.height,.00001f)
        assertEquals(left.top,right.top,0f)
    }
    @Test fun gridScrollMovesEveryCellByExactlyTheSameDistance() {
        for(i in 0..32) {
            val a=BookOverviewGeometry.slot(i,1080f,2200f,16,-45f)
            val b=BookOverviewGeometry.slot(i,1080f,2200f,16,173f)
            assertEquals(218f,b.top-a.top,.001f)
            assertEquals(a.width,b.width,0f)
        }
    }
    @Test fun panelEntryAndExitHaveExactBookAndGridEndpoints() {
        val source=FoldPanelPose(PagePose(.2f,.1f,-.6f),-25f)
        val base=BookScene(open=1f,current=12,overviewAnchor=4,overviewScroll=110f)
        assertEquals(source,BookOverviewGeometry.panel(source,false,12,base,1080f,2200f))
        val scene=base.copy(overview=1f)
        val result=BookOverviewGeometry.panel(source,false,12,scene,1080f,2200f)
        val slot=BookOverviewGeometry.slot(12,1080f,2200f,4,110f)
        val focal=BookGeometry.focal(1080f,2200f,true,1f)
        val depth=BookGeometry.cameraDistance(1f)-result.hinge.z
        assertEquals(slot.centreX,540+result.hinge.x*focal/depth,.001f)
        assertEquals(slot.centreY,BookCameraGeometry.projectY(result.hinge.y,result.hinge.z,1080f,2200f,scene),.001f)
        assertEquals(0f,result.angle,0f)
        assertEquals(-180f,BookOverviewGeometry.panel(source,true,12,scene,1080f,2200f).angle,0f)
    }
    @Test fun everyVisibleSlotIsIncludedButThousandsOfOffscreenPagesAreNot() {
        val visible=BookOverviewGeometry.visible(1080f,2200f,10000,4000,-300f)
        assertTrue(visible.count()<32)
        for(i in 0 until 10000) {
            val slot=BookOverviewGeometry.slot(i,1080f,2200f,4000,-300f)
            if(slot.top<2200 && slot.top+slot.height>0) assertTrue(i in visible)
        }
        assertTrue(BookOverviewGeometry.visible(1080f,2200f,0,0,0f).isEmpty())
    }
    @Test fun selectingAnotherPageDoesNotMoveTheSettledGrid() {
        val source=FoldPanelPose(PagePose(.2f,.1f,-.6f),-25f)
        val scene=BookScene(open=1f,overview=1f,current=0,overviewAnchor=4)
        assertEquals(BookOverviewGeometry.panel(source,false,8,scene,1080f,2200f),
            BookOverviewGeometry.panel(source.copy(angle=-155f),false,8,scene.copy(current=8),1080f,2200f))
    }
    @Test fun hiddenNeighbourRetreatsBeforeItsInnerFaceCanCrossCurrentPaper() {
        val cursor=4
        val hidden=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(cursor,cursor,0f,1),true)
        for(p in listOf(.12f,.2f,.3f,.4f,.5f,.6f)) {
            val scene=BookScene(open=1f,current=cursor,overview=p,overviewAnchor=cursor)
            val panel=BookOverviewGeometry.panel(hidden,true,cursor+1,scene,1080f,2200f)
            val scale=BookOverviewGeometry.scale(p,cursor+1,cursor,BookOverviewGeometry.targetScale(1080f,2200f,scene))
            val nearest=panel.hinge.z+max(0f,-BookGeometry.HalfWidth*scale*sin(panel.angle*PI.toFloat()/180))
            assertTrue("hidden neighbour crosses front at $p",nearest<.03f)
        }
    }
    @Test fun completeSpreadsShareOneHingeAndFaceFrontBeforeColumnsSeparate() {
        for(current in listOf(0,4,31)) for(index in max(0,current-7)..current+7) {
            val left=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(index-1,current,0f,1),true)
            val right=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(index,current,0f,1),false)
            for(progress in listOf(.60f,.68f,.79f,.9f,1f)) {
                val scene=BookScene(open=1f,current=current,overview=progress,overviewAnchor=current)
                val a=BookOverviewGeometry.panel(left,true,index,scene,720f,1562f,right)
                val b=BookOverviewGeometry.panel(right,false,index,scene,720f,1562f,left)
                assertEquals("split spread $index at $progress",a.hinge,b.hinge)
                assertEquals(-180f,a.angle,.001f)
                assertEquals(0f,b.angle,.001f)
            }
        }
    }
    @Test fun rowsArriveBeforeTheColumnOvershootAndDoNotBounceWithDepth() {
        for((w,h) in listOf(720f to 1562f,1562f to 720f)) for(index in 0..12) {
            val current=4
            val source=FoldPanelPose(PagePose(.2f,0f,-.6f),-25f)
            val final=BookOverviewGeometry.slot(index,w,h,current)
            val lag=min(abs(index-current),6)*.012f
            for(p in listOf(.68f,.78f,.84f,.92f)) {
                val scene=BookScene(open=1f,current=current,overview=p+lag,overviewAnchor=current,
                    openCentreY=h*.57f,portrait=w<h)
                val pose=BookOverviewGeometry.panel(source,false,index,scene,w,h)
                assertEquals(final.centreY,BookCameraGeometry.projectY(pose.hinge.y,pose.hinge.z,w,h,scene),.001f)
            }
        }
        assertEquals(0f,BookOverviewGeometry.columnTravel(.65f,4,4),0f)
        assertEquals(1f,BookOverviewGeometry.rowTravel(.66f,4,4),.00001f)
        assertEquals(1.095f,BookOverviewGeometry.columnTravel(.65f+.26f*.52f,4,4),.00001f)
        assertEquals(1f,BookOverviewGeometry.columnTravel(.92f,4,4),0f)
    }
    @Test fun overshootHasOnePeakAndCannotStretchPaperOrOscillateAfterLanding() {
        var previous=0f;var recovering=false;var peak=0f
        for(step in 0..1000) {
            val p=step/1000f
            val column=BookOverviewGeometry.columnTravel(p,4,4)
            peak=max(peak,column)
            if(column<previous-.00001f) recovering=true
            if(recovering) assertTrue(column<=previous+.00001f)
            assertTrue(column>=0f && column<=1.09501f)
            val scale=BookOverviewGeometry.scale(p,4,4,.28f)
            assertTrue(scale in .27999f..1f)
            previous=column
        }
        assertEquals(1.095f,peak,.0001f)
    }
    @Test fun columnKeyframeJoinsHaveContinuousVelocityForInterruptedReversals() {
        val epsilon=.00005f
        for(t in listOf(0f,.08f,.28f,.52f,1f)) {
            val p=.65f+.26f*t
            fun position(at:Float)=BookOverviewGeometry.columnTravel(at,4,4)
            val before=(position(p)-position(p-epsilon))/epsilon
            val after=(position(p+epsilon)-position(p))/epsilon
            assertEquals("velocity discontinuity at $t",before,after,.12f)
        }
    }
}
