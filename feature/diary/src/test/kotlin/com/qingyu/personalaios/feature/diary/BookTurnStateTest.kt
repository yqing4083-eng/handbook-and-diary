package com.qingyu.personalaios.feature.diary

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BookTurnStateTest {
    private class Clock(val value:()->Float): MonotonicFrameClock {
        var now=0L;var peak=0f
        override suspend fun <R> withFrameNanos(onFrame: (Long)->R): R {
            delay(16);now+=16_666_667;peak=maxOf(peak,value());return onFrame(now)
        }
    }
    @Test fun turningLeafSettlesWithoutGlobalRailOvershootOrLosingThePageIndex()=runTest {
        val turn=BookTurnState(mutableStateOf(0));val clock=Clock { turn.progress }
        withContext(clock) { turn.finish(true,true,360) }
        assertTrue("no global rail overshoot",clock.peak<=1f)
        assertEquals(1,turn.current);assertEquals(0f,turn.progress,0f)
    }
    @Test fun cancelledGestureReturnsToTheOriginalPage()=runTest {
        val turn=BookTurnState(mutableStateOf(3));turn.animation.snapTo(.4f);turn.direction=-1
        withContext(Clock { turn.progress }) { turn.finish(false,true) }
        assertEquals(3,turn.current);assertEquals(0f,turn.progress,0f)
    }
    @Test fun repeatedAdditionsTurnExactlyOneLeafAndFinishWithinTheMeasuredWindow()=runTest {
        val turn=BookTurnState(mutableStateOf(2));val clock=Clock { turn.progress }
        repeat(3) { n ->
            val before=clock.now
            var prepared=false
            withContext(clock) { turn.addedPage(true) {
                assertTrue(turn.adding)
                assertEquals(0f,turn.reveal,0f)
                assertEquals(0f,turn.progress,0f)
                prepared=true
            } }
            assertTrue(prepared);assertFalse(turn.adding)
            assertEquals(3+n,turn.current)
            assertEquals(0f,turn.progress,0f)
            // Includes the newly measured right-side exposure before the turn.
            assertTrue((clock.now-before) in 420_000_000L..460_000_000L)
        }
        assertTrue(clock.peak>1f && clock.peak<=1.026f)
    }
    @Test fun disabledMotionStillCommitsTheNewPageOnce()=runTest {
        val turn=BookTurnState(mutableStateOf(2))
        turn.addedPage(false)
        assertEquals(3,turn.current);assertEquals(0f,turn.progress,0f)
    }
    @Test fun trackingUpdatesSynchronouslyAndReleaseDoesNotResetToZero()=runTest {
        val turn=BookTurnState(mutableStateOf(3))
        turn.beginDrag();turn.dragTo(.65f)
        assertEquals(.65f,turn.progress,0f)
        val clock=Clock { turn.progress }
        withContext(clock) { turn.finish(true,true) }
        assertTrue(clock.now<600_000_000L)
        assertEquals(4,turn.current);assertEquals(0f,turn.progress,0f)
    }
    @Test fun springCanBeGrabbedWithoutLosingItsCurrentPose()=runTest {
        val turn=BookTurnState(mutableStateOf(3))
        turn.beginDrag();turn.dragTo(.4f)
        val clock=Clock { turn.progress }
        val job=launch(clock) { turn.finish(true,true) }
        delay(80)
        assertTrue(turn.settling)
        val pose=turn.progress.coerceIn(0f,.999f)
        turn.beginDrag();job.cancelAndJoin()
        assertEquals(pose,turn.progress,0f)
        turn.dragTo(.2f)
        withContext(clock) { turn.finish(false,true) }
        assertEquals(3,turn.current);assertEquals(0f,turn.progress,0f)
    }
}
