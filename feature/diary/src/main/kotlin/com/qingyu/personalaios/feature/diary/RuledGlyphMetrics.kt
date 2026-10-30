package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Current ink maps the complete unit-square pad into one em cell. Never fit an
 * individual stroke's bounds: a short mark must stay short, at its original x/y,
 * with the same nib scale as a large character. Bounds only crop the cached image. */
internal class RuledGlyphMetrics(val glyph:RuledGlyph,val em:Float,val paperWidth:Float) {
    private val original=RuledInkMetrics.of(glyph.strokes)
    private val ink=glyph.strokes.filter { it.tool!=InkTool.ERASER && it.tool!=InkTool.GLYPH_ERASER }
    private fun factor(s:InkStroke):Float {
        val p=s.points.maxOf { it.pressure }.coerceIn(.15f,1.8f)
        return when(s.tool) {
            InkTool.FOUNTAIN->.55f*(.68f+.32f*p);InkTool.INK->(.3f+.72f*p)/2
            InkTool.WATERCOLOR,InkTool.BLEND->1.6f*(.7f+.32f*p)
            InkTool.MARKER->1.1f;InkTool.PENCIL->.4f;InkTool.CRAYON->.7f;else->.5f
        }
    }
    val bounds=if(!glyph.paperWidthInk) original else sourceBounds()
    val side=if(glyph.paperWidthInk) em else em/bounds.extent
    val inkReferenceWidth=side*glyph.sourceWidthScale
    val width=(bounds.right-bounds.left)*side
    val height=(bounds.bottom-bounds.top)*side
    // Legacy vectors retain their original layout. Current ink has a fixed
    // source origin; drawing and both eraser paths use exactly that transform.
    private val padCentreY=((bounds.top+bounds.bottom)/2).coerceIn(0f,1f)
    private var leadingTrim=0f
    fun compactLeading():RuledGlyphMetrics=apply { if(glyph.paperWidthInk) leadingTrim=bounds.left*side }
    val bitmapLeftOffset get()=if(glyph.paperWidthInk) bounds.left*side-leadingTrim else 0f
    val topFromBaseline=if(glyph.paperWidthInk) em-bounds.top*side else (em+height)/2-(padCentreY-.5f)*em
    val baselineOffset=bounds.top*side+topFromBaseline
    fun sourceLeft(x:Float)=x+bitmapLeftOffset-bounds.left*side
    fun advance(em:Float)=if(glyph.paperWidthInk) ceil(bounds.right*side-leadingTrim+em*.045f).coerceAtLeast(1f) else original.advance(em)
    private fun sourceBounds():RuledInkMetrics {
        var l=Float.POSITIVE_INFINITY;var t=l;var r=Float.NEGATIVE_INFINITY;var b=r
        for(s in ink) {
            val radius=s.width*glyph.sourceWidthScale*factor(s)+.002f
            for(point in s.points) { l=min(l,point.x-radius);r=max(r,point.x+radius);t=min(t,point.y-radius);b=max(b,point.y+radius) }
        }
        return if(l.isFinite()) RuledInkMetrics(l.coerceIn(0f,1f),t.coerceIn(0f,1f),r.coerceIn(0f,1f),b.coerceIn(0f,1f)) else original
    }
}
