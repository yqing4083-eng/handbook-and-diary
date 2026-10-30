package com.qingyu.personalaios.core.glass

import androidx.compose.animation.core.*
import kotlin.math.*

/** Shared response adapted from Kyant0/AndroidLiquidGlass DampedDragAnimation (Apache-2.0).
 * Rigid objects use one progress value rather than independent axis stretching.
 */
object LiquidMotion {
    val Navigation=CubicBezierEasing(.32f,.72f,0f,1f)
    fun settle(stiffness:Float=250f)=spring<Float>(.7f,stiffness,.0005f)
    fun position(progress:Float):Float {
        val p=progress.coerceIn(0f,1f)
        val omega=15.811388f;val damping=.7f;val wd=omega*sqrt(1-damping*damping)
        fun response(t:Float)=1-exp(-damping*omega*t)*(cos(wd*t)+damping*omega/wd*sin(wd*t))
        return response(p*.72f)/response(.72f)
    }
}
