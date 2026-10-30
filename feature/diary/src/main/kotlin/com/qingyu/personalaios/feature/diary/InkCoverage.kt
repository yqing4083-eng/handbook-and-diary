package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Recover subpixel ink visibility without dilation or changing selected opacity. */
internal object InkCoverage {
    fun alpha(sum:Int,peak:Int,samples:Int)=sqrt(sum.toFloat()/samples*peak).roundToInt().coerceIn(0,peak)
}
