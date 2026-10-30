package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.InkPoint
import kotlin.math.hypot

/**
 * Pen geometry for the diary canvas.
 *
 * Samples arrive as a coarse polyline: a quick stroke can leave tens of pixels between two
 * points, and rasterizing that polyline shows every one of those samples as a facet. The
 * classic pen answer is a quadratic curve anchored on the mid-points of consecutive samples,
 * using the samples themselves as control points. The curve then follows the pen path
 * through the mid-points instead of cutting the corners, and each corner is rounded by
 * exactly one sample.
 *
 * [sample] evaluates that curve, so the saved page, the live stroke, the book thumbnails and
 * the exported image all render the same shape from this one implementation.
 */
internal object InkCurve {
    /** Samples per input segment. The curve is visually settled by four. */
    const val Density = 4
    /** Two samples are enough where the stroke width already varies along the path. */
    const val WidthDensity = 2
    /** Segments shorter than this (normalized units) are too small to show a facet. */
    const val Dense = .016f

    /**
     * Points to rasterize for [points]. Inputs that are already dense -- smart shapes, traced
     * fill outlines, or a slow stroke sampled finely by the digitizer -- are returned as they
     * are, so smoothing never costs anything where it cannot be seen.
     */
    fun sample(points: List<InkPoint>, perSegment: Int = Density): List<InkPoint> {
        if (points.size < 3 || perSegment <= 0) return points
        if (dense(points)) return points
        val out = ArrayList<InkPoint>(1 + (points.size - 2) * perSegment + 1)
        out.add(points.first())
        for (i in 1 until points.size - 1) {
            // The curve is anchored on mid-points, but starts and ends exactly on the pen
            // path, so the first and last half segments stay straight.
            val start = if (i == 1) points[0] else mid(points[i - 1], points[i])
            val end = mid(points[i], points[i + 1])
            val control = points[i]
            for (k in 1..perSegment) out.add(quadratic(start, control, end, k.toFloat() / perSegment))
        }
        out.add(points.last())
        return out
    }

    private fun dense(points: List<InkPoint>): Boolean {
        var length = 0f
        for (i in 1 until points.size) length += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return length / (points.size - 1) < Dense
    }

    private fun mid(a: InkPoint, b: InkPoint) = InkPoint((a.x + b.x) / 2, (a.y + b.y) / 2, (a.pressure + b.pressure) / 2)

    private fun quadratic(start: InkPoint, control: InkPoint, end: InkPoint, t: Float): InkPoint {
        val u = 1f - t
        val a = u * u; val b = 2f * u * t; val c = t * t
        return InkPoint(
            (a * start.x + b * control.x + c * end.x).coerceIn(0f, 1f),
            (a * start.y + b * control.y + c * end.y).coerceIn(0f, 1f),
            (start.pressure + (end.pressure - start.pressure) * t).coerceIn(0f, 2f),
        )
    }
}
