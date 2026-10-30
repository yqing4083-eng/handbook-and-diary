package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class BookGestureMotionTest {
    @Test fun interruptedTurnContinuesFromTheFingerPoseAndCanReverse() {
        assertEquals(.7f,BookGestureMotion.turn(.7f,0f),0f)
        assertTrue(BookGestureMotion.turn(.7f,.1f)<.7f)
        assertTrue(BookGestureMotion.turn(.7f,-.1f)>.7f)
        assertTrue(BookGestureMotion.turn(.1f,.2f)<0f)
    }
    @Test fun releaseUsesItsDirectionInsteadOfCommittingEveryFastFling() {
        assertTrue(BookGestureMotion.commits(.1f,2f))
        assertFalse(BookGestureMotion.commits(.7f,-2f))
        assertFalse(BookGestureMotion.commits(.2f,0f))
        assertTrue(BookGestureMotion.commits(.5f,0f))
    }
    @Test fun pinchTracksBothDirectionsBeforeAnyFingerIsLifted() {
        assertEquals(0f,BookGestureMotion.closing(1f),0f)
        assertTrue(BookGestureMotion.closing(.9f)>0f)
        assertTrue(BookGestureMotion.closing(.7f)>BookGestureMotion.closing(.9f))
        assertFalse(BookGestureMotion.commitClose(BookGestureMotion.closing(.95f)))
        assertTrue(BookGestureMotion.commitClose(BookGestureMotion.closing(.7f)))
        assertEquals(BookClosingGeometry.PackedAt,BookGestureMotion.closing(.01f),0f)
    }
}
