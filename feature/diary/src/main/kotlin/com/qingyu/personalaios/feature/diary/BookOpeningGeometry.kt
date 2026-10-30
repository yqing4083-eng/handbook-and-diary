package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Lift / edge-on / butterfly spread. Closing follows the exact reverse path. */
internal object BookOpeningGeometry {
    const val DurationMillis=560
    private fun smooth(x:Float)=x.coerceIn(0f,1f).let { it*it*(3-2*it) }
    fun spread(progress:Float)=smooth((progress-.22f)/.44f)
    fun yaw(progress:Float):Float {
        val p=progress.coerceIn(0f,1f)
        return if(p<.22f) BookGeometry.ClosedYaw+(-90f-BookGeometry.ClosedYaw)*smooth(p/.22f)
            else -90f*(1-spread(p))
    }
    // Keep the raised cover below the title while it turns edge-on. The previous
    // 44% zoom compounded the near side's perspective and clipped the book top.
    fun lift(progress:Float)=.08f*smooth(sin(PI*min(progress/.56f,1f)).toFloat().coerceAtLeast(0f))
    fun scale(progress:Float)=1f+.04f*smooth(sin(PI*min(progress/.38f,1f)).toFloat().coerceAtLeast(0f))
    // The reference overshoots width while its outer height stays almost fixed.
    // Flatten the rigid V and advance its hinge by the lost outer-edge depth;
    // this opens the width without stretching paper or zooming the entire book.
    fun recoil(progress:Float):Float = if(progress<.70f)
        smooth((progress-.58f)/.12f) else 1f-smooth((progress-.70f)/.30f)
    fun restAngle(progress:Float)=BookGeometry.RestAngle-14.5f*recoil(progress)
    fun railAdvance(angle:Float)=BookGeometry.HalfWidth*(
        sin(BookGeometry.RestAngle*PI.toFloat()/180)-sin(angle*PI.toFloat()/180))
}
