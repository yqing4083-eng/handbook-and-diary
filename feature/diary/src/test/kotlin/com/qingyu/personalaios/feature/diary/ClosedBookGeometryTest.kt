package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.DiaryJournal
import org.junit.Assert.*
import org.junit.Test

class ClosedBookGeometryTest {
    private val journal=DiaryJournal(id="a",name="A")
    private val scene=BookScene(journal=journal,journals=listOf(journal,DiaryJournal(id="b",name="B")))
    @Test fun restMatchesFirstSolidCoverOnPortraitAndLandscape() {
        for((w,h) in listOf(1080f to 2200f,2200f to 1080f)) {
            val s=scene.copy(portrait=w<h)
            val rect=ClosedBookGeometry.rect(0,w,h,s)
            val quad=BookLibraryGeometry.coverQuad(w,h,s,BookLibraryGeometry.pose(0,0f))
            assertEquals(quad.left,rect.left,.001f);assertEquals(quad.bottom,rect.bottom,.001f)
            assertEquals(quad.right,rect.right,.001f);assertEquals(quad.top,rect.top,.001f)
        }
    }
    @Test fun htmlScaleWindowsAndBottomAlignment() {
        for(p in listOf(0f,.2f,.38f,.45f,.7f,.95f,1f,1.02f)) {
            val s=scene.copy(libraryOffset=-p*BookCarouselMotion.Step)
            val a=ClosedBookGeometry.rect(0,1080f,2200f,s);val b=ClosedBookGeometry.rect(1,1080f,2200f,s)
            assertEquals(a.bottom,b.bottom,.001f)
            assertEquals(a.width/a.height,b.width/b.height,.001f)
        }
        assertEquals(.85f,ClosedBookGeometry.scale(0,-.38f*BookCarouselMotion.Step),.0001f)
        assertEquals(.85f,ClosedBookGeometry.scale(1,-.45f*BookCarouselMotion.Step),.0001f)
        assertEquals(1f,ClosedBookGeometry.scale(1,-.95f*BookCarouselMotion.Step),.0001f)
    }
    @Test fun arrivalPreservesSizeAndAspectAndEndsAtTheExactRestPosition() {
        val base=ClosedBookGeometry.base(1080f,2200f,scene)
        fun rect(p:Float)=ClosedBookGeometry.rect(0,1080f,2200f,scene.copy(creationFrom=DiaryJournal(id="old",name="Old"),creationProgress=p))
        for(i in 0..100) {
            val frame=rect(i/100f)
            assertTrue(frame.width in base.width*.849f..base.width*1.01f)
            assertEquals(base.left+base.width/2,frame.left+frame.width/2,.001f)
        }
        assertTrue(rect(.38f).bottom<base.top)
        assertTrue(rect(.52f).top>rect(.40f).top)
        assertTrue(rect(.8f).top<rect(.52f).top)
        assertEquals(base.left,rect(1f).left,.001f);assertEquals(base.top,rect(1f).top,.001f)
        assertEquals(base.width,rect(1f).width,.001f);assertEquals(base.height,rect(1f).height,.001f)
        for(i in 0..100) assertEquals(base.width/base.height,rect(i/100f).width/rect(i/100f).height,.00001f)
        for(delta in -2..2) {
            val end=BookCreationMotion.creation(delta,1f)
            val before=BookCreationMotion.creation(delta,.99999f)
            assertEquals(end.x,before.x,.0001f);assertEquals(end.y,before.y,.0001f)
            assertEquals(end.sx,before.sx,.0001f);assertEquals(end.sy,before.sy,.0001f)
        }
    }
    @Test fun restingCoverIsCentredOnBothViewportOrientations() {
        for((w,h) in listOf(1080f to 2200f,2200f to 1080f)) {
            val rect=ClosedBookGeometry.base(w,h,scene.copy(portrait=w<h))
            assertEquals(rect.left,w-rect.right,.001f)
        }
    }
    @Test fun solidShelfHitTestingUsesProjectedCoverAndRejectsOutside() {
        val rect=ClosedBookGeometry.rect(0,1080f,2200f,scene)
        assertEquals(0,BookShelfGeometry.pick(rect.left+rect.width/2,rect.top+rect.height/2,1080f,2200f,scene))
        assertNull(BookShelfGeometry.pick(10f,10f,1080f,2200f,scene))
    }
    @Test fun measuredStaggerDropsTheOldBookBeforeLiftingItsNeighbour() {
        val step=BookCarouselMotion.Step
        assertEquals(.911f,ClosedBookGeometry.scale(0,-.098f*step),.005f)
        assertEquals(.879f,ClosedBookGeometry.scale(0,-.16f*step),.005f)
        assertEquals(.85f,ClosedBookGeometry.scale(1,-.45f*step),.00001f)
        assertTrue(ClosedBookGeometry.scale(1,-.65f*step)>.94f)
        for(join in listOf(.45f,.95f)) {
            val e=.0001f
            val slope=(ClosedBookGeometry.scale(1,-(join+e)*step)-ClosedBookGeometry.scale(1,-(join-e)*step))/(2*e)
            assertEquals(0f,slope,.005f)
        }
    }
}
