package com.qingyu.personalaios.feature.diary

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

/** One atlas decode, silhouettes cached once. No per-frame paths or bitmap work. */
@Composable internal fun ReferenceToolSprite(bitmap:Bitmap,tip:Bitmap?,band:Bitmap?,color:Long,modifier:Modifier,vertical:Boolean) {
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val tipPaint=remember(color) { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        val r=((color ushr 16) and 255)/255f;val g=((color ushr 8) and 255)/255f;val b=(color and 255)/255f
        colorFilter=android.graphics.ColorMatrixColorFilter(floatArrayOf(r,0f,0f,0f,0f,0f,g,0f,0f,0f,0f,0f,b,0f,0f,0f,0f,0f,1f,0f))
    } }
    Canvas(modifier) {
        val length=80.dp.toPx();val width=length*bitmap.width/bitmap.height
        drawIntoCanvas {
            val c=it.nativeCanvas;c.save()
            if(vertical) c.translate(size.width/2,4.dp.toPx())
            else { c.translate(4.dp.toPx(),size.height/2);c.rotate(-90f) }
            // Extend the barrel cross section, never stretch the nib.
            c.drawBitmap(bitmap,Rect(0,bitmap.height-6,bitmap.width,bitmap.height-4),RectF(-width/2,length-1,width/2,length+160.dp.toPx()),paint)
            c.drawBitmap(bitmap,null,RectF(-width/2,0f,width/2,length),paint)
            if(tip!=null) c.drawBitmap(tip,null,RectF(-width/2,0f,width/2,length),tipPaint)
            if(band!=null) c.drawBitmap(band,null,RectF(-width/2,0f,width/2,length),tipPaint)
            c.restore()
        }
    }
}

internal object ToolSpriteAtlas {
    private var cached:List<Bitmap>?=null
    private var cachedTips:List<Bitmap?>?=null
    private var cachedBands:List<Bitmap?>?=null
    @Synchronized fun bands(sprites:List<Bitmap>):List<Bitmap?> {
        cachedBands?.let { return it }
        return sprites.mapIndexed { index,source ->
            if(index in 3..5) null else {
                val pixels=IntArray(source.width*source.height);source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
                fun pigment(p:Int):Boolean {
                    val r=(p ushr 16) and 255;val g=(p ushr 8) and 255;val b=p and 255
                    return (p ushr 24)>100 && if(index==2) r-b>40 && g-b>25 else maxOf(r,g,b)<145
                }
                for(y in 0 until source.height) {
                    val center=(source.width*.25f).toInt() until (source.width*.75f).toInt()
                    val stripe=y>source.height*.34f && y<source.height*.62f && center.count { pigment(pixels[y*source.width+it]) }>center.count()*.6f
                    for(x in 0 until source.width) {
                        val i=y*source.width+x;val p=pixels[i]
                        if(!stripe || !pigment(p)) pixels[i]=0 else {
                            val shade=(224+31*(((p ushr 16) and 255)*.2126f+((p ushr 8) and 255)*.7152f+(p and 255)*.0722f)/255).toInt()
                            pixels[i]=(p and 0xFF000000.toInt()) or (shade shl 16) or (shade shl 8) or shade
                        }
                    }
                }
                Bitmap.createBitmap(pixels,source.width,source.height,Bitmap.Config.ARGB_8888)
            }
        }.also { cachedBands=it }
    }
    @Synchronized fun tips(sprites:List<Bitmap>):List<Bitmap?> {
        cachedTips?.let { return it }
        return sprites.mapIndexed { index,source ->
            if(index in 3..5) null else {
                val pixels=IntArray(source.width*source.height);source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
                val fraction=when(index) { 0->.125f;1->.085f;2->.20f;6->.16f;7->.055f;else->.33f }
                for(i in pixels.indices) {
                    val p=pixels[i];val r=(p ushr 16) and 255;val g=(p ushr 8) and 255;val b=p and 255
                    val pigment=i/source.width<source.height*fraction && (if(index==2) r-b>40 && g-b>25 else if(index==7) maxOf(r,g,b)<225 else maxOf(r,g,b)<145)
                    if(!pigment) pixels[i]=0 else {
                        val luminance=(r*.2126f+g*.7152f+b*.0722f)/255f
                        val shade=((.76f+.24f*luminance)*255).toInt()
                        pixels[i]=(p and 0xFF000000.toInt()) or (shade shl 16) or (shade shl 8) or shade
                    }
                }
                Bitmap.createBitmap(pixels,source.width,source.height,Bitmap.Config.ARGB_8888)
            }
        }.also { cachedTips=it }
    }
    @Synchronized fun extract(atlas:Bitmap):List<Bitmap> {
        cached?.let { return it }
        val edges=floatArrayOf(0f,.125f,.23f,.337f,.445f,.552f,.664f,.767f,.869f,1f)
        return (0..8).map { cell ->
            val left=(atlas.width*edges[cell]).toInt();val w=(atlas.width*edges[cell+1]).toInt()-left;val h=atlas.height
            val pixels=IntArray(w*h);atlas.getPixels(pixels,0,w,left,0,w,h)
            val visited=BooleanArray(pixels.size);val queue=IntArray(pixels.size);var largest=IntArray(0)
            for(start in pixels.indices) if(!visited[start] && (pixels[start] ushr 24)>127) {
                var head=0;var tail=1;queue[0]=start;visited[start]=true
                fun visit(next:Int) {
                    if(!visited[next] && (pixels[next] ushr 24)>127) { visited[next]=true;queue[tail++]=next }
                }
                while(head<tail) {
                    val p=queue[head++];val x=p%w;val y=p/w
                    if(x>0) visit(p-1);if(x+1<w) visit(p+1);if(y>0) visit(p-w);if(y+1<h) visit(p+w)
                }
                if(tail>largest.size) largest=queue.copyOf(tail)
            }
            check(largest.isNotEmpty())
            val keep=BooleanArray(pixels.size)
            for(p in largest) { keep[p]=true;val x=p%w;val y=p/w
                for(dy in -1..1) for(dx in -1..1) if(x+dx in 0 until w && y+dy in 0 until h) keep[(y+dy)*w+x+dx]=true }
            var l=w;var t=h;var r=0;var b=0
            for(p in pixels.indices) if(keep[p] && (pixels[p] ushr 24)>0) { l=minOf(l,p%w);r=maxOf(r,p%w);t=minOf(t,p/w);b=maxOf(b,p/w) } else pixels[p]=0
            Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888).let { source ->
                Bitmap.createBitmap(source,l,t,r-l+1,b-t+1).also { if(it!==source) source.recycle() }
            }
        }.also { cached=it }
    }
}
