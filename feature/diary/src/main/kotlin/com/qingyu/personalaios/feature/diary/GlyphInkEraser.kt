package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Eraser centres may lie outside a small glyph while touching its edge.
 * Legacy masks encode [-2,3], radius width*50. Physical-width glyphs encode
 * [-32,33] and a bounded nonlinear radius, retaining broad brush erasure.
 * This keeps backups, undo and original vector strokes intact, with no bitmap blob. */
internal object GlyphInkEraser {
    fun radius(width:Float,physical:Boolean)=if(physical) (width-.001f)/(.05f-width).coerceAtLeast(.000001f) else width*50f
    fun encode(paths:List<List<Pair<Float,Float>>>,radius:Float,physical:Boolean=false)=paths.flatMap { path ->
        val trace=BoundedInkTrace()
        val shift=if(physical) 32f else 2f;val scale=if(physical) 65f else 5f
        path.forEach { (x,y)->trace.add(InkSample((x+shift)/scale,(y+shift)/scale,1f)) }
        trace.paths.filter { it.isNotEmpty() }.map { InkStroke(tool=InkTool.GLYPH_ERASER,color=0L,
            width=if(physical) (.001f+.049f*radius/(1+radius)).coerceIn(.001f,.049999f) else (radius/50f).coerceIn(.001f,.05f),points=it.toList()) }
    }
    fun draw(canvas:Canvas,stroke:InkStroke,width:Float,height:Float,physical:Boolean=false) {
        val radius=radius(stroke.width,physical)*width
        val shift=if(physical) 32f else 2f;val scale=if(physical) 65f else 5f
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode=BlendMode.CLEAR;style=Paint.Style.STROKE
            strokeWidth=radius*2;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        val first=stroke.points.first();val path=Path().apply {
            moveTo((first.x*scale-shift)*width,(first.y*scale-shift)*height)
            for(p in stroke.points.drop(1)) lineTo((p.x*scale-shift)*width,(p.y*scale-shift)*height)
        }
        if(stroke.points.size==1) { paint.style=Paint.Style.FILL;canvas.drawCircle((first.x*scale-shift)*width,(first.y*scale-shift)*height,radius,paint) }
        else canvas.drawPath(path,paint)
    }
}

/** Transform the physical paper eraser into each original glyph's coordinates.
 * Geometry/advance comes from original ink, never from the eraser mask. */
internal fun RuledFlow.eraseGlyphInk(paths:List<List<InkPoint>>,radius:Float):RuledDocument {
    if(paths.isEmpty() || document.glyphs.isEmpty()) return document
    val glyphs=document.glyphs.map { glyph ->
        val run=runs.firstOrNull { glyph.offset in it.start until it.end } ?: return@map glyph
        val metrics=metricsFor(glyph,run.start)
        val bounds=metrics.bounds;val side=metrics.side
        val x=run.x+run.layout.getPrimaryHorizontal(glyph.offset-run.start)
        val left=metrics.sourceLeft(x);val top=run.baseline-metrics.baselineOffset
        val r=radius*width/side
        val local=paths.map { path -> path.map { p->(p.x*width-left)/side to (p.y*height-top)/side } }
        val l=bounds.left-r;val t=bounds.top-r;val right=bounds.right+r;val bottom=bounds.bottom+r
        // Include segment bounds, so fast swipes crossing a glyph are not missed.
        val touches=local.any { path -> path.any { it.first in l..right && it.second in t..bottom } ||
            path.zipWithNext().any { (a,b)-> min(a.first,b.first)<=right && max(a.first,b.first)>=l && min(a.second,b.second)<=bottom && max(a.second,b.second)>=t } }
        if(!touches) glyph else glyph.copy(strokes=glyph.strokes+GlyphInkEraser.encode(local,r,glyph.paperWidthInk))
    }
    return if(glyphs==document.glyphs) document else document.copy(glyphs=glyphs)
}
