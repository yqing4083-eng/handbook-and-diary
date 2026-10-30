package com.qingyu.personalaios.domain
import org.junit.Assert.*
import org.junit.Test

class InkSelectionTest {
    @Test fun lassoSeparatesInteriorFromExteriorAndRejectsIncompleteGesture() {
        val polygon=listOf(InkPoint(.1f,.1f),InkPoint(.8f,.1f),InkPoint(.8f,.8f),InkPoint(.1f,.8f))
        assertTrue(inkInside(polygon,InkPoint(.4f,.5f)))
        assertFalse(inkInside(polygon,InkPoint(.9f,.5f)))
        assertFalse(inkInside(polygon.take(2),InkPoint(.4f,.5f)))
    }
    @Test fun movingAClipKeepsEveryPointInsidePaperWithoutChangingRelativeDistances() {
        val ink=listOf(InkStroke(points=listOf(InkPoint(.2f,.3f),InkPoint(.8f,.9f))))
        val moved=ink.shifted(.5f,.5f).first().points
        assertEquals(1f,moved.last().x,.0001f);assertEquals(1f,moved.last().y,.0001f)
        assertEquals(.6f,moved.last().x-moved.first().x,.0001f)
        assertEquals(.6f,moved.last().y-moved.first().y,.0001f)
        assertEquals(ink.first().id,ink.shifted(.5f,.5f).first().id)
    }
    @Test fun erasingUsesTheStoredAspectOfTheCanvas() {
        val dot=InkStroke(points=listOf(InkPoint(.5f,.5f)))
        assertTrue(dot.hits(InkPoint(.5f,.53f),aspect=1.1f))
        assertFalse(dot.hits(InkPoint(.5f,.53f),aspect=.5f))
    }
}
