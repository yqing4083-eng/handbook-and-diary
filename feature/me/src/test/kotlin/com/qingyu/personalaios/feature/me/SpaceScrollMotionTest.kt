package com.qingyu.personalaios.feature.me

import org.junit.Assert.*
import org.junit.Test

class SpaceScrollMotionTest {
    @Test fun avatarShrinksIntoHeaderAndRetracesExactlyWithScroll() {
        val rest=SpaceScrollMotion.avatar(0f);val mid=SpaceScrollMotion.avatar(.5f);val end=SpaceScrollMotion.avatar(1f)
        assertEquals(1f,rest.width,0f);assertEquals(1f,rest.alpha,0f)
        assertTrue(mid.width<rest.width && mid.height>mid.width && mid.neck>0f)
        assertEquals(0f,end.alpha,0f);assertEquals(1f,end.rise,0f)
        assertEquals(mid,SpaceScrollMotion.avatar(.5f))
    }
    @Test fun heroUsesOneLinearCoordinateAndRemainsPinnedAfterHeaderLeaves() {
        assertEquals(0f,SpaceScrollMotion.progress(0,0f,148f),0f)
        assertEquals(.5f,SpaceScrollMotion.progress(0,74f,148f),0f)
        assertEquals(1f,SpaceScrollMotion.progress(1,0f,148f),0f)
        assertEquals(1f,SpaceScrollMotion.progress(12,0f,148f),0f)
    }
    @Test fun snapChoosesOnlyHeaderEndpointsAndNeverMovesAnAlreadyPinnedFeed() {
        assertEquals(0f,SpaceScrollMotion.snap(30f,148f),0f)
        assertEquals(148f,SpaceScrollMotion.snap(100f,148f),0f)
        assertEquals(170f,SpaceScrollMotion.snap(170f,148f),0f)
    }
}
