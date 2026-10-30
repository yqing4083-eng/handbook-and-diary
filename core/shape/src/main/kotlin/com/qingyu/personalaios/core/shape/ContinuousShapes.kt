package com.qingyu.personalaios.core.shape

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/** Corner values describe the extent of a G2 corner, rather than a circle radius. */
data class IOSContinuousCornerShape(val corner: Dp) : Shape {
    init { require(corner.value.isFinite() && corner.value >= 0f) }
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        ContinuousOutlines.get(size, with(density) { corner.toPx() })
}

/** Smooth end caps; no straight-to-circular-arc junction. */
class ContinuousCapsuleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        ContinuousOutlines.get(size, min(size.width, size.height) / 2f)
    override fun equals(other: Any?) = other is ContinuousCapsuleShape
    override fun hashCode() = ContinuousCapsuleShape::class.java.hashCode()
}

/** A G2 squircle approximation with corners extending to half the shorter side. */
class SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        ContinuousOutlines.get(size, min(size.width, size.height) / 2f)
    override fun equals(other: Any?) = other is SquircleShape
    override fun hashCode() = SquircleShape::class.java.hashCode()
}

private data class GeometryKey(val width: Float, val height: Float, val extent: Float)

private object ContinuousOutlines {
    // Shared, bounded cache. Geometry is created on layout changes, never by a frame clock.
    // Cached outlines are read-only and must not be modified by callers.
    private val cache = object : LinkedHashMap<GeometryKey, Outline>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GeometryKey, Outline>?) = size > 64
    }

    @Synchronized
    fun get(size: Size, requested: Float): Outline {
        val extent = cornerExtent(size.width, size.height, requested)
        val key = GeometryKey(size.width, size.height, extent)
        return cache.getOrPut(key) { Outline.Generic(outlinePath(key)) }
    }

    private fun outlinePath(key: GeometryKey): Path {
        val (w, h, r) = key
        val path = Path()
        if (w == 0f || h == 0f) return path
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        corner(path, r) { x, y -> Point(w - r + x, y) }
        path.lineTo(w, h - r)
        corner(path, r) { x, y -> Point(w - y, h - r + x) }
        path.lineTo(r, h)
        corner(path, r) { x, y -> Point(r - x, h - y) }
        path.lineTo(0f, r)
        corner(path, r) { x, y -> Point(y, r - x) }
        path.close()
        return path
    }

    private inline fun corner(path: Path, extent: Float, transform: (Double, Double) -> Point) {
        for (curve in listOf(ContinuousCorner.first, ContinuousCorner.second)) {
            val p1 = transform(curve.p1.x * extent, curve.p1.y * extent)
            val p2 = transform(curve.p2.x * extent, curve.p2.y * extent)
            val p3 = transform(curve.p3.x * extent, curve.p3.y * extent)
            path.cubicTo(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(),
                p3.x.toFloat(), p3.y.toFloat())
        }
    }
}
