package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class BoundedInkTraceTest {
    @Test fun exitingAndReenteringDoesNotHugTheEdge() {
        val trace=BoundedInkTrace()
        listOf(InkSample(.5f,.2f),InkSample(1.2f,.2f),InkSample(1.3f,.8f),InkSample(.5f,.8f)).forEach(trace::add)
        assertEquals(2,trace.paths.size)
        assertEquals(1f,trace.paths[0].last().x,0f);assertEquals(.2f,trace.paths[0].last().y,.00001f)
        assertEquals(1f,trace.paths[1].first().x,0f);assertEquals(.8f,trace.paths[1].first().y,.00001f)
    }
    @Test fun outsideToOutsideCrossingStillPaintsTheInsideSegment() {
        val trace=BoundedInkTrace();trace.add(InkSample(-1f,.5f,.3f));trace.add(InkSample(2f,.5f,1.2f))
        assertEquals(1,trace.paths.size);assertEquals(0f,trace.paths.single().first().x,0f);assertEquals(1f,trace.paths.single().last().x,0f)
        assertEquals(.6f,trace.paths.single().first().pressure,.00001f)
    }
    @Test fun fullyOutsideTouchesAreEmptyAndCornerCrossingsAreBounded() {
        val trace=BoundedInkTrace();trace.add(InkSample(-1f,-1f));trace.add(InkSample(-.5f,-.5f));assertTrue(trace.paths.isEmpty())
        trace.add(InkSample(2f,2f));assertEquals(listOf(InkPoint(0f,0f),InkPoint(1f,1f)),trace.paths.single())
        trace.clear();assertTrue(trace.paths.isEmpty())
    }
}
