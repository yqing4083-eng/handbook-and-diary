package com.qingyu.personalaios.core.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.*

/** Bounded deformation, following AndroidLiquidGlass's bounded drag/scale approach.
 * Geometry is independent of content opacity, so interrupted and nested morphs can be rebased.
 */
object LiquidMenuMotion {
    fun bounds(anchor: Rect, target: Rect, progress: Float, lift: Float): Rect {
        val p = if (progress.isFinite()) progress.coerceIn(-.025f, 1.035f) else 0f
        if (p == 0f) return anchor
        if (p == 1f) return target
        val phase = p.coerceIn(0f, 1f)
        val pulse = sin(PI.toFloat() * phase).pow(2)
        // A short neck followed by a rounded spread, never a 1px strip for large forms.
        val width = dimension(anchor.width, target.width, p) * (1f - .12f * pulse)
        val height = dimension(anchor.height, target.height, p) * (1f + .07f * pulse)
        val direction = if (target.center.y < anchor.center.y) -1f else 1f
        val travel = min(abs(target.center.y - anchor.center.y) * .08f,
            min(lift.coerceAtLeast(0f), min(anchor.height, target.height) * .18f))
        val center = anchor.center + (target.center - anchor.center) * p + Offset(0f, direction * travel * pulse)
        return Rect(center - Offset(width / 2f, height / 2f), Size(width, height))
    }

    private fun dimension(start: Float, end: Float, progress: Float): Float =
        (start + (end - start) * progress).coerceIn(min(start, end) * .94f, max(start, end) * 1.035f)

    fun optionReveal(progress: Float, bottomDistance: Float): Float =
        ((progress - .50f - .035f * bottomDistance.coerceIn(0f, 3f)) / .33f).coerceIn(0f, 1f)

    /** Root-local placement. Prefer the side with enough space, then keep the entire menu visible. */
    fun place(anchor: Rect, menu: Size, safe: Rect, gap: Float, preferBelow: Boolean = false): Offset {
        val above = anchor.top - gap - safe.top
        val below = safe.bottom - anchor.bottom - gap
        val y = when {
            preferBelow && below >= menu.height -> anchor.bottom + gap
            above >= menu.height || above >= below -> anchor.top - gap - menu.height
            else -> anchor.bottom + gap
        }
        return Offset(
            (anchor.center.x - menu.width / 2f).coerceIn(safe.left, max(safe.left, safe.right - menu.width)),
            y.coerceIn(safe.top, max(safe.top, safe.bottom - menu.height)),
        )
    }
}
