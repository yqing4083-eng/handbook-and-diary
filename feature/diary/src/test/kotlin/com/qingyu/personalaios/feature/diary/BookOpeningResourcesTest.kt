package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import com.qingyu.personalaios.domain.DiaryPage

class BookOpeningResourcesTest {
    @Test fun everyFaceDrawnByTheOpeningFanIsReadyAtItsActualResolution() {
        for(count in listOf(1,2,7,16,30,80)) for(current in 0 until count) for(direction in listOf(-1,1)) {
            val plan=BookOpeningResources.pages(current,count,direction)
            val expected=AlbumLeafGeometry.visibleLeaves(current,count).flatMap {
                listOfNotNull(AlbumLeafGeometry.frontPage(it,count),AlbumLeafGeometry.backPage(it,count))
            }.toSet()
            assertEquals(expected,plan.map { it.index }.toSet())
            assertEquals(plan.size,plan.distinctBy { it.index }.size)
            plan.forEach { assertEquals(abs(it.index-current)>1,it.small) }
            assertEquals(current,plan.first().index)
        }
    }
    @Test fun centralAndTurningFacesPrecedeSmallDistantArtwork() {
        assertEquals(listOf(8,9,7),BookOpeningResources.pages(8,30,1).take(3).map { it.index })
        assertEquals(listOf(8,7,9),BookOpeningResources.pages(8,30,-1).take(3).map { it.index })
        assertEquals(15,BookOpeningResources.pages(8,80).size)
    }
    @Test fun emptyAndTransientOutOfRangeCursorsRemainSafe() {
        assertTrue(BookOpeningResources.pages(0,0).isEmpty())
        assertEquals(BookOpeningResources.pages(0,16),BookOpeningResources.pages(-3,16))
        assertEquals(BookOpeningResources.pages(15,16),BookOpeningResources.pages(90,16))
    }
    @Test fun idleWarmupStopsForAnyShelfOrPageMotion() {
        val rest=BookScene(pages=listOf(DiaryPage(order=0)))
        assertTrue(BookOpeningResources.canWarmWhileClosed(rest))
        listOf(rest.copy(libraryOffset=.01f),rest.copy(libraryRecoil=.01f),rest.copy(open=.01f),
            rest.copy(turn=.01f),rest.copy(overview=.01f),rest.copy(creationProgress=.99f),
            rest.copy(removingId="old"),rest.copy(closing=0f),rest.copy(preparingOpen=true),rest.copy(pages=emptyList()))
            .forEach { assertFalse(BookOpeningResources.canWarmWhileClosed(it)) }
    }
    @Test fun idleJobsCannotWarmAStaleBookOrAnEditedPageRevision() {
        val rest=BookScene(pages=listOf(DiaryPage(order=0)))
        assertTrue(BookOpeningResources.sameArtwork(rest,rest.copy(libraryOffset=.2f)))
        assertFalse(BookOpeningResources.sameArtwork(rest,rest.copy(journal=rest.journal.copy(id="another"))))
        assertFalse(BookOpeningResources.sameArtwork(rest,rest.copy(pages=listOf(rest.pages[0].copy(revision=1)))))
        assertFalse(BookOpeningResources.sameArtwork(null,rest))
    }
}
