package com.qingyu.personalaios.feature.diary

import android.graphics.Bitmap
import android.graphics.Canvas
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** One layout per gesture, one mutable bitmap per touched glyph. Only new input
 * segments are visited; document layout/storage changes once on pointer up. */
internal class GlyphEraseSession(val flow:RuledFlow,private val previews:MutableMap<Int,Bitmap>) {
    private data class Placement(val glyph:RuledGlyph,val left:Float,val top:Float,val side:Float,val bounds:RuledInkMetrics,val metrics:RuledGlyphMetrics)
    private val placements=flow.document.glyphs.mapNotNull { glyph ->
        val run=flow.runs.firstOrNull { glyph.offset in it.start until it.end } ?: return@mapNotNull null
        val metrics=flow.metricsFor(glyph,run.start);val bounds=metrics.bounds;val side=metrics.side
        val x=run.x+run.layout.getPrimaryHorizontal(glyph.offset-run.start)
        Placement(glyph,metrics.sourceLeft(x),run.baseline-metrics.baselineOffset,side,bounds,metrics)
    }
    private val counts=mutableListOf<Int>()
    var pointVisits=0;private set
    var glyphCopies=0;private set
    fun preview(paths:List<List<InkPoint>>,radius:Float):Boolean {
        var changed=false
        paths.forEachIndexed { index,path ->
            while(counts.size<=index) counts+=0
            val seen=counts[index];if(path.size<=seen) return@forEachIndexed
            val fresh=path.subList((seen-1).coerceAtLeast(0),path.size);counts[index]=path.size;pointVisits+=fresh.size
            val l=fresh.minOf { it.x }*flow.width;val r=fresh.maxOf { it.x }*flow.width
            val t=fresh.minOf { it.y }*flow.height;val b=fresh.maxOf { it.y }*flow.height
            val physicalRadius=radius*flow.width
            for(p in placements) {
                if(r+physicalRadius<p.left+p.bounds.left*p.side || l-physicalRadius>p.left+p.bounds.right*p.side ||
                    b+physicalRadius<p.top+p.bounds.top*p.side || t-physicalRadius>p.top+p.bounds.bottom*p.side) continue
                val image=previews.getOrPut(p.glyph.offset) {
                    glyphCopies++;checkNotNull((if(p.glyph.paperWidthInk) PhysicalGlyphRaster.render(p.glyph,p.metrics) else OriginalInkRaster.render(p.glyph.strokes,ceil(p.side).toInt())).copy(Bitmap.Config.ARGB_8888,true))
                }
                val masks=GlyphInkEraser.encode(listOf(fresh.map { (it.x*flow.width-p.left)/p.side to (it.y*flow.height-p.top)/p.side }),physicalRadius/p.side,p.glyph.paperWidthInk)
                val canvas=Canvas(image)
                if(p.glyph.paperWidthInk) {
                    canvas.save();canvas.translate(-p.bounds.left*p.side,-p.bounds.top*p.side)
                    for(mask in masks) GlyphInkEraser.draw(canvas,mask,p.side,p.side,true)
                    canvas.restore()
                } else for(mask in masks) GlyphInkEraser.draw(canvas,mask,image.width.toFloat(),image.height.toFloat())
                changed=true
            }
        }
        return changed
    }
    fun finish(paths:List<List<InkPoint>>,radius:Float)=flow.eraseGlyphInk(paths,radius)
    fun clear() { previews.clear() }
}
