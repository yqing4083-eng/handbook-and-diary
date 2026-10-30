package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Velocity-aware gesture settling; physical geometry lives in BookLibraryGeometry. */
internal object BookCarouselMotion {
    const val Spacing=480f/469f
    const val Step=.685f*Spacing
    const val CreationMillis=800
    fun coordinateScale(width:Float,height:Float,portrait:Boolean):Float {
        if(width<=0f || height<=0f) return 1f
        val actualStep=BookGeometry.CoverWidth*BookGeometry.focal(width,height,portrait,0f)/BookLibraryGeometry.ClosedViewDepth/width*Spacing
        return Step/actualStep
    }
    private fun clamp(p:Float)=p.coerceIn(0f,1f)
    fun controls(offset:Float):Float {
        val p=clamp(abs(offset/Step))
        fun smooth(t:Float)=t*t*(3-2*t)
        return when { p<=.34f -> 1-smooth(p/.34f);p<.78f -> 0f;else -> smooth((p-.78f)/.22f) }
    }
    fun speed(velocity:Float)=velocity*480f/Step/1000f
    fun commits(offset:Float,velocity:Float):Boolean {
        val p=abs(offset/Step)
        val fast=abs(speed(velocity))>=1.15f
        // A deliberate reversal cancels the old destination instead of treating
        // both velocity signs as a forward fling.
        if(fast && offset*velocity<0f && p<.85f) return false
        return p+1e-6f>=.30f || (fast && offset*velocity>0f && p+1e-6f>=.055f)
    }
    fun release(offset:Float,direction:Int,velocity:Float,commit:Boolean):BookRelease {
        val from=-direction*offset/Step
        val measuredVelocity=(-direction*velocity/Step).coerceIn(-24f,24f)
        val target=if(commit) 1f else 0f
        val remaining=target-from
        val baseRate=(if(commit) 14f else 18f)+(abs(measuredVelocity)-1f).coerceIn(0f,8f)
        val toward=measuredVelocity*sign(remaining)
        val rate=max(baseRate,(toward/abs(remaining).coerceAtLeast(.01f)).coerceIn(0f,40f))
        // A late high-speed flick must not throw the shelf past the next book.
        // Increase braking near the slot, then cap only velocity aimed at that
        // slot; outward cancellation velocity remains continuous.
        val initialVelocity=if(abs(remaining)<.000001f) 0f else if(toward>rate*abs(remaining))
            sign(remaining)*rate*abs(remaining) else measuredVelocity
        val a=from-target;val b=initialVelocity+rate*a
        // Critically damped travel carries the measured finger velocity. Its
        // late decay resembles the recording's ~101 ms image-space tail; this
        // is a reconstruction, not an assertion about Paper's source curve.
        var main=120
        while(main<1100) {
            val t=main/1000f;val decay=exp(-rate*t)
            val error=(a+b*t)*decay
            val v=(b-rate*(a+b*t))*decay
            if(abs(error)<.0015f && abs(v)<.025f) break
            main+=4
        }
        return BookRelease(from,direction,commit,main,80,initialVelocity,rate)
    }
}

internal data class BookRelease(val from:Float,val direction:Int,val commit:Boolean,val mainMillis:Int,
    val settleMillis:Int,val initialVelocity:Float,val rate:Float) {
    val duration get()=mainMillis+settleMillis
    val targetOffset get()=if(commit) -direction*BookCarouselMotion.Step else 0f
    private val target get()=if(commit) 1f else 0f
    private fun position(seconds:Float):Float {
        val a=from-target;val b=initialVelocity+rate*a
        return target+(a+b*seconds)*exp(-rate*seconds)
    }
    private fun velocity(seconds:Float):Float {
        val a=from-target;val b=initialVelocity+rate*a
        return (b-rate*(a+b*seconds))*exp(-rate*seconds)
    }
    /** Landing recoil belongs to lift/depth, not a forced sideways overshoot. */
    fun recoil(progress:Float):Float {
        if(!commit || progress<=0f || progress>=1f) return 0f
        val start=from.coerceIn(.82f,.98f)
        val p=-direction*offset(progress)/BookCarouselMotion.Step
        val u=((p-start)/(1-start)).coerceIn(0f,1f)
        return 64f*(u*(1-u)).pow(3)
    }
    fun offset(progress:Float):Float {
        val time=progress.coerceIn(0f,1f)*duration
        if(progress>=1f) return targetOffset
        val p=if(time<=mainMillis) position(time/1000f) else {
            // Finish at the exact coordinate with matching position AND velocity
            // on both ends, avoiding the former zero-to-negative tail seam.
            val u=(time-mainMillis)/settleMillis;val u2=u*u;val u3=u2*u
            val start=position(mainMillis/1000f)
            val tangent=velocity(mainMillis/1000f)*settleMillis/1000f
            (2*u3-3*u2+1)*start+(u3-2*u2+u)*tangent+(-2*u3+3*u2)*target
        }
        return -direction*p*BookCarouselMotion.Step
    }
}
