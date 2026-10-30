package com.qingyu.personalaios.feature.diary

import kotlin.math.*
import com.qingyu.personalaios.domain.InkPoint

/** Calligraphic ink: a fuller downstroke, lighter upstroke and pressure flex.
 * Geometry is expressed in nib widths so a handwriting glyph scales uniformly. */
internal object FountainNib {
    fun sample(points:List<InkPoint>,width:Float,height:Float,nibWidth:Float):List<InkPoint> {
        if(points.size<2) return points
        val spacing=(nibWidth*.45f).coerceIn(2f,8f)
        val result=ArrayList<InkPoint>(points.size*2);result.add(points.first())
        for(i in 1 until points.size) {
            val a=points[i-1];val b=points[i]
            val steps=ceil(hypot((b.x-a.x)*width,(b.y-a.y)*height)/spacing).toInt().coerceAtLeast(1)
            for(j in 1..steps) {
                val t=j.toFloat()/steps
                result.add(InkPoint(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.pressure+(b.pressure-a.pressure)*t))
            }
        }
        return result
    }
    fun radius(width:Float,pressure:Float,nx:Float,ny:Float,fromStart:Float,toEnd:Float):Float {
        val major=nx*.76604444f-ny*.64278764f
        val minor=nx*.64278764f+ny*.76604444f
        // A fixed chisel alone makes up/down strokes identical and oblique
        // hairlines disappear. Keep the footprint, with a readable minor axis.
        val contact=sqrt(major*major+.60f*.60f*minor*minor)
        // Normal (-dy, dx): positive screen-y is a downstroke. Smoothstep
        // avoids a width discontinuity as the pen turns through horizontal.
        val down=smooth((1f-nx)*.5f)
        val direction=.58f+.52f*down
        val flex=.68f+.32f*pressure.coerceIn(.15f,1.6f)
        return width*.5f*contact*direction*flex*taper(width,fromStart,toEnd)
    }
    fun taper(width:Float,fromStart:Float,toEnd:Float):Float {
        val start=.68f+.32f*smooth(fromStart/(width*1.15f).coerceAtLeast(.001f))
        val end=.28f+.72f*smooth(toEnd/(width*2.2f).coerceAtLeast(.001f))
        return min(start,end)
    }
    private fun smooth(value:Float):Float { val t=value.coerceIn(0f,1f);return t*t*(3f-2f*t) }
}
