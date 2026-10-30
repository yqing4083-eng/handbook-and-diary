package com.qingyu.personalaios.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingyu.personalaios.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class DiaryState(val pages: List<DiaryPage> = emptyList(), val saving: Boolean = false,
    val error: String? = null, val pendingPage: String? = null,
    val allPages: List<DiaryPage> = emptyList(), val journals: List<DiaryJournal> = emptyList(),
    val journalId: String = "main", val working: Boolean = false,val clips:List<InkClip> = emptyList(),val loaded:Boolean=false) {
    val journal get() = journals.find { it.id == journalId && it.deletedAt==0L } ?: DiaryJournal(id="main",name=if(journals.isEmpty()) "我的日记" else "新建书册")
    val hasJournal get() = journals.any { it.id==journalId && it.deletedAt==0L }
}

@HiltViewModel
class DiaryViewModel @Inject constructor(private val repository: DiaryRepository) : ViewModel() {
    private val mutable = MutableStateFlow(DiaryState())
    val state = mutable.asStateFlow()
    private val drafts = linkedMapOf<String, DiaryPage>()
    private val pending = linkedMapOf<String, DiaryPage>()
    private val writes = Mutex()
    private val operations = Mutex()
    private var requestedJournal:String?=null
    private var saveJob: Job? = null
    private val imageImports=mutableSetOf<Job>()
    private data class ArtEdit(val strokes:List<InkStroke>?=null,val stickers:List<DiarySticker>?=null,val body:String?=null,val bodyHtml:String?=null,val title:String?=null,val wallpaper:String?=null) {
        fun snapshot(page:DiaryPage)=ArtEdit(if(strokes!=null) page.strokes else null,if(stickers!=null) page.stickers else null,if(body!=null) page.body else null,if(bodyHtml!=null) page.bodyHtml else null,if(title!=null) page.title else null,if(wallpaper!=null) page.wallpaper else null)
        fun apply(page:DiaryPage)=page.copy(strokes=strokes ?: page.strokes,stickers=stickers ?: page.stickers,body=body ?: page.body,bodyHtml=bodyHtml ?: page.bodyHtml,title=title ?: page.title,wallpaper=wallpaper ?: page.wallpaper)
    }
    private val undo = mutableMapOf<String, ArrayDeque<ArtEdit>>()
    private val redo = mutableMapOf<String, ArrayDeque<ArtEdit>>()
    private val photoTime=mutableMapOf<String,Long>()
    private fun rememberEdit(id:String,edit:ArtEdit) {
        undo.getOrPut(id) { ArrayDeque() }.apply { addLast(edit);if(size>50) removeFirst() };redo[id]?.clear()
    }
    init {
        viewModelScope.launch {
            try {
                repository.initialize()
                combine(repository.pages, repository.journals,repository.clips) { pages, journals,clips -> Triple(pages,journals,clips) }.collect { (stored,journals,clips) ->
                    val pages = stored.map { page -> drafts[page.id]?.takeIf { it.revision >= page.revision } ?: page }
                    mutable.update { old ->
                        val journalId=(requestedJournal ?: old.journalId).takeIf { id -> journals.any { it.id==id && it.deletedAt==0L } }
                            ?: journals.firstOrNull { it.deletedAt==0L }?.id ?: "main"
                        if(journals.any { it.id==requestedJournal }) requestedJournal=null
                        old.copy(allPages=pages,journals=journals,journalId=journalId,clips=clips,loaded=true,
                            pages=pages.filter { it.journalId==journalId && it.deletedAt==0L && journals.any { j -> j.id==journalId && j.deletedAt==0L } })
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(error = "日记暂时无法读取，请退出后重试。",loaded=true) } }
        }
    }
    private fun edit(id: String, change: (DiaryPage) -> DiaryPage) {
        val page = state.value.pages.find { it.id == id } ?: return
        val updated = change(page).copy(revision = page.revision + 1)
        drafts[id] = updated; pending[id] = updated
        mutable.update { it.copy(pages = it.pages.map { p -> if (p.id == id) updated else p },
            allPages=it.allPages.map { p -> if(p.id==id) updated else p },saving = true) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(350); flush() }
    }
    private fun canWrite(id:String)=state.value.pages.find { it.id==id }?.let { page ->
        state.value.journals.find { it.id==page.journalId }?.kind!=BookKind.SCRAPBOOK
    } ?: false
    private fun facePage(id:String,face:PaperFace)=state.value.pages.find { it.id==id }?.let { DiaryFaces.page(it,face) }
    private fun editFace(id:String,face:PaperFace,change:(DiaryPage)->DiaryPage) = edit(id) {
        DiaryFaces.replace(it,face,change(DiaryFaces.page(it,face)))
    }
    private fun historyKey(id:String,face:PaperFace)=if(face==PaperFace.RIGHT) id else "$id:left"
    /** Saving in the background is not an editor exit. Only an explicit new
     * editor session or a successfully closed sheet resets its local history. */
    fun resetEditingHistory(id:String,face:PaperFace=PaperFace.RIGHT) {
        val key=historyKey(id,face)
        undo.remove(key);redo.remove(key);photoTime.remove(key);photoTime.remove("$key:text")
    }
    fun title(id: String, value: String,face:PaperFace=PaperFace.RIGHT) { if(canWrite(id)) editFace(id,face) { it.copy(title = value) } }
    fun body(id:String,value:String,face:PaperFace=PaperFace.RIGHT) {
        if(!canWrite(id)) return
        val page=facePage(id,face) ?: return
        // Keep existing saved formatting until the user actually replaces the text.
        if(page.body!=value) editFace(id,face) { it.copy(body=value,bodyHtml="") }
    }
    fun ruledText(id:String,blocks:List<RuledParagraph>,face:PaperFace=PaperFace.RIGHT,titleX:Float?=null,document:RuledDocument?=null,title:String?=null,titleInk:List<RuledGlyph> = emptyList()) {
        if(!canWrite(id)) return
        val page=facePage(id,face) ?: return
        val body=RuledText.plain(blocks);val html=RuledText.encode(blocks.filter { it.text.isNotEmpty() },titleX ?: RuledText.titleX(page.bodyHtml),document,titleInk)
        if(page.body==body && page.bodyHtml==html && (title==null || page.title==title)) return
        val key=historyKey(id,face);val now=System.currentTimeMillis()
        if(now-(photoTime["$key:text"] ?: 0)>500) rememberEdit(key,ArtEdit(body=page.body,bodyHtml=page.bodyHtml,title=if(title!=null) page.title else null))
        photoTime["$key:text"]=now
        editFace(id,face) { it.copy(body=body,bodyHtml=html,title=title ?: it.title) }
    }
    fun stickers(id: String, value: List<DiarySticker>,face:PaperFace=PaperFace.RIGHT) {
        val page=facePage(id,face) ?: return
        if(page.stickers==value) return
        val now=System.currentTimeMillis()
        val key=historyKey(id,face)
        if(now-(photoTime[key] ?: 0)>500 || page.stickers.map { it.id }!=value.map { it.id }) rememberEdit(key,ArtEdit(stickers=page.stickers))
        photoTime[key]=now;editFace(id,face) { it.copy(stickers=value) }
    }
    fun addImage(id: String, source: String,face:PaperFace=PaperFace.RIGHT,asSticker:Boolean=false):Job {
        val job=viewModelScope.launch(start=CoroutineStart.LAZY) {
        try {
            val path=repository.importImage(source)
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(path,bounds) }
            val aspect=if(bounds.outWidth>0 && bounds.outHeight>0) bounds.outHeight.toFloat()/bounds.outWidth else 1f
            val canvas=facePage(id,face)?.canvasAspect ?: .75f
            val w=minOf(.65f,.65f/(aspect*canvas));val h=w*aspect*canvas
            facePage(id,face)?.let { stickers(id,it.stickers+DiarySticker(path=path,x=(1-w)/2,y=(1-h)/2,width=w,height=h,kind=if(asSticker) StickerKind.STICKER else StickerKind.IMAGE),face) }
            flush()
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { mutable.update { it.copy(error="图片未能贴入，请重新选择。") } }
        }
        imageImports+=job
        job.invokeOnCompletion { imageImports.remove(job) }
        job.start();return job
    }
    fun addTape(id: String, pattern: TapePattern = TapePattern.STRIPE) = elements(id,writingOnly=true) { list ->
        list + DiarySticker(path = "", kind = StickerKind.TAPE, x = .14f, y = .3f, width = .72f, height = .18f,
            rotation = -12f, tape = pattern, color = 0xFFF2D98C)
    }
    fun addText(id: String) = elements(id,writingOnly=true) { list ->
        list + DiarySticker(path = "", kind = StickerKind.TEXT, x = .08f, y = .12f, width = .84f, height = .24f,
            fontSize = .05f, color = 0xFF243749, text = "")
    }
    fun addTextSticker(id:String,text:String,face:PaperFace=PaperFace.RIGHT) {
        val page=facePage(id,face) ?: return
        if(text.isBlank()) return
        val emoji=text.codePointCount(0,text.length)<=2
        val w=if(emoji) .3f else .84f
        stickers(id,page.stickers+DiarySticker(path="",kind=StickerKind.TEXT,text=text.take(4000),
            x=(1-w)/2,y=.4f,width=w,height=.18f,fontSize=if(emoji) .13f else .055f),face)
    }
    fun addSticker(id: String, art: StickerArt,assets:android.content.res.AssetManager,face:PaperFace=PaperFace.RIGHT):Job {
        val job=viewModelScope.launch(start=CoroutineStart.LAZY) {
        try {
            val path = withContext(Dispatchers.IO) { repository.importBytes(StickerPack.png(assets,art), stickerFile(art)) }
            facePage(id,face)?.let { page ->
                val (width,height)=stickerSize(art,page.canvasAspect)
                stickers(id, page.stickers + DiarySticker(path = path, kind = StickerKind.STICKER,
                    x = (1f - width) / 2f, y = (1f - height) / 2f, width = width, height = height, rotation = 0f),face)
            }
            flush()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutable.update { it.copy(error = "贴纸未能放入，请重试。") } }
        }
        imageImports+=job
        job.invokeOnCompletion { imageImports.remove(job) }
        job.start();return job
    }
    private fun elements(id: String,writingOnly:Boolean=false,change: (List<DiarySticker>) -> List<DiarySticker>) {
        if(writingOnly && !canWrite(id)) return
        val page = state.value.pages.find { it.id == id } ?: return
        stickers(id, change(page.stickers))
    }
    fun element(id: String, elementId: String, change: (DiarySticker) -> DiarySticker) = elements(id) { list ->
        list.map { if (it.id == elementId) change(it) else it }
    }
    fun collage(id: String, ids: List<String>, columns: Int, rows: Int) = elements(id) { list ->
        val slots = collageSlots(ids.size, columns, rows)
        if (slots.isEmpty()) list else list.map { sticker ->
            val at = ids.indexOf(sticker.id)
            if (at < 0 || at >= slots.size) sticker else {
                val slot = slots[at]
                sticker.copy(x = slot.x, y = slot.y, width = slot.width, height = slot.height, rotation = 0f)
            }
        }
    }
    suspend fun importPaperWallpaper(source:String)=withContext(Dispatchers.IO) { repository.importImage(source) }
    suspend fun importPaperWallpaper(data:ByteArray,name:String)=withContext(Dispatchers.IO) { repository.importBytes(data,name) }
    fun wallpaper(id:String,path:String,face:PaperFace=PaperFace.RIGHT) {
        val page=facePage(id,face) ?: return
        if(canWrite(id) && page.wallpaper!=path) {
            rememberEdit(historyKey(id,face),ArtEdit(wallpaper=page.wallpaper))
            editFace(id,face) { it.copy(wallpaper=path) }
        }
    }
    fun paper(id: String) { if(state.value.journal.kind==BookKind.LEGACY) edit(id) { it.copy(style = PaperStyle.entries[(it.style.ordinal + 1) % PaperStyle.entries.size]) } }
    fun paper(id:String,style:PaperStyle) { if(state.value.journal.kind==BookKind.LEGACY) edit(id) { it.copy(style=style) } }
    fun ink(id: String, strokes: List<InkStroke>,face:PaperFace=PaperFace.RIGHT) {
        // The landscape editor also supports drawing over scrapbook photos.
        val page = facePage(id,face) ?: return
        if (strokes == page.strokes) return
        val key=historyKey(id,face)
        rememberEdit(key,ArtEdit(strokes=page.strokes));photoTime.remove(key)
        editFace(id,face) { it.copy(strokes = strokes) }
    }
    fun undo(id: String,face:PaperFace=PaperFace.RIGHT) {
        val key=historyKey(id,face)
        val old = undo[key]?.removeLastOrNull() ?: return
        val page = facePage(id,face) ?: return
        redo.getOrPut(key) { ArrayDeque() }.addLast(old.snapshot(page));photoTime.remove(key)
        editFace(id,face) { old.apply(it) }
    }
    fun redo(id: String,face:PaperFace=PaperFace.RIGHT) {
        val key=historyKey(id,face)
        val next = redo[key]?.removeLastOrNull() ?: return
        val page = facePage(id,face) ?: return
        undo.getOrPut(key) { ArrayDeque() }.addLast(next.snapshot(page));photoTime.remove(key)
        editFace(id,face) { next.apply(it) }
    }
    suspend fun flush(): Boolean = writes.withLock {
        val snapshot = pending.values.toList()
        if (snapshot.isEmpty()) return@withLock true
        try {
            repository.save(snapshot)
            snapshot.forEach { page -> if (pending[page.id]?.revision == page.revision) pending.remove(page.id) }
            mutable.update { it.copy(saving = pending.isNotEmpty(), error = null) }
            true
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            mutable.update { it.copy(saving = true, error = "未能保存。内容仍在本次会话中，请重试。") }; false
        }
    }
    /** A checkmark also waits for a photo picker import, not just the text debounce. */
    suspend fun finishEditing():Boolean {
        while(imageImports.isNotEmpty()) imageImports.toList().joinAll()
        return flush()
    }
    fun save() { viewModelScope.launch { finishEditing() } }
    private fun structure(action:suspend ()->Unit) { viewModelScope.launch {
        operations.withLock {
            mutable.update { it.copy(working=true) }
            try { if(flush()) { drafts.clear();action() } }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { mutable.update { it.copy(error="未能完成操作，原有内容仍然保留。") } }
            finally { mutable.update { it.copy(working=false) } }
        }
    } }
    fun addPage() = addPage(null)
    fun addPhotoPages(startId:String?,sources:List<String>,journalId:String=state.value.journalId) {
        if(sources.isEmpty()) return
        structure {
            val id=repository.importPhotoPages(journalId,startId,sources)
            requestedJournal=journalId
            mutable.update { it.copy(journalId=journalId,
                pages=it.allPages.filter { p -> p.journalId==journalId && p.deletedAt==0L },pendingPage=id) }
        }
    }
    fun addPage(afterId:String?) = structure {
        val id=repository.addPage(state.value.journalId,afterId);mutable.update { it.copy(pendingPage=id) }
    }
    fun selectJournal(id:String) = structure {
        val journal=state.value.journals.find { it.id==id && it.deletedAt==0L } ?: return@structure
        requestedJournal=id
        mutable.update { it.copy(journalId=id,pages=it.allPages.filter { p -> p.journalId==id && p.deletedAt==0L },pendingPage=journal.lastPageId) }
    }
    /** Shelf selection has no database mutation. Await its state handoff directly
     * instead of enqueueing a structural job then polling for several seconds. */
    suspend fun switchJournal(id:String):Boolean=operations.withLock {
        val journal=state.value.journals.find { it.id==id && it.deletedAt==0L } ?: return@withLock false
        if(!flush()) return@withLock false
        requestedJournal=id
        mutable.update { it.copy(journalId=id,pages=it.allPages.filter { p -> p.journalId==id && p.deletedAt==0L },pendingPage=journal.lastPageId) }
        true
    }
    /** Closed-shelf cursor handoff on the input event, with no disk operation.
     * A dirty or structurally mutating book must use the awaited save path. */
    fun switchShelfNow(id:String):Boolean {
        if(state.value.working || pending.isNotEmpty() || operations.isLocked || writes.isLocked) return false
        val journal=state.value.journals.find { it.id==id && it.deletedAt==0L } ?: return false
        requestedJournal=id
        mutable.update { it.copy(journalId=id,pages=it.allPages.filter { p -> p.journalId==id && p.deletedAt==0L },pendingPage=journal.lastPageId) }
        return true
    }
    fun createJournal(kind:BookKind,afterId:String?=null)=structure {
        val journal=NewBook.journal(kind,state.value.journals.size)
        val id=repository.createJournal(journal,afterId)
        requestedJournal=journal.id
        mutable.update { it.copy(journalId=journal.id,pages=emptyList(),pendingPage=id) }
    }
    fun updateJournal(journal:DiaryJournal)=structure { repository.putJournal(journal) }
    fun trashJournal(id:String)=structure {
        val books=state.value.journals.filter { it.deletedAt==0L }
        val at=books.indexOfFirst { it.id==id };val journal=books.getOrNull(at) ?: return@structure
        val previousRequest=requestedJournal
        requestedJournal=(books.getOrNull(at+1) ?: books.getOrNull(at-1))?.id
        try {
            repository.putJournal(journal.copy(deletedAt=System.currentTimeMillis()))
            mutable.update { it.copy(pendingPage=null) }
        } catch(error:Exception) {
            requestedJournal=previousRequest
            throw error
        }
    }
    suspend fun saveCover(journal:DiaryJournal,source:String?):Boolean=operations.withLock {
        mutable.update { it.copy(working=true) }
        try {
            if(!flush()) return@withLock false
            val path=source?.let { repository.importImage(it) } ?: journal.coverPath
            repository.putJournal(journal.copy(coverPath=path))
            true
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { mutable.update { it.copy(error="封面未能保存，原有封面仍然保留。") };false }
        finally { mutable.update { it.copy(working=false) } }
    }
    fun coverImage(source:String)=structure {
        val path=repository.importImage(source);repository.putJournal(state.value.journal.copy(coverPath=path))
    }
    fun duplicateJournal()=structure {
        val original=state.value.journal
        val journal=original.copy(id=java.util.UUID.randomUUID().toString(),name="${original.name} 副本",order=state.value.journals.size,lastPageId="")
        repository.putJournal(journal)
        val id=repository.duplicate(state.value.pages.map { it.id },journal.id)
        requestedJournal=journal.id
        mutable.update { it.copy(journalId=journal.id,pages=emptyList(),pendingPage=id) }
    }
    fun duplicatePages(ids:List<String>)=structure {
        val id=repository.duplicate(ids,state.value.journalId);mutable.update { it.copy(pendingPage=id) }
    }
    fun movePages(ids:List<String>,journalId:String,beforeId:String?=null)=structure { repository.organize(ids,journalId,beforeId) }
    fun trashPages(ids:List<String>,restore:Boolean=false)=structure { repository.trash(ids,restore) }
    fun shown(id:String) {
        val journal=state.value.journal
        if(journal.lastPageId!=id) viewModelScope.launch { repository.pageShown(journal.id,id) }
    }
    fun backup(destination:String)=structure { repository.backup(destination) }
    fun restore(source:String)=structure { repository.restore(source) }
    fun saveClip(id:String,selected:Set<String>,cut:Boolean=false)=structure {
        val page=state.value.pages.find { it.id==id } ?: return@structure
        val strokes=page.strokes.filter { it.id in selected };if(strokes.isEmpty()) return@structure
        repository.putClip(InkClip(name=page.title.ifBlank { "画布片段" },strokes=strokes))
        if(cut) ink(id,page.strokes.filterNot { it.id in selected })
    }
    fun pasteClip(id:String,clip:InkClip) {
        val page=state.value.pages.find { it.id==id } ?: return
        val copy=clip.strokes.map { it.copy(id=java.util.UUID.randomUUID().toString()) }.shifted(.025f,.025f)
        ink(id,page.strokes+copy)
    }
    fun deleteClip(id:String)=structure { repository.deleteClip(id) }
    fun shiftInk(id:String,selected:Set<String>,dx:Float,dy:Float) {
        val page=state.value.pages.find { it.id==id } ?: return
        val moved=page.strokes.filter { it.id in selected }.shifted(dx,dy).associateBy { it.id }
        ink(id,page.strokes.map { moved[it.id] ?: it })
    }
    fun pageShown() { mutable.update { it.copy(pendingPage = null) } }
}
