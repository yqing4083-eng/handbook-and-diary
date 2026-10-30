package com.qingyu.personalaios.feature.diary

import android.graphics.Bitmap
import com.qingyu.personalaios.domain.*
import java.util.concurrent.Executors

/** CPU rasterization and file decoding stay off the UI / Canvas animation thread. */
internal class BookTextureStore @JvmOverloads constructor(private val ready: ()->Unit,
    private val rasterPage:(DiaryPage,Boolean)->Bitmap=BookTextures::page,
    private val rasterCover:(DiaryJournal)->Bitmap=BookTextures::cover) {
    private fun executor(name:String)=Executors.newSingleThreadExecutor { task -> Thread({
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run()
    },name).apply { isDaemon=true } }
    // A large photograph/page raster can take much longer than a cover. Never
    // queue the cover needed by a swipe behind that speculative page work.
    private val covers=executor("DiaryCovers")
    private val pages=executor("DiaryPages")
    private val bitmaps=LinkedHashMap<String,Bitmap>(24,.75f,true)
    private val pending=HashSet<String>()
    private var wanted:Set<String> = emptySet()
    @Volatile private var closed=false
    fun pageKey(page:DiaryPage,small:Boolean=false)="p:${page.id}:${page.revision}${if(small) ":small" else ""}"
    // Names/IDs are not painted on the cover. Books sharing immutable artwork
    // can share the CPU/GPU texture instead of decoding the same image again.
    fun coverKey(journal:DiaryJournal)="c:${journal.coverPath}:${journal.coverColor}:${journal.bandColor}"
    @Synchronized fun take(key:String):Bitmap?=bitmaps.remove(key)
    @Synchronized fun wanted(keys:Set<String>) { wanted=keys }
    private fun prepare(key:String,create:()->Bitmap) {
        synchronized(this) { if(closed || bitmaps.containsKey(key) || !pending.add(key)) return }
        (if(key.startsWith("c:")) covers else pages).execute {
            synchronized(this) { if(closed || key !in wanted) { pending.remove(key);return@execute } }
            val bitmap=BookStartupCache.take(key) ?: runCatching(create).getOrNull()
            val stored=synchronized(this) {
                pending.remove(key)
                if(closed || key !in wanted) { bitmap?.recycle();false } else if(bitmap!=null) {
                    bitmaps[key]=bitmap
                    while(bitmaps.size>12 || bitmaps.values.sumOf { it.allocationByteCount.toLong() }>32L*1024*1024) { val entry=bitmaps.entries.first();bitmaps.remove(entry.key);entry.value.recycle() }
                    true
                } else false
            }
            if(stored && !closed) ready()
        }
    }
    fun preparePage(page:DiaryPage,small:Boolean=false)=prepare(pageKey(page,small)) { rasterPage(page,small) }
    fun prepareCover(journal:DiaryJournal)=prepare(coverKey(journal)) { rasterCover(journal) }
    fun release() {
        closed=true;covers.shutdownNow();pages.shutdownNow()
        synchronized(this) { bitmaps.values.forEach { it.recycle() };bitmaps.clear() }
    }
}
