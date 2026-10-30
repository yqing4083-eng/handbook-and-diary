package com.qingyu.personalaios.feature.diary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.util.LruCache
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Deduplicate one file, without holding a global lock during file IO/decoding.
 * Unrelated covers and page photos must be able to finish independently. */
internal class DiaryImageCache(private val decode:(String)->Bitmap?) {
    private val cache=object:LruCache<String,Bitmap>(24*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    private val loading=ConcurrentHashMap<String,CompletableFuture<Bitmap?>>()
    fun peek(path:String):Bitmap?=cache.get(path)
    fun load(path:String):Bitmap? {
        peek(path)?.let { return it }
        val result=CompletableFuture<Bitmap?>()
        val previous=loading.putIfAbsent(path,result)
        if(previous!=null) return try { previous.get() } catch(_:InterruptedException) {
            Thread.currentThread().interrupt();null
        }
        try {
            val image=peek(path) ?: decode(path)
            if(image!=null) cache.put(path,image)
            result.complete(image);return image
        } catch(error:Throwable) {
            result.complete(null);throw error
        } finally { loading.remove(path,result) }
    }
}

/** Immutable private media, shared by book previews, live collage and exports. */
internal object DiaryImages {
    private val cache=DiaryImageCache(::decode)
    fun peek(path:String):Bitmap?=cache.peek(path)
    fun load(path:String):Bitmap?=cache.load(path)
    private fun decode(path:String):Bitmap? {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1280) sample*=2
        return runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.io.File(path))) { decoder,_,_ ->
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sample)
        } }.getOrNull()
        // No recycling on eviction: a displayed Compose image may still reference it.
    }
}
