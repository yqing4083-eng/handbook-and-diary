package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class BookRenderPolicyTest {
    private val journal=DiaryJournal(id="selected")
    private val scene=BookScene(journal=journal,journals=listOf(journal))
    @Test fun displayRequestFollowsActualCadenceAndRejectsInvalidRates() {
        listOf(60f,90f,120f,144f).forEach { assertEquals(it,BookRenderPolicy.displayRate(it),0f) }
        listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY).forEach { assertEquals(60f,BookRenderPolicy.displayRate(it),0f) }
    }
    @Test fun fastClosedShelfDoesNotSchedulePageRastersUntilSettledWarmup() {
        listOf(scene,scene.copy(libraryOffset=.2f),scene.copy(libraryRecoil=.01f),scene.copy(creationProgress=.3f))
            .forEach { assertFalse(BookRenderPolicy.preloadPages(it,false)) }
        assertTrue(BookRenderPolicy.preloadPages(scene,true))
        assertTrue(BookRenderPolicy.preloadPages(scene.copy(preparingOpen=true),false))
        assertTrue(BookRenderPolicy.preloadPages(scene.copy(open=.01f),false))
    }
    @Test fun emptyOrDeletedLibrariesNeverManufactureAFallbackBook() {
        listOf(BookScene(),scene.copy(journals=emptyList()),scene.copy(journals=listOf(journal.copy(deletedAt=1)))) .forEach {
            assertFalse(BookRenderPolicy.hasContent(it));assertTrue(BookRenderPolicy.shelf(it).isEmpty())
        }
        assertTrue(BookRenderPolicy.hasContent(scene))
    }
    @Test fun retainedDeletionAndCreationShelvesKeepRealBooksUntilTheirAnimationFinishes() {
        assertTrue(BookRenderPolicy.hasContent(scene.copy(removingId=journal.id,removal=.9f)))
        assertTrue(BookRenderPolicy.hasContent(scene.copy(creationFrom=DiaryJournal(id="previous"),creationProgress=.2f)))
    }
    @Test fun onlyFiveNeighboursAreSubmittedEvenWithThousandsOfBooks() {
        val journals=List(10000) { DiaryJournal(id="b-$it") }
        val shelf=BookRenderPolicy.shelf(scene.copy(journal=journals[5000],journals=journals))
        assertEquals(listOf(-2,2,-1,1,0),shelf.map { it.first })
        assertEquals(listOf(4998,5002,4999,5001,5000),shelf.map { journals.indexOf(it.second) })
    }
    @Test fun motionFramesNeverUploadTwoLargeTextures() {
        assertEquals(2,BookRenderPolicy.uploadCount(scene))
        listOf(scene.copy(libraryOffset=.1f),scene.copy(turn=.2f),scene.copy(open=.5f),
            scene.copy(libraryRecoil=.001f),scene.copy(creationProgress=.4f),scene.copy(removingId="a"))
            .forEach { assertEquals(1,BookRenderPolicy.uploadCount(it)) }
    }
    @Test fun sharedBaseRectPreservesEveryCalibratedCarouselPosition() {
        for((w,h) in listOf(1080f to 2138f,2400f to 920f)) for(frame in 0..100) {
            val state=scene.copy(portrait=w<h,libraryOffset=-BookCarouselMotion.Step*frame/100f)
            val base=ClosedBookGeometry.base(w,h,state)
            for(delta in -2..2) assertEquals(BookShelfGeometry.pose(delta,w,h,state),BookShelfGeometry.pose(delta,w,h,state,base))
        }
    }
}
