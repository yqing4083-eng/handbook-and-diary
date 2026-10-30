package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.InkPoint

/** Build once off the UI thread. Transparent image margins are never selected.
 * The normalized alpha contour follows the same crop and transform as the art. */
internal object StickerContour {
    fun create(bitmap:Bitmap?,crop:List<InkPoint>,fillPage:Boolean):Path {
        val path=Path()
        if(bitmap==null || fillPage || !bitmap.hasAlpha()) path.addRect(0f,0f,1f,1f,Path.Direction.CW)
        else {
            val side=256
            val w=minOf(side,bitmap.width);val h=minOf(side,bitmap.height)
            val small=Bitmap.createScaledBitmap(bitmap,w,h,true)
            val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h)
            val region=Region()
            for(y in 0 until h) {
                var x=0
                while(x<w) {
                    while(x<w && Color.alpha(pixels[y*w+x])<48) x++
                    val start=x
                    while(x<w && Color.alpha(pixels[y*w+x])>=48) x++
                    if(x>start) region.op(start,y,x,y+1,Region.Op.UNION)
                }
            }
            region.getBoundaryPath(path)
            path.transform(Matrix().apply { setScale(1f/w,1f/h) })
            if(small!==bitmap) small.recycle()
        }
        if(crop.size>=3) {
            val mask=Path().apply { crop.forEachIndexed { i,p -> if(i==0) moveTo(p.x,p.y) else lineTo(p.x,p.y) };close() }
            path.op(mask,Path.Op.INTERSECT)
        }
        return path
    }
}
