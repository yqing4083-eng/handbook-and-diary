package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.InkPoint

/** Bake appearance into the existing stroke format; exports and Vulkan textures
 * use the same ink without a database migration or editor-only paint effects. */
internal object PaperPenDynamics {
    fun fingerPressure(speed:Float)=(.0012f/(speed+.0005f)).coerceIn(.25f,1.6f)
    fun color(color: Long, opacity: Float): Long =
        (color and 0x00FFFFFFL) or ((opacity.coerceIn(.05f, 1f) * 255).toLong() shl 24)

    fun pressure(raw: Float, sensitivity: Float): Float =
        (1f + (raw - 1f) * sensitivity.coerceIn(0f, 1f)).coerceIn(0f, 2f)

    fun points(points: List<InkPoint>, straight: Boolean): List<InkPoint> =
        if (straight && points.size > 1) listOf(points.first(), points.last()) else points
}
