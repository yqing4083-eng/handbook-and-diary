package com.qingyu.personalaios.feature.diary

import kotlin.math.*

internal data class PagePose(val x:Float,val y:Float,val z:Float)

/** A continuous fan of real textured sheets, before and after the selected ID changes. */
internal object BookPageMotion {
    fun pose(relative:Float,selection:Float=0f):PagePose {
        return AccordionGeometry.resting(relative,selection)
    }
    fun relative(delta:Int,progress:Float,direction:Int)=delta-direction*progress.coerceIn(0f,1f)
    fun angle(side:Int,relative:Float)=-side*AccordionGeometry.fold(relative)
    fun flapBend(progress:Float)=.018f+.055f*sin(PI*progress.coerceIn(0f,1f)).toFloat().pow(2)
    fun outgoingBack(progress:Float)=smooth((progress-.72f)/.28f)
    fun incomingBack(progress:Float)=1-smooth(progress/.28f)
    private fun smooth(p:Float)=p.coerceIn(0f,1f).let { it*it*(3-2*it) }
}
