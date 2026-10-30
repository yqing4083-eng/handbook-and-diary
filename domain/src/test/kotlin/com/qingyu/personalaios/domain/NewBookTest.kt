package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class NewBookTest {
    @Test fun everyNewBookUsesNeoBookAndSelectedFormat() {
        for(kind in listOf(BookKind.SCRAPBOOK,BookKind.DIARY)) repeat(20) {
            val book=NewBook.journal(kind,it)
            assertEquals("NeoBook",book.name)
            assertEquals(kind,book.kind)
            val page=NewBook.page(book,0)
            assertEquals(book.id,page.journalId)
            assertEquals(kind==BookKind.DIARY,page.singleFace)
            assertEquals(if(kind==BookKind.DIARY) PaperStyle.LINED else PaperStyle.PLAIN,page.style)
            assertEquals(if(kind==BookKind.DIARY) NewBook.FaceAspect else NewBook.SpreadAspect,page.canvasAspect,0f)
        }
    }
    @Test fun diaryImageImportKeepsSingleFaceLayoutAndWriting() {
        val book=NewBook.journal(BookKind.DIARY)
        val written=NewBook.page(book,0).copy(body="keep")
        val blank=NewBook.page(book,1)
        val plan=PhotoPageFill.plan(listOf(written,blank),book.id,written.id,listOf("a","b"),book.kind)
        assertEquals(written,plan.pages.first())
        assertEquals(blank.id,plan.firstId)
        plan.pages.drop(1).forEach {
            assertTrue(it.singleFace);assertEquals(PaperStyle.LINED,it.style)
            assertEquals(NewBook.FaceAspect,it.canvasAspect,0f)
        }
    }
    @Test fun legacyPaperTemplatesRemainUnchanged() {
        val legacy=DiaryJournal(template=PaperStyle.GRID)
        assertEquals(BookKind.LEGACY,legacy.kind)
        assertEquals(PaperStyle.GRID,NewBook.page(legacy,3).style)
        assertFalse(NewBook.page(legacy,3).singleFace)
    }
}
