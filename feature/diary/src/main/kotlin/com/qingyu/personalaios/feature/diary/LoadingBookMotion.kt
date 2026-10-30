package com.qingyu.personalaios.feature.diary

import kotlin.math.*

internal object LoadingBookMotion {
    data class Frame(val current:Int,val direction:Int,val turn:Float)
    fun at(seconds:Double):Frame {
        val time=if(seconds.isFinite()) seconds.coerceAtLeast(0.0) else 0.0
        val step=(floor(time/1.35)%6).toInt()
        val phase=((time%1.35)/1.10).coerceIn(0.0,1.0)
        return Frame(if(step<3) step else 6-step,if(step<3) 1 else -1,(phase*phase*(3-2*phase)).toFloat())
    }
}
