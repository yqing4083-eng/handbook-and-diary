package com.qingyu.personalaios.feature.diary

import androidx.lifecycle.ViewModelStore
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {
    @Test fun closingAnEditorDropsBothHistoryStacksAndStartsNewIndependentEdits() = scenario { model,_ ->
        val a=InkStroke(points=listOf(InkPoint(.2f,.3f)))
        val b=a.copy(id="second",points=listOf(InkPoint(.3f,.4f)))
        model.ink("a",listOf(a));model.ink("a",listOf(a,b));model.undo("a")
        assertEquals(listOf(a),model.state.value.pages.first().strokes)
        assertTrue(model.finishEditing());model.resetEditingHistory("a")
        model.undo("a");model.redo("a")
        assertEquals(listOf(a),model.state.value.pages.first().strokes)
        model.ink("a",listOf(a,b));model.undo("a")
        assertEquals(listOf(a),model.state.value.pages.first().strokes)
    }
    @Test fun backgroundOrFailedSaveKeepsTheActiveEditorHistory() = scenario { model,repo ->
        val a=InkStroke(points=listOf(InkPoint(.2f,.3f)))
        model.ink("a",listOf(a));repo.fail=true;assertFalse(model.finishEditing())
        model.undo("a");assertTrue(model.state.value.pages.first().strokes.isEmpty())
        model.redo("a");repo.fail=false;assertTrue(model.finishEditing())
        model.undo("a");assertTrue(model.state.value.pages.first().strokes.isEmpty())
    }
    @Test fun positionedWritingUndoRedoAndSaveRetryRetainTheOrigin() = scenario { model,repo ->
        model.body("a","old diary")
        val blocks=listOf(RuledParagraph("centre",6,.5f,"春日晴空"))
        model.ruledText("a",blocks)
        model.undo("a");assertEquals("old diary",model.state.value.pages.first().body)
        model.redo("a");assertEquals(blocks,RuledText.decode(model.state.value.pages.first().bodyHtml))
        repo.fail=true;assertFalse(model.finishEditing())
        repo.fail=false;assertTrue(model.finishEditing())
        assertEquals(blocks,RuledText.decode(repo.pages.value.first().bodyHtml))
        assertEquals("春日晴空",repo.pages.value.first().body)
    }
    @Test fun emoticonsAndKaomojiStayOnTheChosenFaceAndCanBeUndone() = scenario { model,repo ->
        model.body("a","right text");model.body("a","left text",PaperFace.LEFT)
        model.addTextSticker("a","😊",PaperFace.LEFT)
        model.addTextSticker("a","(｡･ω･｡)ﾉ♡",PaperFace.RIGHT)
        assertEquals("😊",model.state.value.pages.first().leftFace!!.stickers.single().text)
        assertEquals("(｡･ω･｡)ﾉ♡",model.state.value.pages.first().stickers.single().text)
        model.undo("a",PaperFace.LEFT);assertTrue(model.state.value.pages.first().leftFace!!.stickers.isEmpty())
        model.redo("a",PaperFace.LEFT);assertTrue(model.finishEditing())
        assertEquals("right text",repo.pages.value.first().body)
        assertEquals("left text",repo.pages.value.first().leftFace!!.body)
    }
    @Test fun twoFacesSaveIndependentlyAndFailedSaveCanRetry() = scenario { model,repo ->
        model.body("a","right words")
        model.body("a","left words",PaperFace.LEFT)
        model.title("a","left title",PaperFace.LEFT)
        val sticker=DiarySticker(path="left.png",kind=StickerKind.STICKER)
        model.stickers("a",listOf(sticker),PaperFace.LEFT)
        repo.fail=true;assertFalse(model.finishEditing())
        repo.fail=false;assertTrue(model.finishEditing())
        val saved=repo.pages.value.first()
        assertEquals("right words",saved.body);assertTrue(saved.stickers.isEmpty())
        assertEquals("left words",saved.leftFace?.body);assertEquals("left title",saved.leftFace?.title)
        assertEquals(listOf(sticker),saved.leftFace?.stickers)
    }
    @Test fun faceUndoNeverRewindsTheOppositeFaceOrTyping() = scenario { model,repo ->
        val left=InkStroke(points=listOf(InkPoint(.2f,.3f)))
        val right=InkStroke(points=listOf(InkPoint(.7f,.8f)))
        model.ink("a",listOf(left),PaperFace.LEFT);model.ink("a",listOf(right))
        model.body("a","left typing",PaperFace.LEFT)
        model.undo("a",PaperFace.LEFT)
        assertTrue(model.state.value.pages.first().leftFace!!.strokes.isEmpty())
        assertEquals(listOf(right),model.state.value.pages.first().strokes)
        model.redo("a",PaperFace.LEFT);assertTrue(model.finishEditing())
        assertEquals(listOf(left),repo.pages.value.first().leftFace?.strokes)
        assertEquals("left typing",repo.pages.value.first().leftFace?.body)
    }
    private class Repository : DiaryRepository {
        override val journals=MutableStateFlow(listOf(DiaryJournal(id="main",name="original")))
        override suspend fun putJournal(journal:DiaryJournal) {
            if(fail) error("disk full")
            journals.value=journals.value.map { if(it.id==journal.id) journal else it }
        }
        override val pages = MutableStateFlow(listOf(DiaryPage(id = "a", order = 0), DiaryPage(id = "b", order = 1)))
        val saved = mutableListOf<List<DiaryPage>>()
        var fail = false
        var latency = 0L
        override suspend fun initialize() = Unit
        override suspend fun save(pages: List<DiaryPage>) {
            delay(latency)
            if (fail) error("disk full")
            saved.add(pages)
            this.pages.value = this.pages.value.map { old -> pages.find { it.id == old.id } ?: old }
        }
        override suspend fun addPage(): String {
            pages.value += DiaryPage(id = "c", order = 2); return "c"
        }
        override suspend fun createJournal(journal:DiaryJournal,afterId:String?):String {
            if(fail) error("disk full")
            val page=NewBook.page(journal,0)
            journals.value+=journal;pages.value+=page;return page.id
        }
        override suspend fun importPhotoPages(journalId:String,startId:String?,sources:List<String>):String {
            if(fail) error("import failed")
            val plan=PhotoPageFill.plan(pages.value,journalId,startId,sources)
            pages.value=pages.value.filter { it.journalId!=journalId }+plan.pages
            return plan.firstId
        }
    }
    @Test fun creationAlwaysUsesNeoBookWithoutNumberingAndCarriesKind() = scenario { model,repo ->
        model.createJournal(BookKind.SCRAPBOOK);runCurrent()
        assertEquals("NeoBook",model.state.value.journal.name)
        assertEquals(BookKind.SCRAPBOOK,model.state.value.journal.kind)
        assertEquals(PaperStyle.PLAIN,model.state.value.pages.single().style)
        model.createJournal(BookKind.DIARY);runCurrent()
        assertEquals("NeoBook",model.state.value.journal.name)
        assertEquals(BookKind.DIARY,model.state.value.journal.kind)
        assertTrue(model.state.value.pages.single().singleFace)
        assertEquals(2,repo.journals.value.count { it.name=="NeoBook" })
    }
    @Test fun manyBookSelectionHandsOffImmediatelyWithoutStructuralJobs() = scenario { model,repo ->
        val books=List(80) { NewBook.journal(BookKind.SCRAPBOOK,it) }
        repo.journals.value=books
        repo.pages.value=books.flatMap { book -> List(2) { NewBook.page(book,it) } }
        runCurrent()
        for(book in books+books.reversed()) {
            assertTrue(model.switchJournal(book.id))
            assertEquals(book.id,model.state.value.journalId)
            assertEquals(2,model.state.value.pages.size)
            assertTrue(model.state.value.pages.all { it.journalId==book.id })
            assertFalse(model.state.value.working)
        }
        assertTrue(repo.saved.isEmpty())
        assertFalse(model.switchJournal("missing"))
        assertEquals(books.first().id,model.state.value.journalId)
    }
    @Test fun immediateShelfHandoffNeverDiscardsOrBypassesAnUnsavedEntry() = scenario { model,repo ->
        val next=DiaryJournal(id="next",name="next")
        repo.journals.value+=next
        repo.pages.value+=DiaryPage(id="next-page",journalId=next.id,order=0)
        runCurrent()
        model.body("a","keep this draft")
        assertFalse(model.switchShelfNow(next.id))
        assertEquals("main",model.state.value.journalId)
        repo.fail=true
        assertFalse(model.switchJournal(next.id))
        assertEquals("keep this draft",model.state.value.pages.first().body)
        repo.fail=false
        assertTrue(model.flush())
        assertTrue(model.switchShelfNow(next.id))
        assertEquals(next.id,model.state.value.journalId)
        assertEquals("keep this draft",repo.pages.value.first().body)
    }
    private fun scenario(block: suspend TestScope.(DiaryViewModel, Repository) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = Repository(); val model = DiaryViewModel(repository)
        val store = ViewModelStore().apply { put("diary", model) }
        try { runCurrent(); block(model, repository) }
        finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun scrapbookRejectsTextTapeAndPaperChangesButSupportsPhotoDrawing() = scenario { model,repo ->
        repo.journals.value=listOf(repo.journals.value.single().copy(kind=BookKind.SCRAPBOOK));runCurrent()
        val before=model.state.value.pages.first()
        model.title("a","no");model.body("a","no")
        model.addText("a");model.addTape("a");model.paper("a",PaperStyle.GRID)
        assertEquals(before,model.state.value.pages.first())
        val stroke=InkStroke(points=listOf(InkPoint(.1f,.1f),InkPoint(.7f,.6f)))
        model.ink("a",listOf(stroke));model.undo("a")
        assertTrue(model.state.value.pages.first().strokes.isEmpty())
        model.redo("a")
        model.stickers("a",listOf(DiarySticker(path="image.png")))
        assertTrue(model.flush())
        assertEquals("image.png",repo.pages.value.first().stickers.single().path)
        assertTrue(repo.pages.value.first().body.isBlank())
        assertEquals(listOf(stroke),repo.pages.value.first().strokes)
    }
    @Test fun diaryKeepsRuledTemplateWhileAllowingWriting() = scenario { model,repo ->
        repo.journals.value=listOf(repo.journals.value.single().copy(kind=BookKind.DIARY,template=PaperStyle.LINED))
        repo.pages.value=repo.pages.value.map { it.copy(style=PaperStyle.LINED,singleFace=true,canvasAspect=NewBook.FaceAspect) };runCurrent()
        model.paper("a",PaperStyle.PLAIN);model.body("a","entry");assertTrue(model.flush())
        assertEquals(PaperStyle.LINED,repo.pages.value.first().style)
        assertEquals("entry",repo.pages.value.first().body)
        assertTrue(repo.pages.value.first().singleFace)
    }
    @Test fun landscapeScrapbookAcceptsAndMovesLockedStickerOverPhotos() = scenario { model,repo ->
        repo.journals.value=listOf(repo.journals.value.single().copy(kind=BookKind.SCRAPBOOK));runCurrent()
        val photo=DiarySticker(path="photo.png")
        val sticker=DiarySticker(path="sticker.png",kind=StickerKind.STICKER)
        model.stickers("a",listOf(photo,sticker))
        model.stickers("a",listOf(photo,sticker.copy(x=.4f,locked=true)))
        model.body("a","not supported")
        assertTrue(model.flush())
        val saved=repo.pages.value.first()
        assertTrue(saved.body.isEmpty())
        assertEquals(.4f,saved.stickers.last().x,0f);assertTrue(saved.stickers.last().locked)
        model.undo("a");model.redo("a");assertTrue(model.flush())
        assertEquals(listOf(photo,sticker.copy(x=.4f,locked=true)),repo.pages.value.first().stickers)
    }
    @Test fun coverConfirmationPreservesAllPagesAndFailureKeepsOldJournal() = scenario { model,repo ->
        val original=repo.journals.value.single()
        val pages=repo.pages.value
        repo.fail=true
        assertFalse(model.saveCover(original.copy(bandColor=0xFF112233L),null))
        assertEquals(original,repo.journals.value.single())
        repo.fail=false
        assertTrue(model.saveCover(original.copy(bandColor=0xFF112233L),null))
        assertEquals(0xFF112233L,repo.journals.value.single().bandColor)
        assertEquals(pages,repo.pages.value)
        assertFalse(model.state.value.working)
    }
    @Test fun photoImportFlushesDraftAndReusesBlankPaperWithoutReplacingWriting() = scenario { model,repo ->
        model.body("a","keep this draft")
        model.addPhotoPages("a",listOf("one.png","two.png"));runCurrent()
        assertEquals("keep this draft",repo.saved.first().first().body)
        assertEquals(3,model.state.value.pages.size)
        assertEquals("keep this draft",model.state.value.pages.first().body)
        assertEquals("b",model.state.value.pendingPage)
        assertEquals(listOf("one.png","two.png"),model.state.value.pages.drop(1).map { it.stickers.single().path })
    }
    @Test fun failedOrCancelledPhotoImportKeepsAllOriginalPagesAndUnlocksOperations() = scenario { model,repo ->
        val before=repo.pages.value
        model.addPhotoPages("a",emptyList());runCurrent()
        assertEquals(before,repo.pages.value);assertNull(model.state.value.error)
        repo.fail=true;model.addPhotoPages("a",listOf("photo.png"));runCurrent()
        assertEquals(before,repo.pages.value);assertNotNull(model.state.value.error)
        assertFalse(model.state.value.working)
    }
    @Test fun switchingPagesDuringDebounceSavesBothDrafts() = scenario { model, repo ->
        model.body("a", "page A"); advanceTimeBy(100); model.body("b", "page B")
        advanceTimeBy(351); runCurrent()
        assertEquals(setOf("a", "b"), repo.saved.last().map { it.id }.toSet())
        assertEquals(listOf("page A", "page B"), repo.pages.value.map { it.body })
        assertFalse(model.state.value.saving)
    }
    @Test fun openingLegacyNotePreservesFormattingUntilTextActuallyChanges() = scenario { model,repo ->
        repo.pages.value=repo.pages.value.map { if(it.id=="a") it.copy(body="Styled words",bodyHtml="<p><b>Styled</b> words</p>") else it }
        runCurrent()
        val revision=model.state.value.pages[0].revision
        model.body("a","Styled words")
        assertEquals(revision,model.state.value.pages[0].revision)
        assertEquals("<p><b>Styled</b> words</p>",model.state.value.pages[0].bodyHtml)
        model.body("a","plain replacement");assertTrue(model.flush())
        assertEquals("",repo.pages.value[0].bodyHtml)
        assertEquals("plain replacement",repo.pages.value[0].body)
    }
    @Test fun saveFailureRetainsDraftAndRetryPersistsIt() = scenario { model, repo ->
        model.title("a", "never lose this"); repo.fail = true
        assertFalse(model.flush()); assertNotNull(model.state.value.error)
        assertEquals("never lose this", model.state.value.pages[0].title)
        repo.fail = false; assertTrue(model.flush())
        assertEquals("never lose this", repo.pages.value[0].title)
        assertNull(model.state.value.error)
    }
    @Test fun undoAndRedoKeepTextAlongsideInk() = scenario { model, repo ->
        model.body("a", "words")
        val strokes = listOf(InkStroke(points = listOf(InkPoint(.2f,.3f))))
        model.ink("a", strokes); model.undo("a")
        assertTrue(model.state.value.pages[0].strokes.isEmpty())
        model.redo("a"); model.flush()
        assertEquals(strokes, repo.pages.value[0].strokes)
        assertEquals("words", repo.pages.value[0].body)
    }
    @Test fun canceledAutosaveRetainsEveryPendingPage() = scenario { model, repo ->
        repo.latency = 100
        model.body("a", "first"); advanceTimeBy(351); runCurrent()
        model.body("b", "second"); advanceTimeBy(451); runCurrent()
        assertEquals(listOf("first", "second"), repo.pages.value.map { it.body })
        assertNull(model.state.value.error)
    }
    @Test fun creatingPageFlushesExistingWorkFirst() = scenario { model, repo ->
        model.body("a", "before new page"); model.addPage(); runCurrent()
        assertEquals("before new page", repo.saved.first().first().body)
        assertEquals("c", model.state.value.pendingPage)
        assertEquals(3, model.state.value.pages.size)
    }
    @Test fun stickerPlacementSurvivesTextEditsInkUndoAndSaveRetry() = scenario { model,repo ->
        val image=DiarySticker(path="local.png").transformed(.1f,.1f,1.2f,15f)
        model.stickers("a",listOf(image));model.body("a","collage")
        model.ink("a",listOf(InkStroke(points=listOf(InkPoint(.2f,.3f)))));model.undo("a")
        repo.fail=true;assertFalse(model.flush());repo.fail=false;assertTrue(model.flush())
        assertEquals(listOf(image),repo.pages.value[0].stickers)
        assertEquals("collage",repo.pages.value[0].body);assertTrue(repo.pages.value[0].strokes.isEmpty())
    }
    @Test fun deletingAnImageCanBeUndoneWithoutRewindingNewText() = scenario { model,repo ->
        val image=DiarySticker(path="local.png")
        model.stickers("a",listOf(image));model.stickers("a",emptyList());model.body("a","new words")
        model.undo("a");assertEquals(listOf(image),model.state.value.pages[0].stickers)
        model.redo("a");model.flush()
        assertTrue(repo.pages.value[0].stickers.isEmpty());assertEquals("new words",repo.pages.value[0].body)
    }
    @Test fun trashingClosedBookSelectsItsNeighbourAndKeepsEveryPage() = scenario { model,repo ->
        repo.journals.value+=DiaryJournal(id="next",order=1);runCurrent()
        model.body("a","preserved before deletion");model.trashJournal("main");runCurrent()
        assertEquals("next",model.state.value.journalId)
        assertTrue(repo.journals.value.first().deletedAt>0)
        assertEquals(2,model.state.value.allPages.size)
        assertEquals("preserved before deletion",repo.pages.value.first().body)
        model.updateJournal(repo.journals.value.first().copy(deletedAt=0));runCurrent()
        model.selectJournal("main");runCurrent()
        assertEquals(2,model.state.value.pages.size)
    }
    @Test fun trashingTheLastBookLeavesARecoverableEmptyShelf() = scenario { model,repo ->
        model.trashJournal("main");runCurrent()
        assertFalse(model.state.value.hasJournal)
        assertTrue(model.state.value.pages.isEmpty())
        assertEquals(2,repo.pages.value.size)
        model.updateJournal(repo.journals.value.first().copy(deletedAt=0));runCurrent()
        assertTrue(model.state.value.hasJournal)
        assertEquals(2,model.state.value.pages.size)
    }
    @Test fun failedDraftSavePreventsTrashingTheBook() = scenario { model,repo ->
        model.body("a","unsaved words");repo.fail=true;model.trashJournal("main");runCurrent()
        assertEquals(0L,repo.journals.value.first().deletedAt)
        assertEquals("unsaved words",model.state.value.pages.first().body)
        assertNotNull(model.state.value.error)
    }
}
