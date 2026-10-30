package com.qingyu.personalaios.feature.diary
import com.qingyu.personalaios.domain.InkPoint
import org.junit.Assert.*
import org.junit.Test

class InkCurveTest {
    @Test fun theEndsAreUntouchedAndTheCountFollowsTheDensity() {
        val points=listOf(InkPoint(0f,0f),InkPoint(.5f,.5f),InkPoint(1f,0f))
        val traced=InkCurve.sample(points)
        assertEquals(1+(points.size-2)*InkCurve.Density+1,traced.size)
        assertEquals(points.first(),traced.first());assertEquals(points.last(),traced.last())
        assertTrue(traced.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    /** A corner is rounded, never cut: the trace stops at the mid-point of the next segment. */
    @Test fun acornerIsRoundedByOneSampleInsteadOfReachingIt() {
        val corner=InkPoint(.5f,.5f)
        val traced=InkCurve.sample(listOf(InkPoint(0f,0f),corner,InkPoint(1f,0f)))
        assertEquals(.75f,traced[traced.size-2].x,.0001f)
        assertEquals(.25f,traced[traced.size-2].y,.0001f)
        assertTrue(traced.maxOf { it.y }<corner.y)
        assertFalse(traced.any { it==corner })
    }

    @Test fun anAlreadyDenseStrokeIsReturnedAsItIs() {
        val dense=List(40) { InkPoint(.4f+it*.004f,.5f) }
        assertSame(dense,InkCurve.sample(dense))
        assertSame(dense,InkCurve.sample(dense,InkCurve.WidthDensity))
        val sparse=listOf(InkPoint(.1f,.1f),InkPoint(.4f,.4f),InkPoint(.8f,.2f))
        assertSame(sparse,InkCurve.sample(sparse,0))
        val two=sparse.take(2)
        assertSame(two,InkCurve.sample(two))
        assertSame(two,InkCurve.sample(two,InkCurve.WidthDensity))
    }

    @Test fun pressureTravelsBetweenTheSamplesItInterpolates() {
        val points=listOf(InkPoint(.1f,.1f,1.9f),InkPoint(.5f,.3f,.1f),InkPoint(.9f,.9f,1.4f))
        val traced=InkCurve.sample(points,InkCurve.WidthDensity)
        assertTrue(traced.all { it.pressure in 0f..2f })
        assertEquals(1.9f,traced.first().pressure,.0001f)
        assertEquals(1.4f,traced.last().pressure,.0001f)
        assertEquals(.75f,traced[traced.size-2].pressure,.0001f)
    }
}
