package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Uniformly reduce the original pad ink, preserving pressure, nib and colour.
 * Brush width follows the same scale as the path, rather than staying paper-sized. */
internal object PhysicalGlyphRaster {
    private const val Samples=4
    private data class Key(val glyph:RuledGlyph,val em:Float,val paperWidth:Float)
    private val cache=object:android.util.LruCache<Key,Bitmap>(6*1024*1024) { override fun sizeOf(key:Key,value:Bitmap)=value.allocationByteCount }
    fun render(glyph:RuledGlyph,m:RuledGlyphMetrics):Bitmap {
        val key=Key(glyph,m.em,m.paperWidth);cache.get(key)?.let { return it }
        val w=ceil(m.width).toInt().coerceAtLeast(1);val h=ceil(m.height).toInt().coerceAtLeast(1)
        val source=Bitmap.createBitmap(w*Samples,h*Samples,Bitmap.Config.ARGB_8888);val canvas=Canvas(source)
        // Paint in the original pad's pixel space, then uniformly reduce the
        // entire canvas. Nib width, pigment spacing, grain and fountain pressure
        // filtering consequently use the same inputs as the live pad painter.
        val sourceSide=m.paperWidth/glyph.sourceWidthScale
        canvas.save()
        canvas.translate(-m.bounds.left*m.side*Samples,-m.bounds.top*m.side*Samples)
        canvas.scale(m.side*Samples/sourceSide,m.side*Samples/sourceSide)
        canvas.clipRect(0f,0f,sourceSide,sourceSide)
        for(s in glyph.strokes) {
            if(s.tool==InkTool.GLYPH_ERASER) GlyphInkEraser.draw(canvas,s,sourceSide,sourceSide,true)
            else PageArtwork.stroke(canvas,s,sourceSide,sourceSide,glyph.sourceWidthScale)
        }
        canvas.restore()
        val values=IntArray(w*h*Samples*Samples);source.getPixels(values,0,w*Samples,0,0,w*Samples,h*Samples)
        val output=IntArray(w*h)
        for(y in 0 until h) for(x in 0 until w) {
            var a=0;var peak=0;var r=0;var g=0;var b=0
            for(dy in 0 until Samples) for(dx in 0 until Samples) {
                val p=values[(y*Samples+dy)*w*Samples+x*Samples+dx];val alpha=p ushr 24
                a+=alpha;peak=max(peak,alpha);r+=((p ushr 16) and 255)*alpha;g+=((p ushr 8) and 255)*alpha;b+=(p and 255)*alpha
            }
            // Restore fine-stroke coverage using the same optical reconstruction
            // as legacy handwriting. No dilation, geometry or nib-size change.
            if(a>0) output[y*w+x]=Color.argb(InkCoverage.alpha(a,peak,16),(r+a/2)/a,(g+a/2)/a,(b+a/2)/a)
        }
        source.recycle()
        return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888).also { cache.put(key,it) }
    }
}
