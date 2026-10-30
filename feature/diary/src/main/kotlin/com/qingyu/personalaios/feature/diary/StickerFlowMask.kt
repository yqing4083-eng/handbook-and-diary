package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*

/** Asset-local opaque runs, cached independently of position/rotation. Loading
 * happens off the UI thread; dragging only projects these small rectangles. */
internal object StickerFlowMask {
    data class Key(val path:String,val crop:List<InkPoint>,val fill:Boolean,val kind:StickerKind)
    fun key(s:DiarySticker)=Key(s.path,s.crop,s.fillPage,s.kind)
    private val cache=android.util.LruCache<Key,List<RuledFlowGeometry.Cell>>(128)
    fun peek(s:DiarySticker)=cache.get(key(s))
    fun load(s:DiarySticker):List<RuledFlowGeometry.Cell> {
        val key=key(s);cache.get(key)?.let { return it }
        if(s.kind==StickerKind.TEXT || s.kind==StickerKind.TAPE) return listOf(RuledFlowGeometry.Cell(0f,0f,1f,1f))
        val bitmap=DiaryImages.load(s.path) ?: return listOf(RuledFlowGeometry.Cell(0f,0f,1f,1f))
        val n=64
        val mask=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888)
        Canvas(mask).apply {
            scale(n.toFloat(),n.toFloat())
            drawPath(StickerContour.create(bitmap,s.crop,s.fillPage),Paint().apply { color=Color.BLACK })
        }
        val pixels=IntArray(n*n);mask.getPixels(pixels,0,n,0,0,n,n);mask.recycle()
        val cells=buildList {
            for(y in 0 until n) {
                var x=0
                while(x<n) {
                    while(x<n && (pixels[y*n+x] ushr 24)<48) x++
                    val start=x
                    while(x<n && (pixels[y*n+x] ushr 24)>=48) x++
                    if(x>start) add(RuledFlowGeometry.Cell(start.toFloat()/n,y.toFloat()/n,x.toFloat()/n,(y+1f)/n))
                }
            }
        }
        cache.put(key,cells);return cells
    }
}
