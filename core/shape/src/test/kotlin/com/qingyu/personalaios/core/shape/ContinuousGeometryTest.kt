package com.qingyu.personalaios.core.shape

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ContinuousGeometryTest {
    @Test fun straightEdgesMeetWithZeroCurvature() {
        assertEquals(0.0, ContinuousCorner.first.curvature(0.0), 1e-12)
        assertEquals(0.0, ContinuousCorner.second.curvature(1.0), 1e-12)
        assertEquals(0.0, ContinuousCorner.first.tangent(0.0).y, 1e-12)
        assertEquals(0.0, ContinuousCorner.second.tangent(1.0).x, 1e-12)
    }

    @Test fun joinMatchesPositionTangentAndCurvature() {
        val a = ContinuousCorner.first
        val b = ContinuousCorner.second
        assertPoint(a.point(1.0), b.point(0.0))
        assertPoint(a.tangent(1.0), b.tangent(0.0))
        assertPoint(a.secondDerivative(1.0), b.secondDerivative(0.0))
        assertEquals(a.curvature(1.0), b.curvature(0.0), 1e-12)
    }

    @Test fun curveIsConvexMonotonicAndStaysInsideBounds() {
        for (curve in listOf(ContinuousCorner.first, ContinuousCorner.second)) {
            var previous = curve.point(0.0)
            for (i in 0..1_000) {
                val t = i / 1_000.0
                val point = curve.point(t)
                val tangent = curve.tangent(t)
                assertTrue(point.x in -1e-12..1.000000000001)
                assertTrue(point.y in -1e-12..1.000000000001)
                assertTrue(point.x >= previous.x - 1e-12)
                assertTrue(point.y >= previous.y - 1e-12)
                assertTrue(tangent.x >= -1e-12 && tangent.y >= -1e-12)
                assertTrue(curve.curvature(t).isFinite() && curve.curvature(t) >= -1e-12)
                previous = point
            }
        }
    }

    @Test fun halvesAreSymmetric() {
        for (i in 0..100) {
            val t = i / 100.0
            val a = ContinuousCorner.first.point(t)
            val b = ContinuousCorner.second.point(1.0 - t)
            assertTrue(abs(a.x + b.y - 1.0) < 1e-12)
            assertTrue(abs(a.y + b.x - 1.0) < 1e-12)
        }
    }

    @Test fun oversizedCornersAreClampedWithoutOverlap() {
        assertEquals(34f, cornerExtent(320f, 68f, 120f), 0f)
        assertEquals(4f, cornerExtent(8f, 40f, 16f), 0f)
        assertEquals(0f, cornerExtent(0f, 40f, 16f), 0f)
        assertEquals(0f, cornerExtent(120f, 40f, 0f), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteDimensionsAreRejected() { cornerExtent(Float.NaN, 30f, 16f) }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCornersAreRejected() { cornerExtent(100f, 30f, -1f) }

    private fun assertPoint(a: Point, b: Point) {
        assertEquals(a.x, b.x, 1e-12)
        assertEquals(a.y, b.y, 1e-12)
    }
}
