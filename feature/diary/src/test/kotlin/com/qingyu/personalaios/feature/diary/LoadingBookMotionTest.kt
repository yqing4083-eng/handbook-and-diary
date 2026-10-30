package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class LoadingBookMotionTest {
    @Test fun loopNeverRunsOutOfPagesAndAllCycleBoundariesMeet() {
        for(i in 1..10000) {
            val time=i*1.35
            val before=LoadingBookMotion.at(time-.00001)
            val after=LoadingBookMotion.at(time+.00001)
            assertEquals(before.current+before.direction*before.turn,after.current+after.direction*after.turn,.0001f)
            assertTrue(after.current in 0..3 && after.turn in 0f..1f)
        }
        for(time in listOf(1e8,1e12,Double.NaN,-10.0)) {
            val frame=LoadingBookMotion.at(time);assertTrue(frame.current in 0..3 && frame.turn in 0f..1f)
        }
    }
}
