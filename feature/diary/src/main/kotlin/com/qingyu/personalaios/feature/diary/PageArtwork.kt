package com.qingyu.personalaios.feature.diary

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** One composition for the canvas, book thumbnails and export. Coordinates never depend on orientation. */
internal object PageArtwork {
    const val Paper = 0xFFFFFFFF.toInt()
    fun sheetBitmap(page:DiaryPage,width:Int=1200,notes:Boolean=true):Bitmap {
        if(!page.singleFace) return bitmap(page,width,notes)
        val result=Bitmap.createBitmap(width,(width/(page.canvasAspect*2)).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(result);canvas.drawColor(Paper)
        for(face in PaperFace.entries) {
            val art=bitmap(DiaryFaces.page(page,face),width/2,notes)
            canvas.drawBitmap(art,null,RectF(if(face==PaperFace.LEFT) 0f else width/2f,0f,
                if(face==PaperFace.LEFT) width/2f else width.toFloat(),result.height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
            art.recycle()
        }
        return result
    }
    fun bitmap(page:DiaryPage,width:Int=600,notes:Boolean=false):Bitmap {
        val height=(width/page.canvasAspect).toInt().coerceIn(1,3000)
        val result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(result);canvas.drawColor(Paper)
        background(canvas,page.wallpaper,width.toFloat(),height.toFloat())
        guides(canvas,page.style,width.toFloat(),height.toFloat())
        if(notes) notes(canvas,page,width.toFloat(),height.toFloat())
        PaperLayers.base(page.stickers).forEach { sticker(canvas,it,width,height) }
        ink(canvas,page.strokes,width.toFloat(),height.toFloat())
        PaperLayers.top(page.stickers).forEach { sticker(canvas,it,width,height) }
        return result
    }
    fun background(canvas:Canvas,path:String,width:Float,height:Float) {
        if(path.isBlank()) return
        DiaryImages.load(path)?.let { image ->
            val cover=PhotoPageFill.cover(image.width.toFloat()/image.height,width/height)
            canvas.save();canvas.clipRect(0f,0f,width,height)
            canvas.drawBitmap(image,null,RectF(cover.left*width,cover.top*height,(cover.left+cover.width)*width,(cover.top+cover.height)*height),Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
    }
    private fun sticker(canvas:Canvas,s:DiarySticker,width:Int,height:Int) {
            val rect=RectF(s.x*width,s.y*height,(s.x+s.width)*width,(s.y+s.height)*height)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha=(s.alpha*255).toInt().coerceIn(0,255) }
            canvas.save();canvas.rotate(s.rotation,rect.centerX(),rect.centerY())
            when(s.kind) {
                StickerKind.TAPE -> tape(canvas,s,rect,paint)
                StickerKind.TEXT -> elementText(canvas,s,rect,width.toFloat(),paint)
                else -> DiaryImages.load(s.path)?.let { image ->
                    if(s.crop.size>2) canvas.clipPath(path(s.crop,rect.width(),rect.height(),rect.left,rect.top).apply { close() })
                    if(s.fillPage && s.kind==StickerKind.IMAGE) {
                        val cover=PhotoPageFill.cover(image.width.toFloat()/image.height,rect.width()/rect.height())
                        val target=RectF(rect.left+cover.left*rect.width(),rect.top+cover.top*rect.height(),
                            rect.left+(cover.left+cover.width)*rect.width(),rect.top+(cover.top+cover.height)*rect.height())
                        canvas.clipRect(rect)
                        canvas.drawBitmap(image,null,target,paint)
                    } else canvas.drawBitmap(image,null,rect,paint)
                }
            }
            canvas.restore()
    }
    fun noteBitmap(page:DiaryPage,width:Int=1200):Bitmap? {
        if(page.title.isBlank() && page.body.isBlank()) return null
        val bitmap=Bitmap.createBitmap(width,(width/page.canvasAspect).roundToInt().coerceIn(1,3000),Bitmap.Config.ARGB_8888)
        notes(Canvas(bitmap),page,width.toFloat(),bitmap.height.toFloat())
        return bitmap
    }
    private fun notes(canvas:Canvas,page:DiaryPage,width:Float,height:Float) {
        if(page.title.isBlank() && page.body.isBlank()) return
        if(page.singleFace && page.style==PaperStyle.LINED) {
            val document=RuledPageDocument.load(page,width,height)
            RuledFlow(document,width,height,page.stickers,page.stickers.associate { it.id to StickerFlowMask.load(it) }).draw(canvas)
            return
        }
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=0xff293844.toInt();textSize=width*.041f }
        val value:CharSequence=if(page.bodyHtml.isNotBlank()) android.text.SpannableStringBuilder().apply {
            if(page.title.isNotBlank()) append(page.title).append("\n\n")
            append(android.text.Html.fromHtml(page.bodyHtml,android.text.Html.FROM_HTML_MODE_COMPACT))
        } else page.title+if(page.title.isBlank()) page.body else "\n\n"+page.body
        // Flow through the two portrait faces, leaving the physical fold clear.
        // Free text/image objects remain independently positioned across the spread.
        val columns=if(page.canvasAspect>=1.1f) 2 else 1
        val pad=width*.045f;val gutter=width*.035f
        val column=if(columns==2) width/2-pad-gutter else width-2*pad
        val available=height*.84f
        val layout=StaticLayout.Builder.obtain(value,0,value.length,paint,column.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f,1.25f).build()
        val split=if(layout.height<=available) layout.height else layout.getLineTop(layout.getLineForVertical(available.toInt()))
        for(side in 0 until columns) {
            if(side==1 && split>=layout.height) break
            val offset=if(side==0) 0 else split
            canvas.save();canvas.translate(if(side==0) pad else width/2+gutter,height*.09f)
            canvas.clipRect(0f,0f,column,if(side==0) split.toFloat() else available)
            canvas.translate(0f,-offset.toFloat());layout.draw(canvas);canvas.restore()
        }
    }
    fun path(points:List<InkPoint>,w:Float,h:Float,x:Float=0f,y:Float=0f)=Path().apply {
        points.forEachIndexed { i,p -> if(i==0) moveTo(x+p.x*w,y+p.y*h) else lineTo(x+p.x*w,y+p.y*h) }
    }
    /** Washi body and its printed pattern; the pattern geometry is shared with the live collage. */
    fun tape(canvas:Canvas,s:DiarySticker,rect:RectF,base:Paint) {
        val corner=min(rect.width(),rect.height())*.16f
        canvas.drawRoundRect(rect,corner,corner,Paint(base).apply { style=Paint.Style.FILL;color=s.color.toInt() })
        canvas.drawRoundRect(RectF(rect.left,rect.top,rect.right,rect.top+rect.height()*.55f),corner,corner,
            Paint(base).apply { style=Paint.Style.FILL;color=0xFF000000.toInt();alpha=(base.alpha*.16f).toInt().coerceIn(0,255) })
        val mark=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.FILL;color=0xFF000000.toInt() }
        tapeOps(s.tape).forEach { op ->
            val x=rect.left+op.x*rect.width();val y=rect.top+op.y*rect.height()
            val w=op.width*rect.width();val h=op.height*rect.height()
            mark.alpha=(base.alpha*op.alpha).toInt().coerceIn(0,255)
            when(op.mark) {
                TapeMark.BAND -> canvas.drawRect(x,y,x+w,y+h,mark)
                TapeMark.DOT -> canvas.drawCircle(x+w/2,y+h/2,w/2,mark)
                TapeMark.LINE -> canvas.drawLine(x,y,x+w,y+h,mark)
            }
        }
    }
    /** Ink is untouched by text: the box is whatever the writer sized it to. */
    fun elementText(canvas:Canvas,s:DiarySticker,rect:RectF,width:Float,base:Paint) {
        if(s.text.isEmpty()) return
        val text=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=s.color.toInt();alpha=base.alpha;textSize=s.fontSize*width }
        val layout=StaticLayout.Builder.obtain(s.text,0,s.text.length,text,rect.width().toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f,1.2f).build()
        canvas.save();canvas.translate(rect.left,rect.top);canvas.clipRect(0f,0f,rect.width(),rect.height());layout.draw(canvas);canvas.restore()
    }
    fun guides(canvas:Canvas,style:PaperStyle,w:Float,h:Float,scroll:Int=0) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=if(style==PaperStyle.LINED) 0x806B4A32.toInt() else 0x18293844;strokeWidth=max(1f,w/600) }
        val gap=if(style==PaperStyle.LINED) PaperRules.gap(w) else w/15f
        when(style) {
            PaperStyle.PLAIN -> Unit
            PaperStyle.LINED -> {
                paint.pathEffect=DashPathEffect(floatArrayOf(max(2f,w*.005f),max(2f,w*.005f)),0f)
                val left=PaperRules.inset(w);val right=w-left
                if(gap<h) canvas.drawLine(left,gap,right,gap,paint)
                var y=gap*2-(scroll%gap)
                while(y<=PaperRules.lastRow(w,h)*gap) {
                    if(y>=PaperRules.bodyViewport(w)) canvas.drawLine(left,y,right,y,paint)
                    y+=gap
                }
            }
            PaperStyle.DOTTED -> { var y=gap;while(y<h) { var x=gap;while(x<w) { canvas.drawCircle(x,y,max(.8f,w/650),paint);x+=gap };y+=gap } }
            PaperStyle.GRID -> { var x=gap;while(x<w) { canvas.drawLine(x,0f,x,h,paint);x+=gap };var y=gap;while(y<h) { canvas.drawLine(0f,y,w,y,paint);y+=gap } }
            PaperStyle.MUSIC -> { var y=gap*2;while(y+gap<h) { repeat(5) { canvas.drawLine(w*.04f,y+it*gap*.22f,w*.96f,y+it*gap*.22f,paint) };y+=gap*2.8f } }
        }
    }
    @JvmOverloads fun ink(canvas:Canvas,strokes:List<InkStroke>,w:Float,h:Float,widthScale:Float=1f) {
        // CLEAR affects ink only, never text, stickers, ruled lines or paper.
        val layer=if(strokes.any { it.tool==InkTool.ERASER || it.tool==InkTool.GLYPH_ERASER }) canvas.saveLayer(0f,0f,w,h,null) else -1
        try { inkIsolated(canvas,strokes,w,h,widthScale) } finally { if(layer>=0) canvas.restoreToCount(layer) }
    }
    @JvmOverloads fun inkIsolated(canvas:Canvas,strokes:List<InkStroke>,w:Float,h:Float,widthScale:Float=1f) {
        strokes.filter { it.tool==InkTool.FILL }.forEach { stroke(canvas,it,w,h,widthScale) }
        strokes.filter { it.tool!=InkTool.FILL }.forEach { stroke(canvas,it,w,h,widthScale) }
    }
    @JvmOverloads fun stroke(canvas:Canvas,s:InkStroke,w:Float,h:Float,widthScale:Float=1f,sampleScale:Float=1f,grainScale:Float=sampleScale) {
        val checkpoint=canvas.save()
        try { canvas.clipRect(0f,0f,w,h);drawStroke(canvas,s,w,h,widthScale,sampleScale,grainScale) }
        finally { canvas.restoreToCount(checkpoint) }
    }
    private fun drawStroke(canvas:Canvas,s:InkStroke,w:Float,h:Float,widthScale:Float,sampleScale:Float,grainScale:Float) {
        if(s.tool==InkTool.GLYPH_ERASER) { GlyphInkEraser.draw(canvas,s,w,h);return }
        if(s.tool==InkTool.FOUNTAIN) { FountainRibbon.draw(canvas,s,w,h,widthScale);return }
        if(s.tool!=InkTool.FILL && s.tool!=InkTool.DIAGRAM) {
            val painter=BrushStrokeRaster(canvas,s.tool,s.color,s.width*widthScale,w,h,sampleScale,grainScale)
            s.points.forEach(painter::add)
            return
        }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=s.color.toInt();strokeWidth=s.width*w*widthScale;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
            style=if(s.tool==InkTool.FILL) Paint.Style.FILL else Paint.Style.STROKE
        }
        val geometry=path(s.points,w,h)
        if(s.tool==InkTool.FILL) geometry.close()
        if(s.points.size==1) { val p=s.points.first();paint.style=Paint.Style.FILL;canvas.drawCircle(p.x*w,p.y*h,paint.strokeWidth/2,paint) }
        else canvas.drawPath(geometry,paint)
    }

}

/** Recognition acts on the completed gesture, leaving the original stroke untouched when uncertain. */
internal fun diagram(points:List<InkPoint>,aspect:Float):List<InkPoint> {
    if(points.size<3) return points
    val first=points.first();val last=points.last()
    val distance=hypot(last.x-first.x,(last.y-first.y)/aspect)
    val minX=points.minOf { it.x };val maxX=points.maxOf { it.x };val minY=points.minOf { it.y };val maxY=points.maxOf { it.y }
    if(distance>.06f) return listOf(first,last)
    val w=maxX-minX;val h=maxY-minY
    if(w<.02f || h<.02f) return points
    val edge=points.count { minOf(abs(it.x-minX)/w,abs(it.x-maxX)/w,abs(it.y-minY)/h,abs(it.y-maxY)/h)<.13f }.toFloat()/points.size
    if(edge>.78f) return listOf(InkPoint(minX,minY),InkPoint(maxX,minY),InkPoint(maxX,maxY),InkPoint(minX,maxY),InkPoint(minX,minY))
    return List(65) { i -> val angle=2*PI*i/64;InkPoint((minX+maxX)/2+w/2*cos(angle).toFloat(),(minY+maxY)/2+h/2*sin(angle).toFloat()) }
}
