package com.qingyu.personalaios.core.shape

import kotlin.math.hypot
import kotlin.math.min

internal data class Point(val x: Double, val y: Double) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(scale: Double) = Point(x * scale, y * scale)
}

internal data class Cubic(val p0: Point, val p1: Point, val p2: Point, val p3: Point) {
    fun point(t: Double): Point {
        val u = 1.0 - t
        return p0 * (u * u * u) + p1 * (3 * u * u * t) +
            p2 * (3 * u * t * t) + p3 * (t * t * t)
    }
    fun tangent(t: Double): Point {
        val u = 1.0 - t
        return (p1 - p0) * (3 * u * u) + (p2 - p1) * (6 * u * t) +
            (p3 - p2) * (3 * t * t)
    }
    fun secondDerivative(t: Double): Point =
        (p2 - p1 * 2.0 + p0) * (6 * (1.0 - t)) +
            (p3 - p2 * 2.0 + p1) * (6 * t)

    fun curvature(t: Double): Double {
        val first = tangent(t)
        val second = secondDerivative(t)
        val speed = hypot(first.x, first.y)
        return (first.x * second.y - first.y * second.x) / (speed * speed * speed)
    }
}

/**
 * Original, symmetric two-cubic corner. Coordinates run from (0,0) to (1,1).
 * End curvature is zero, so straight edges meet the corner with G2 continuity.
 * The interior join has identical first AND second derivatives (C2).
 * This is an iOS-like continuous corner, not Apple's proprietary geometry.
 */
internal object ContinuousCorner {
    val first = Cubic(Point(0.0, 0.0), Point(.36, 0.0), Point(.68, 0.0), Point(.84, .16))
    val second = Cubic(Point(.84, .16), Point(1.0, .32), Point(1.0, .64), Point(1.0, 1.0))
}

/** Public normalized profile for render engines which must match this same contour. */
fun continuousCornerPoint(progress: Double): Pair<Double, Double> {
    require(progress.isFinite() && progress in 0.0..1.0)
    val point = if (progress <= .5) ContinuousCorner.first.point(progress * 2)
        else ContinuousCorner.second.point((progress - .5) * 2)
    return point.x to point.y
}

internal fun cornerExtent(width: Float, height: Float, requested: Float): Float {
    require(width.isFinite() && height.isFinite() && requested.isFinite())
    require(width >= 0f && height >= 0f && requested >= 0f)
    return min(requested, min(width, height) / 2f)
}
