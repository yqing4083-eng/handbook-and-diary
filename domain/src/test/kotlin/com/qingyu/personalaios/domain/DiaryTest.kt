package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class DiaryTest {
    @Test fun newJournalUsesPlainPaperWithoutOverridingAnExplicitTemplate() {
        assertEquals(PaperStyle.PLAIN,DiaryJournal().template)
        assertEquals(PaperStyle.PLAIN,DiaryPage(order=0).style)
        val stored=DiaryJournal(template=PaperStyle.LINED)
        assertEquals(PaperStyle.LINED,stored.copy(name="Renamed").template)
    }
    @Test fun eraserHitsMiddleOfSparseStroke() {
        val ink = InkStroke(points = listOf(InkPoint(.1f,.5f), InkPoint(.9f,.5f)))
        assertTrue(ink.hits(InkPoint(.5f,.5f)))
        assertFalse(ink.hits(InkPoint(.5f,.7f)))
    }
    @Test fun eraserDoesNotHitInfiniteExtension() {
        val ink = InkStroke(points = listOf(InkPoint(.2f,.5f), InkPoint(.4f,.5f)))
        assertFalse(ink.hits(InkPoint(.9f,.5f)))
    }
    @Test fun singleDotCanBeErased() {
        val ink = InkStroke(points = listOf(InkPoint(.3f,.4f)))
        assertTrue(ink.hits(InkPoint(.3f,.4f)))
        assertFalse(ink.hits(InkPoint(.7f,.8f)))
    }
    @Test fun repeatedPointsRemainFinite() {
        val p = InkPoint(.3f,.4f)
        assertTrue(InkStroke(points = listOf(p,p)).hits(p))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidCoordinates() { InkPoint(Float.NaN, .5f) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOutOfBoundsCoordinates() { InkPoint(2f, .5f) }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyStroke() { InkStroke(points = emptyList()) }
    @Test fun pageIdentitySurvivesEditingAndReordering() {
        val page = DiaryPage(order = 0, strokes = listOf(InkStroke(points = listOf(InkPoint(.1f,.2f)))))
        assertEquals(page.id, page.copy(order = 1, title = "Edited", revision = 1).id)
        assertEquals(page.strokes, page.copy(body = "Mixed content").strokes)
        assertNotEquals(page.id, DiaryPage(order = 0).id)
    }
}
