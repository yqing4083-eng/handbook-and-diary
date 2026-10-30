package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.DiaryJournal
import org.junit.Assert.*
import org.junit.Test

class BookShelfGeometryTest {
    private val a=DiaryJournal(id="a")
    private val scene=BookScene(journal=a,journals=listOf(a,DiaryJournal(id="b")))
    @Test fun solidShelfKeepsTheExistingCarouselCentresAndUniformPhysicalDimensions() {
        for((w,h) in listOf(1080f to 2138f,2400f to 920f)) for(i in 0..100) for(delta in -2..2) {
            val s=scene.copy(portrait=w<h,libraryOffset=-BookCarouselMotion.Step*i/100f)
            val target=ClosedBookGeometry.rect(delta,w,h,s)
            val pose=BookShelfGeometry.pose(delta,w,h,s)
            val q=BookLibraryGeometry.coverQuad(w,h,s,pose)
            assertEquals(1f,pose.scale,0f)
            // A real tilted rectangle has tiny perspective differences at its
            // four corners; its projected centre still tracks the original path.
            assertEquals(target.left+target.width/2,(q.points[0]+q.points[2]+q.points[4]+q.points[6])/4,w*.002f)
            assertEquals(target.top+target.height/2,(q.points[1]+q.points[3]+q.points[5]+q.points[7])/4,h*.002f)
            assertTrue(q.points.all { it.isFinite() })
        }
    }
    @Test fun restingSolidCoverExactlyMeetsTheOpeningRootAndCommitDoesNotJump() {
        val pose=BookShelfGeometry.pose(0,1080f,2138f,scene)
        assertEquals(0f,pose.x,.00001f)
        assertEquals(BookLibraryGeometry.HoverHeight,pose.y,.00001f)
        assertEquals(BookLibraryGeometry.HoverDepth,pose.z,.00001f)
        for(delta in -2..2) {
            val before=BookShelfGeometry.pose(delta,1080f,2138f,scene.copy(libraryOffset=-BookCarouselMotion.Step))
            val after=BookShelfGeometry.pose(delta-1,1080f,2138f,scene)
            assertEquals(before.x,after.x,.00001f);assertEquals(before.y,after.y,.00001f);assertEquals(before.z,after.z,.00001f)
        }
    }
    @Test fun landingRecoilStartsAndEndsAtZeroAndDoesNotChangeHorizontalTravel() {
        val release=BookCarouselMotion.release(-BookCarouselMotion.Step*.4f,1,-.5f,true)
        assertEquals(0f,release.recoil(0f),0f)
        assertTrue((0..1000).maxOf { release.recoil(it/1000f) }>.95f)
        assertEquals(0f,release.recoil(1f),0f)
        val s=scene.copy(libraryRecoil=1f)
        val original=BookShelfGeometry.pose(0,1080f,2138f,scene)
        val lifted=BookShelfGeometry.pose(0,1080f,2138f,s)
        assertEquals(original.x,lifted.x,0f)
        assertEquals(.008f,lifted.y-original.y,.000001f)
        assertEquals(.012f,lifted.z-original.z,.000001f)
    }
}
