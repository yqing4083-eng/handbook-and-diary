package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import com.qingyu.personalaios.domain.DiaryJournal

class BookRemovalGeometryTest {
    @Test fun deletingFirstMiddleOrLastBookDoesNotJumpAnySurvivingVolumeAtCommit() {
        for (count in listOf(2,3,8)) for (index in 0 until count) {
            val books=List(count) { DiaryJournal(id="b$it") }
            val direction=BookRemovalGeometry.direction(index,count)
            val selected=books[index+direction]
            val removed=BookScene(journal=books[index],journals=books,removingId=books[index].id,
                libraryOffset=-direction*BookCarouselMotion.Step,removal=1f)
            val remaining=books.filter { it.id!=books[index].id }
            val committed=BookScene(journal=selected,journals=remaining)
            for ((w,h) in listOf(1080f to 2138f,2400f to 942f)) {
                for (book in remaining) {
                    val a=BookShelfGeometry.pose(books.indexOf(book)-index,w,h,removed.copy(portrait=w<h))
                    val b=BookShelfGeometry.pose(remaining.indexOf(book)-remaining.indexOf(selected),w,h,committed.copy(portrait=w<h))
                    assertPose(a,b)
                }
            }
        }
    }
    @Test fun onlyTheSuccessorsSideClosesTheVacatedSlot() {
        val books=List(5) { DiaryJournal(id="b$it") }
        val original=BookScene(journal=books[2],journals=books)
        for (step in 0..100) {
            val offset=-BookCarouselMotion.Step*step/100f
            val scene=original.copy(removingId="b2",libraryOffset=offset)
            for(delta in -2..-1) {
                assertEquals(0f,BookRemovalGeometry.offset(delta,scene),0f)
                assertPose(BookShelfGeometry.pose(delta,1080f,2138f,original),BookShelfGeometry.pose(delta,1080f,2138f,scene))
            }
            for(delta in 1..2) {
                assertEquals(offset,BookRemovalGeometry.offset(delta,scene),0f)
                assertPose(BookShelfGeometry.pose(delta,1080f,2138f,original.copy(libraryOffset=offset)),
                    BookShelfGeometry.pose(delta,1080f,2138f,scene))
            }
        }
    }
    @Test fun singletonHasNoInventedSuccessorAndNormalCarouselRemainsUnchanged() {
        assertEquals(0,BookRemovalGeometry.direction(0,1))
        assertEquals(0,BookRemovalGeometry.direction(-1,4))
        assertEquals(0,BookRemovalGeometry.direction(4,4))
        val scene=BookScene(libraryOffset=.4f)
        for(delta in -3..3) assertEquals(.4f,BookRemovalGeometry.offset(delta,scene),0f)
    }
    private fun assertPose(a:LibraryPose,b:LibraryPose) {
        assertEquals(a.x,b.x,.00001f);assertEquals(a.y,b.y,.00001f);assertEquals(a.z,b.z,.00001f)
        assertEquals(a.pitch,b.pitch,.00001f);assertEquals(a.yaw,b.yaw,.00001f);assertEquals(a.scale,b.scale,.00001f)
    }
}
