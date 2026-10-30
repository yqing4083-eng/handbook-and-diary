package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Progressive layers calibrated with the planar panels and their perspective. */
internal object AccordionGeometry {
    // About 63 px of nearest-neighbour exposure in the 720 px portrait reference.
    // Panel angle, lens and layer distance are calibrated together.
    const val LayerSpacing=.375f
    const val LayerDepth=.62f
    const val MaxLayerDepth=2.67f
    const val ScreenCentre=.513f
    const val ActionsTop=.826f
    fun fold(relative:Float)=BookGeometry.RestAngle
    fun depth(distance:Float):Float {
        val d=abs(distance)
        // New 1546px reference: successive exposed heights are about
        // .925/.880/.843/.807/.775/.744 of the central sheet. No depth plateau.
        return if(d<=1f) LayerDepth*d+.12f*d*(1-d) else LayerDepth+.41f*(d-1)
    }
    fun lateral(relative:Float):Float {
        val d=abs(relative);val t=d.coerceIn(0f,1f)
        return sign(relative)*(.23f*d+.145f*t*t*(3-2*t))
    }
    fun resting(relative:Float,selection:Float=0f)=PagePose(
        lateral(relative),0f,.03f-depth(relative))
    fun pose(delta:Int,progress:Float=0f,direction:Int=1,selection:Float=0f):PagePose {
        val t=progress.coerceIn(-.12f,1.12f)
        return resting(delta-direction*t,selection)
    }
    /** The two exposed under-pages meet at one fixed hinge while the fan travels.
     * Moving both under-pages with their stacks used to reveal a background slit.
     */
    fun underlay(progress:Float,selection:Float=0f):PagePose = resting(0f,selection).let {
        it.copy(z=it.z-.006f*sin(PI.toFloat()*progress.coerceIn(0f,1f)))
    }
}
