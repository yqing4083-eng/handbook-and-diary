package com.qingyu.personalaios.feature.diary

import android.content.res.AssetManager
import android.graphics.*
import android.util.LruCache
import com.qingyu.personalaios.domain.StickerArt
import java.io.ByteArrayOutputStream

/** Decode only a sticker region; avoid holding thirteen full sheets in memory. */
internal object StickerPack {
    private val thumbnails=object:LruCache<StickerArt,Bitmap>(2*1024*1024) {
        override fun sizeOf(key:StickerArt,value:Bitmap)=value.allocationByteCount
    }
    fun bitmap(assets:AssetManager,art:StickerArt,thumbnail:Boolean=false):Bitmap {
        if(thumbnail) synchronized(thumbnails) { thumbnails.get(art)?.let { return it } }
        val result=assets.open("stickers/sheet-${art.sheet.toString().padStart(2,'0')}.png").use { input ->
            val decoder=checkNotNull(BitmapRegionDecoder.newInstance(input,false))
            try {
                val options=BitmapFactory.Options().apply {
                    inPreferredConfig=Bitmap.Config.ARGB_8888;inSampleSize=1
                    if(thumbnail) while(maxOf(art.right-art.left,art.bottom-art.top)/inSampleSize>192) inSampleSize*=2
                }
                checkNotNull(decoder.decodeRegion(Rect(art.left,art.top,art.right,art.bottom),options))
            } finally { decoder.recycle() }
        }
        if(thumbnail) synchronized(thumbnails) { thumbnails.put(art,result) }
        return result
    }
    fun png(assets:AssetManager,art:StickerArt):ByteArray {
        val image=bitmap(assets,art)
        return try { ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray() } finally { image.recycle() }
    }
}
