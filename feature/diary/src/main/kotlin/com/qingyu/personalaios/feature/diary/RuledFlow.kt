package com.qingyu.personalaios.feature.diary

import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** UTF-16 offsets always refer to real IME text. Layout never inserts padding
 * spaces, object placeholders or paragraph breaks around a sticker. */
internal class RuledFlow(val document:RuledDocument,val width:Float,val height:Float,stickers:List<DiarySticker>,contours:Map<String,List<RuledFlowGeometry.Cell>> = stickers.mapNotNull { s -> StickerFlowMask.peek(s)?.let { s.id to it } }.toMap()) {
    var erasurePreviews:Map<Int,android.graphics.Bitmap> = emptyMap()
    data class Run(val start:Int,val end:Int,val row:Int,val x:Float,val baseline:Float,val layout:StaticLayout)
    data class Caret(val x:Float,val baseline:Float,val row:Int)
    val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF293844.toInt();textSize=PaperRules.font(width);isElegantTextHeight=false }
    val runs=mutableListOf<Run>()
    private val empty=mutableMapOf<Int,Pair<Int,Float>>()
    val gap=PaperRules.gap(width)
    val text get()=document.text
    private val glyphs=document.glyphs.associateBy { it.offset }
    private val metrics=document.glyphs.associate { it.offset to RuledGlyphMetrics(it,paint.textSize,width) }
    private val compactMetrics=mutableMapOf<Int,RuledGlyphMetrics>()
    fun metricsFor(glyph:RuledGlyph,runStart:Int):RuledGlyphMetrics =
        if(glyph.offset>runStart && glyphs.containsKey(glyph.offset-1)) compactMetrics.getOrPut(glyph.offset) {
            RuledGlyphMetrics(glyph,paint.textSize,width).compactLeading()
        } else checkNotNull(metrics[glyph.offset])
    private fun fit(start:Int,end:Int,available:Float):Int {
        if(glyphs.isEmpty()) return start+paint.breakText(text,start,end,true,available,null)
        var position=start;var used=0f
        while(position<end) {
            val next=position+Character.charCount(Character.codePointAt(text,position))
            val advance=glyphs[position]?.let {metricsFor(it,start).advance(paint.textSize)} ?: paint.measureText(text,position,next)
            if(used+advance>available) break
            used+=advance;position=next
        }
        return position
    }
    init {
        val occupied=stickers.map { s ->
            if(s.kind!=StickerKind.TEXT || s.text.isEmpty()) s else {
                val textPaint=TextPaint(paint).apply { textSize=s.fontSize*width }
                val layout=StaticLayout.Builder.obtain(s.text,0,s.text.length,textPaint,ceil(width*s.width).toInt().coerceAtLeast(1)).setIncludePad(false).build()
                val right=(0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: 0f
                StickerPlacement.fitText(s,ceil(right+2f)/width,layout.height/height,width/height)
            }
        }
        val characters=android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).apply { setText(this@RuledFlow.text) }
        var offset=0;var row=1;var first=true
        while(offset<=text.length && row<=10000) {
            val baseline=PaperRules.baseline(width,row)
            val left=if(first) document.anchorAt(offset).x*width else width*.065f
            val slots=RuledFlowGeometry.slots(width,height,baseline+paint.fontMetrics.ascent,baseline+paint.fontMetrics.descent,occupied,left,contours)
            empty[row]=offset to (slots.firstOrNull()?.left ?: width*.065f)
            if(offset==text.length) break
            if(text[offset]=='\n') { offset++;row++;first=true;continue }
            val paragraphEnd=text.indexOf('\n',offset).takeIf { it>=0 } ?: text.length
            var newline=false
            for(slot in slots) {
                if(offset>=paragraphEnd) break
                var end=fit(offset,paragraphEnd,slot.width)
                if(end<paragraphEnd && !characters.isBoundary(end)) end=characters.preceding(end)
                if(end<=offset) continue
                // Preserve ordinary word wrapping when a word fits on the next row.
                if(end<paragraphEnd && text[end-1].isLetter() && text[end].isLetter() && text[end].code<128) {
                    val space=text.lastIndexOf(' ',end-1)
                    if(space>=offset) end=space+1
                }
                val fragment=SpannableString(text.substring(offset,end))
                for(g in document.glyphs) if(g.offset in offset until end)
                    fragment.setSpan(OriginalInkSpan(g,metricsFor(g,offset)) { erasurePreviews[g.offset] },g.offset-offset,g.offset-offset+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val layout=StaticLayout.Builder.obtain(fragment,0,fragment.length,paint,ceil(slot.width+1).toInt().coerceAtLeast(1))
                    .setIncludePad(false).setUseLineSpacingFromFallbacks(false)
                    .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE).build()
                runs+=Run(offset,end,row,slot.left,baseline,layout)
                offset=end;first=false
                if(offset==paragraphEnd) {
                    if(offset<text.length) { offset++;newline=true }
                    break
                }
            }
            if(offset==text.length) break
            row++;if(newline) first=true
        }
    }
    fun caret(offset:Int):Caret {
        val at=offset.coerceIn(0,text.length)
        // A wrap boundary belongs to the next run; a paragraph newline belongs to the old one.
        val next=runs.firstOrNull { it.start==at }
        val run=next ?: runs.lastOrNull { at>it.start && at<=it.end }
        if(run!=null) return Caret(run.x+run.layout.getPrimaryHorizontal(at-run.start),run.baseline,run.row)
        val entry=empty.entries.lastOrNull { it.value.first==at } ?: empty.entries.lastOrNull()
        return Caret(entry?.value?.second ?: width*.065f,PaperRules.baseline(width,entry?.key ?: 1),entry?.key ?: 1)
    }
    fun rowAtOffset(offset:Int)=caret(offset).row
    fun hit(x:Float,y:Float):Int {
        val row=PaperRules.row(width,height,y+gap*.28f)
        val choices=runs.filter { it.row==row }
        val run=choices.minByOrNull { when { x<it.x -> it.x-x;x>it.x+it.layout.getLineRight(0)->x-it.x-it.layout.getLineRight(0);else->0f } }
            ?: return empty[row]?.first ?: text.length
        return (run.start+run.layout.getOffsetForHorizontal(0,x-run.x)).coerceIn(run.start,run.end)
    }
    fun emptyRow(row:Int)=runs.none { it.row==row }
    fun draw(canvas:Canvas,selectionStart:Int=-1,selectionEnd:Int=-1,composingStart:Int=-1,composingEnd:Int=-1) {
        val save=canvas.save();canvas.clipRect(0f,0f,width,height)
        try { for(run in runs) {
            if(run.baseline+paint.fontMetrics.ascent>height) break
            val start=max(selectionStart,run.start);val end=min(selectionEnd,run.end)
            if(end>start) {
                val l=run.x+run.layout.getPrimaryHorizontal(start-run.start);val r=run.x+run.layout.getPrimaryHorizontal(end-run.start)
                canvas.drawRect(min(l,r),run.baseline+paint.fontMetrics.ascent,max(l,r),run.baseline+paint.fontMetrics.descent,
                    Paint().apply { color=0x403399FF })
            }
            canvas.save();canvas.translate(run.x,run.baseline-run.layout.getLineBaseline(0));run.layout.draw(canvas);canvas.restore()
            if(composingEnd>composingStart && composingStart<run.end && composingEnd>run.start) {
                val l=run.x+run.layout.getPrimaryHorizontal(max(composingStart,run.start)-run.start)
                val r=run.x+run.layout.getPrimaryHorizontal(min(composingEnd,run.end)-run.start)
                canvas.drawLine(l,run.baseline+2,r,run.baseline+2,Paint(paint).apply { strokeWidth=max(1f,width/400) })
            }
        } } finally { canvas.restoreToCount(save) }
    }
}

internal object RuledPageDocument {
    fun load(page:DiaryPage,width:Float,height:Float):RuledDocument {
        val body=RuledText.document(page.bodyHtml) ?: RuledDocument.from(RuledText.initial(page)) { value,x ->
            val doc=RuledDocument(value,listOf(RuledAnchor(0,x,"measure")))
            RuledFlow(doc,width,height,emptyList()).runs.lastOrNull()?.row ?: 1
        }
        val prefix=page.title+"\n"
        val titleInk=RuledText.ink(page.bodyHtml,"title").filter { it.offset in page.title.indices && page.title[it.offset]=='\uFFFC' }
        return RuledDocument(prefix+body.text,listOf(RuledAnchor(0,RuledText.titleX(page.bodyHtml),"title"))+body.anchors.map { it.copy(start=it.start+prefix.length) },
            titleInk+body.glyphs.map { it.copy(offset=it.offset+prefix.length) })
    }
    fun body(doc:RuledDocument):RuledDocument {
        val start=(doc.text.indexOf('\n')+1).takeIf { it>0 } ?: doc.text.length
        val text=doc.text.substring(start)
        val anchors=doc.anchors.filter { it.start>=start }.map { it.copy(start=it.start-start) }.toMutableList()
        if(anchors.none { it.start==0 }) anchors.add(0,RuledAnchor(0,.065f,"body"))
        return RuledDocument(text,anchors,doc.glyphs.filter { it.offset>=start }.map { it.copy(offset=it.offset-start) })
    }
}

/** Bitmap trimming never changes the original pad-to-cell transform. */
private class OriginalInkSpan(private val glyph:RuledGlyph,private val metrics:RuledGlyphMetrics,private val preview:()->android.graphics.Bitmap?):ReplacementSpan() {
    private var cached:android.graphics.Bitmap?=null
    private val bounds=metrics.bounds
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun getSize(paint:Paint,text:CharSequence,start:Int,end:Int,fm:Paint.FontMetricsInt?):Int =
        metrics.advance(paint.textSize).toInt()
    override fun draw(canvas:Canvas,text:CharSequence,start:Int,end:Int,x:Float,top:Int,y:Int,bottom:Int,paint:Paint) {
        if(glyph.paperWidthInk) {
            val image=preview() ?: (cached ?: PhysicalGlyphRaster.render(glyph,metrics).also { cached=it })
            canvas.drawBitmap(image,x+metrics.bitmapLeftOffset,y-metrics.topFromBaseline,imagePaint);return
        }
        val side=metrics.side;val pixels=ceil(side).toInt().coerceAtLeast(1)
        val image=preview() ?: (cached?.takeIf { it.width==pixels } ?: OriginalInkRaster.render(glyph.strokes,pixels).also { cached=it })
        val checkpoint=canvas.save()
        val origin=y-metrics.baselineOffset
        canvas.clipRect(x,origin+bounds.top*side,x+(bounds.right-bounds.left)*side,origin+bounds.bottom*side)
        canvas.drawBitmap(image,null,android.graphics.RectF(x-bounds.left*side,origin,
            x+(1f-bounds.left)*side,origin+side),imagePaint)
        canvas.restoreToCount(checkpoint)
    }
}

/** Tiny glyphs must be filtered from proper pigment deposits. Painting dabs
 * directly below one pixel loses pigment. One scratch buffer per render thread
 * supplies supersampling; each span retains only its small final bitmap. */
internal object OriginalInkRaster {
    private const val Samples=4
    private data class Key(val strokes:List<InkStroke>,val pixels:Int)
    private val cache=object:android.util.LruCache<Key,android.graphics.Bitmap>(2*1024*1024) {
        override fun sizeOf(key:Key,value:android.graphics.Bitmap)=value.allocationByteCount
    }
    private class Work {
        var bitmap:android.graphics.Bitmap?=null
        var values=IntArray(0)
        fun prepare(side:Int):android.graphics.Bitmap {
            if(bitmap?.width!=side) {
                bitmap?.recycle()
                bitmap=android.graphics.Bitmap.createBitmap(side,side,android.graphics.Bitmap.Config.ARGB_8888)
                values=IntArray(side*side)
            }
            return checkNotNull(bitmap).also { it.eraseColor(android.graphics.Color.TRANSPARENT) }
        }
    }
    private val scratch=object:ThreadLocal<Work>() { override fun initialValue()=Work() }
    fun render(strokes:List<InkStroke>,pixels:Int):android.graphics.Bitmap {
        val key=Key(strokes,pixels);cache.get(key)?.let { return it }
        val work=checkNotNull(scratch.get());val side=pixels*Samples;val source=work.prepare(side)
        PageArtwork.ink(Canvas(source),strokes,side.toFloat(),side.toFloat())
        source.getPixels(work.values,0,side,0,0,side,side)
        val output=IntArray(pixels*pixels)
        // Bilinear downsampling can miss a thin stroke between sample centres.
        // Average alpha coverage and premultiplied colour across the whole cell.
        for(y in 0 until pixels) for(x in 0 until pixels) {
            var alpha=0;var peak=0;var red=0;var green=0;var blue=0
            for(dy in 0 until Samples) for(dx in 0 until Samples) {
                val p=work.values[(y*Samples+dy)*side+x*Samples+dx]
                val a=p ushr 24;alpha+=a;peak=max(peak,a)
                red+=((p ushr 16) and 255)*a;green+=((p ushr 8) and 255)*a;blue+=(p and 255)*a
            }
            if(alpha>0) output[y*pixels+x]=android.graphics.Color.argb(InkCoverage.alpha(alpha,peak,16),
                (red+alpha/2)/alpha,(green+alpha/2)/alpha,(blue+alpha/2)/alpha)
        }
        return android.graphics.Bitmap.createBitmap(output,pixels,pixels,android.graphics.Bitmap.Config.ARGB_8888).also { cache.put(key,it) }
    }
}
