package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Trim the outer pad whitespace, keeping every point and the drawing's aspect.
 * A minimum extent keeps a dot from becoming a full-size character. */
internal data class RuledInkMetrics(val left:Float,val top:Float,val right:Float,val bottom:Float) {
    val extent=max(.32f,max(right-left,bottom-top))
    fun advance(em:Float)=ceil(em*((right-left)/extent+.045f))
    companion object {
        fun of(strokes:List<InkStroke>):RuledInkMetrics {
            var l=1f;var t=1f;var r=0f;var b=0f
            for(s in strokes) if(s.tool!=InkTool.ERASER && s.tool!=InkTool.GLYPH_ERASER) {
                val radius=.025f+s.width*when(s.tool) { InkTool.WATERCOLOR,InkTool.BLEND->2.1f;InkTool.MARKER->1.2f;else->1f }
                for(p in s.points) { l=min(l,p.x-radius);r=max(r,p.x+radius);t=min(t,p.y-radius);b=max(b,p.y+radius) }
            }
            if(l>r) return RuledInkMetrics(0f,0f,.32f,.32f)
            return RuledInkMetrics(l.coerceIn(0f,1f),t.coerceIn(0f,1f),r.coerceIn(0f,1f),b.coerceIn(0f,1f))
        }
    }
}
