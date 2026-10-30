package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** A close has its own clock: immediately gather, turn the packed volume,
 * then recover around its lower edge. Leaves never reopen during the tail. */
internal object BookClosingGeometry {
    const val DurationMillis=620
    const val PackedAt=.68f
    private fun smooth(x:Float)=x.coerceIn(0f,1f).let { it*it*(3-2*it) }
    fun opening(clock:Float):Float = when {
        // 1 -> .66 is a visually stationary opening plateau. Start at its
        // lower boundary; the closing rail keeps the resting angle unchanged.
        clock<.36f -> .66f-.22f*(clock.coerceAtLeast(0f)/.36f).let { it*(2-it) }
        clock<PackedAt -> .44f*(1-smooth((clock-.36f)/(PackedAt-.36f)))
        else -> 0f
    }
    fun scale(clock:Float):Float = when {
        clock<0f -> 1f
        clock<PackedAt -> 1f-.08f*smooth(clock/PackedAt)
        else -> {
            val t=((clock-PackedAt)/(1-PackedAt)).coerceIn(0f,1f)
            // A visible but small overshoot, followed by recovery. Only the packed
            // volume moves: the leaves stay closed throughout the entire tail.
            if(t<.6f) .92f+.11f*smooth(t/.6f)
            else 1.03f-.03f*smooth((t-.6f)/.4f)
        }
    }
    fun bottomOffset(clock:Float)=BookGeometry.CoverHeight*.5f*(scale(clock)-1f)
}
