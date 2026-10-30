package com.qingyu.personalaios.feature.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared ownership transfer: startup CPU textures become the real book's upload
 * inputs, rather than rendering throwaway previews and decoding everything twice. */
internal object BookStartupCache {
    private val cache=object:LruCache<String,Bitmap>(24*1024*1024) { override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount }
    @Synchronized fun take(key:String)=cache.remove(key)
    @Synchronized fun put(key:String,image:Bitmap) { cache.put(key,image) }
}
suspend fun warmDiaryResources(context:Context,state:DiaryState)=withContext(Dispatchers.Default) {
    val visible=state.journals.filter { it.deletedAt==0L };val index=visible.indexOfFirst { it.id==state.journalId }.coerceAtLeast(0)
    visible.subList((index-1).coerceAtLeast(0),(index+2).coerceAtMost(visible.size)).forEach { j ->
        BookStartupCache.put("c:${j.coverPath}:${j.coverColor}:${j.bandColor}",BookTextures.cover(j))
    }
    val pageIndex=state.pages.indexOfFirst { it.id==state.journal.lastPageId }.coerceAtLeast(0)
    state.pages.subList((pageIndex-1).coerceAtLeast(0),(pageIndex+2).coerceAtMost(state.pages.size)).forEach { p ->
        BookStartupCache.put("p:${p.id}:${p.revision}",BookTextures.page(p,false))
    }
    BitmapFactory.decodeResource(context.resources,R.drawable.editor_tools_atlas)?.let { atlas ->
        val sprites=ToolSpriteAtlas.extract(atlas);ToolSpriteAtlas.tips(sprites);ToolSpriteAtlas.bands(sprites);atlas.recycle()
    }
}
