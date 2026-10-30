package com.qingyu.personalaios.feature.diary

import android.graphics.*

/** Pigment catches the fixed paper tooth. Repeated dabs do not move the grain
 * with the tip, so a wax stroke retains pores instead of turning into a marker. */
internal object WaxCrayonGrain {
    private data class Key(val color:Int,val pressure:Int)
    private val cache=object:android.util.LruCache<Key,Bitmap>(512*1024) {
        override fun sizeOf(key:Key,value:Bitmap)=value.allocationByteCount
    }
    @Synchronized fun shader(color:Long,pressure:Int,scale:Float):BitmapShader {
        val key=Key(color.toInt() or 0xFF000000.toInt(),pressure)
        val bitmap=cache.get(key) ?: run {
            val n=64;val pixels=IntArray(n*n)
            fun noise(x:Int,y:Int):Float {
                var h=x*374761393+y*668265263+0x13579BDF;h=(h xor (h ushr 13))*1274126177
                return ((h xor (h ushr 16)) and 65535)/65535f
            }
            for(y in 0 until n) for(x in 0 until n) {
                val tooth=.55f*noise(x/3,y/3)+.30f*noise(x,y)+.15f*noise(x/9,y/2)
                val threshold=.65f-.075f*pressure
                val contact=((tooth-threshold)*4.3f).coerceIn(0f,1f)
                val alpha=if(contact==0f) 0 else ((.20f+.80f*contact)*255).toInt()
                pixels[y*n+x]=(alpha shl 24) or (key.color and 0xFFFFFF)
            }
            Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888).also { cache.put(key,it) }
        }
        return BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(scale,scale) })
        }
    }
}
