package com.qingyu.personalaios.data.local

import android.content.Context
import com.qingyu.personalaios.domain.DiaryJournal
import java.io.File
import kotlin.random.Random

/** All 18 user-supplied artworks, selected once and then persisted with the book. */
internal object InitialBookCovers {
    @Synchronized fun apply(context:Context,journal:DiaryJournal):DiaryJournal {
        val names=checkNotNull(context.assets.list("book-covers")).filter { it.endsWith(".webp") }.sorted()
        check(names.isNotEmpty())
        val name=names[Random.nextInt(names.size)]
        val directory=File(context.filesDir,"diary-media").apply { check(isDirectory || mkdirs()) }
        val target=File(directory,"initial-$name")
        if(!target.isFile) {
            val temporary=File(directory,"initial-$name.tmp")
            try {
                context.assets.open("book-covers/$name").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(target))
            } finally { temporary.delete() }
        }
        val band=0xFF000000L or name.substringAfterLast('_').substringBefore('.').toLong(16)
        return journal.copy(coverPath=target.absolutePath,bandColor=band)
    }
}
