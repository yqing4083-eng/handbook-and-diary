package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import com.qingyu.personalaios.domain.DiaryJournal

class BookCarouselMotionTest {
    @Test fun deskBooksTrackFingerPixelsInBothOrientations() {
        for((w,h,portrait) in listOf(Triple(1080f,2138f,true),Triple(2400f,920f,false))) {
            val scene=BookScene(portrait=portrait)
            val initial=BookLibraryGeometry.coverQuad(w,h,scene,BookLibraryGeometry.pose(2,0f))
            for(offset in listOf(-.03f,-.23f,-.7f,.4f)) {
                val tracked=offset*BookCarouselMotion.coordinateScale(w,h,portrait)
                val q=BookLibraryGeometry.coverQuad(w,h,scene,BookLibraryGeometry.pose(2,tracked))
                // Track the projected centre, not the extrema of a trapezoid. Extrema
                // switch corners as a perspective book crosses the camera centre.
                fun centre(quad:BookQuad)=(quad.points[0]+quad.points[2]+quad.points[4]+quad.points[6])/4
                assertEquals(offset*w,centre(q)-centre(initial),kotlin.math.abs(offset*w)*.003f+.2f)
            }
        }
    }
    @Test fun creationPreservesExactProjectedDimensionsThroughoutItsVisibleArc() {
        for((w,h,portrait) in listOf(Triple(1080f,2138f,true),Triple(2400f,920f,false))) {
            val scene=BookScene(portrait=portrait)
            val end=BookLibraryGeometry.coverQuad(w,h,scene,BookLibraryGeometry.arrival(1f))
            for(i in 0..1000) {
                val pose=BookLibraryGeometry.arrival(i/1000f)
                val q=BookLibraryGeometry.coverQuad(w,h,scene,pose)
                assertEquals(1f,pose.scale,0f)
                assertEquals(end.right-end.left,q.right-q.left,.002f)
                assertEquals(end.bottom-end.top,q.bottom-q.top,.002f)
                assertTrue(q.points.all { it.isFinite() })
            }
        }
    }
    @Test fun newBookTravelsInFromLowerRightAndReboundsAsOneRigidObject() {
        val start=BookLibraryGeometry.arrival(0f);val crest=BookLibraryGeometry.arrival(.58f)
        val settled=BookLibraryGeometry.arrival(1f)
        assertTrue(start.x>2f);assertTrue(start.y<0f)
        assertTrue(crest.y>settled.y)
        assertTrue(BookLibraryGeometry.arrival(.84f).y<settled.y)
        assertEquals(crest.z,settled.z,0f)
    }
    @Test fun creationBeginsAndEndsWithoutMovingExistingJournalsAtTheCommit() {
        for(delta in -2..2) assertEquals(BookLibraryGeometry.pose(delta,0f),BookLibraryGeometry.creationPose(delta,1f))
        assertEquals(BookLibraryGeometry.pose(0,0f),BookLibraryGeometry.creationPose(-1,0f))
        for(delta in -2..2) {
            val before=BookLibraryGeometry.creationPose(delta,.99999f);val after=BookLibraryGeometry.creationPose(delta,1f)
            assertEquals(before.x,after.x,.0001f);assertEquals(before.y,after.y,.0001f);assertEquals(before.z,after.z,.0001f)
        }
    }
    @Test fun selectionCommitHasIdenticalGeometryForEveryBookInBothDirections() {
        for(dir in listOf(-1,1)) for(delta in -2..2)
            assertEquals(BookLibraryGeometry.pose(delta-dir,0f),BookLibraryGeometry.pose(delta,-dir*BookCarouselMotion.Step))
    }
    @Test fun fasterReleaseCompletesSoonerWithoutAnInitialPositionJump() {
        val offset=-BookCarouselMotion.Step*.45f
        val slow=BookCarouselMotion.release(offset,1,-.3f,true)
        val fast=BookCarouselMotion.release(offset,1,-4f,true)
        assertTrue(fast.duration<slow.duration)
        for(r in listOf(slow,fast)) {
            assertEquals(offset,r.offset(0f),.00001f)
            assertEquals(-BookCarouselMotion.Step,r.offset(1f),.00001f)
            // A slow release approaches the slot without a manufactured sideways bounce.
            var previous=offset
            for(i in 1..1000) {
                val value=r.offset(i/1000f)
                assertTrue(value<=previous+.000001f)
                assertTrue(value>=-BookCarouselMotion.Step-.000001f)
                previous=value
            }
        }
    }
    @Test fun shortDragAndEdgeCancellationReturnToTheSameBook() {
        assertFalse(BookCarouselMotion.commits(.05f,0f));assertTrue(BookCarouselMotion.commits(.25f,0f))
        assertTrue(BookCarouselMotion.commits(.055f*BookCarouselMotion.Step,4f))
        val r=BookCarouselMotion.release(.05f,-1,0f,false)
        assertEquals(.05f,r.offset(0f),.00001f);assertEquals(0f,r.offset(1f),0f)
    }
    @Test fun reversedFastFlickCancelsRatherThanCommittingTheOldDirection() {
        assertFalse(BookCarouselMotion.commits(-BookCarouselMotion.Step*.45f,4f))
        assertFalse(BookCarouselMotion.commits(BookCarouselMotion.Step*.45f,-4f))
        assertTrue(BookCarouselMotion.commits(-BookCarouselMotion.Step*.45f,-4f))
    }
    @Test fun settlingCarriesFingerVelocityAndHasNoTailVelocitySeam() {
        for(direction in listOf(-1,1)) for(commit in listOf(false,true)) {
            val offset=-direction*BookCarouselMotion.Step*.45f
            val velocity=-direction*.7f
            val r=BookCarouselMotion.release(offset,direction,velocity,commit)
            val dt=.0001f
            assertEquals(velocity,(r.offset(dt*1000/r.duration)-r.offset(0f))/dt,.012f)
            val seam=r.mainMillis.toFloat()/r.duration
            val step=.0001f
            val before=(r.offset(seam)-r.offset(seam-step))/(step*r.duration/1000)
            val after=(r.offset(seam+step)-r.offset(seam))/(step*r.duration/1000)
            assertEquals(before,after,.006f)
            assertEquals(0f,(r.offset(1f)-r.offset(.9999f))/(r.duration*.0001f/1000),.006f)
        }
    }
    @Test fun aRegrabbedSignedCoordinateNeverJumpsOnRelease() {
        for(direction in listOf(-1,1)) for(offset in listOf(-.3f,0f,.3f,.8f)) {
            val r=BookCarouselMotion.release(offset,direction,.2f,false)
            assertEquals(offset,r.offset(0f),.000001f)
            assertEquals(0f,r.offset(1f),0f)
            for(i in 0..1000) assertTrue(r.offset(i/1000f).isFinite())
        }
    }
    @Test fun aLateFastFlingNeverThrowsTheShelfPastTheAdjacentSlot() {
        for(from in listOf(.06f,.45f,.8f,.95f,.999f)) for(velocity in listOf(-.3f,-4f,-15f)) {
            val r=BookCarouselMotion.release(-from*BookCarouselMotion.Step,1,velocity,true)
            var previous=r.offset(0f)
            for(i in 1..1000) {
                val next=r.offset(i/1000f)
                assertTrue(next<=previous+.000001f)
                assertTrue(next>=-BookCarouselMotion.Step-.000001f)
                previous=next
            }
        }
    }
    @Test fun sideBookHitTestingUsesTheProjected3dCover() {
        val a=DiaryJournal(id="a");val b=DiaryJournal(id="b")
        val scene=BookScene(journal=a,journals=listOf(a,b))
        val q=BookLibraryGeometry.coverQuad(1080f,2138f,scene,BookLibraryGeometry.pose(1,0f))
        assertEquals(1,BookLibraryGeometry.pick((q.points[0]+q.points[6])/2+5,(q.points[1]+q.points[7])/2,1080f,2138f,scene))
        assertNull(BookLibraryGeometry.pick(10f,10f,1080f,2138f,scene))
    }
}
