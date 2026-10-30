@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.qingyu.personalaios.feature.diary

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.*
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.*
import kotlin.math.*

@Composable fun DiaryScreen(model:DiaryViewModel,exitGate:DiaryExitGate,immersive:Boolean=false,
    onCanvas:(Boolean)->Unit={},onOpaquePaper:(Boolean)->Unit={},onCoverEditing:(Boolean)->Unit={},onImmersive:(Boolean)->Unit={}) {
    val state by model.state.collectAsStateWithLifecycle()
    val environment=checkNotNull(LocalGlassEnvironment.current)
    val bookLayer=rememberGlassBackdrop()
    val paperLayer=rememberGlassBackdrop()
    val bookBackdrop=rememberGlassContentBackdrop(bookLayer)
    val paperBackdrop=rememberGlassContentBackdrop(paperLayer)
    val motion=environment.theme.motionEnabled
    val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
    val currentLandscape by rememberUpdatedState(landscape)
    val currentImmersive by rememberUpdatedState(immersive)
    val changeImmersive by rememberUpdatedState(onImmersive)
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle,model) {
        val observer=LifecycleEventObserver { _,event -> active=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED);if(event==Lifecycle.Event.ON_STOP) model.save() }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer);model.save() }
    }
    val opened=remember { Animatable(0f) };val spread=remember { Animatable(0f) };val rotation=remember { Animatable(0f) }
    val canvas by remember { derivedStateOf { spread.value>0f } }
    val bookIsOpen by remember { derivedStateOf { opened.value>0f } }
    val surfaceEnvironment=environment.copy(backdrop=if(canvas) paperBackdrop else bookBackdrop)
    val closing=remember { Animatable(0f) };var closingBook by remember { mutableStateOf(false) }
    var closeDrag by remember { mutableFloatStateOf(-1f) }
    var librarySlide by remember { mutableFloatStateOf(0f) }
    var libraryDragOrigin by remember { mutableFloatStateOf(0f) }
    var libraryJob by remember { mutableStateOf<Job?>(null) }
    val libraryTail=remember { Animatable(0f) };var libraryRelease by remember { mutableStateOf<BookRelease?>(null) }
    val creation=remember { Animatable(1f) };var creationFrom by remember { mutableStateOf<DiaryJournal?>(null) }
    var creationShelf by remember { mutableStateOf<List<DiaryJournal>>(emptyList()) }
    var creationStarted by remember { mutableStateOf(false) }
    val removal=remember { Animatable(0f) };var removingId by remember { mutableStateOf<String?>(null) }
    var removalFrom by remember { mutableStateOf<DiaryJournal?>(null) }
    var removalShelf by remember { mutableStateOf<List<DiaryJournal>>(emptyList()) }
    val removalTail=remember { Animatable(0f) };var removalRelease by remember { mutableStateOf<BookRelease?>(null) }
    var mutatingPaper by remember { mutableStateOf(false) }
    var paperBeforeAddition by remember { mutableStateOf<List<DiaryPage>?>(null) }
    var libraryIncoming by remember { mutableStateOf<String?>(null) };var libraryRebase by remember { mutableFloatStateOf(0f) }
    val effectiveLibraryOffset=remember { derivedStateOf {
        removalRelease?.offset(removalTail.value) ?:
            ((libraryRelease?.offset(libraryTail.value) ?: librarySlide)-if(state.journalId==libraryIncoming) libraryRebase else 0f)
    } }
    val visibleJournals=remember(state.journals) { state.journals.filter { it.deletedAt==0L } }
    val shelfJournals=if(removalFrom!=null) removalShelf else visibleJournals
    val shelfJournal=removalFrom ?: state.journal
    val selectedJournalIndex=shelfJournals.indexOfFirst { it.id==shelfJournal.id }
    val journalPageCounts=remember(state.allPages) { state.allPages.filter { it.deletedAt==0L }.groupingBy { it.journalId }.eachCount() }
    val pageIds=remember(state.pages) { state.pages.map { it.id } }
    val headingJournal by remember(shelfJournals,shelfJournal,selectedJournalIndex) { derivedStateOf {
        val offset=effectiveLibraryOffset.value
        if(abs(offset)>BookLibraryGeometry.Step*.56f)
            shelfJournals.getOrNull(selectedJournalIndex+if(offset<0) 1 else -1) ?: shelfJournal else shelfJournal
    } }
    val curl=rememberBookTurnState()
    var turnJob by remember { mutableStateOf<Job?>(null) }
    var dragTurnOrigin by remember { mutableFloatStateOf(0f) }
    var bookView by remember { mutableStateOf<BookSceneView?>(null) }
    var preparingBook by remember { mutableStateOf(false) }
    var editorWasImmersive by remember { mutableStateOf(false) }
    val restPaperSource=remember { arrayOfNulls<PaperLiftGeometry.Source>(1) }
    val visiblePaperSource=remember { arrayOfNulls<PaperLiftGeometry.Source>(1) }
    var transitionSource by remember { mutableStateOf<PaperLiftGeometry.Source?>(null) }
    var detachedPaper by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) };var selected by remember { mutableStateOf(false) };var editing by remember { mutableStateOf(false) }
    val selection=if(selected) 1f else 0f
    var modeError by remember { mutableStateOf<String?>(null) };var panel by remember { mutableStateOf("") }
    var coverEditing by remember { mutableStateOf(false) }
    val coverProgress by animateFloatAsState(if(coverEditing) 1f else 0f,tween(if(motion) 440 else 0,easing=LiquidMotion.Navigation),label="cover push")
    val coverVisible=coverEditing || coverProgress>0f
    var nameEditing by remember { mutableStateOf(false) }
    var nameBounds by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(coverEditing) { onCoverEditing(coverEditing);onDispose { if(coverEditing) onCoverEditing(false) } }
    val overview=remember { Animatable(0f) }
    var overviewAnchor by remember { mutableIntStateOf(0) }
    var overviewScroll by remember { mutableFloatStateOf(0f) }
    var overviewVisible by rememberSaveable { mutableStateOf(false) }
    var overviewTop by remember { mutableFloatStateOf(Float.NaN) }
    var overviewOrderIds by remember { mutableStateOf<List<String>?>(null) }
    var overviewDragId by remember { mutableStateOf<String?>(null) }
    var overviewDragOffset by remember { mutableStateOf(Offset.Zero) }
    val overviewPages=remember(state.pages,overviewOrderIds) {
        overviewOrderIds?.let { order -> val byId=state.pages.associateBy { it.id };order.mapNotNull { byId[it] }+state.pages.filter { it.id !in order } } ?: state.pages
    }
    val showingOverview=overviewVisible
    LaunchedEffect(showingOverview) {
        if(motion) overview.animateTo(if(showingOverview) 1f else 0f,tween(BookOverviewGeometry.DurationMillis,easing=LinearEasing))
        else overview.snapTo(if(showingOverview) 1f else 0f)
    }
    var note by remember { mutableStateOf(false) };var erasing by rememberSaveable { mutableStateOf(false) }
    var imageEditing by rememberSaveable { mutableStateOf(false) };var tray by rememberSaveable { mutableStateOf(true) }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    var tool by rememberSaveable { mutableStateOf(InkTool.FOUNTAIN) };var color by rememberSaveable { mutableLongStateOf(0xFF293844) }
    var inkWidth by rememberSaveable { mutableFloatStateOf(PaperBrushPresets.initialWidth(InkTool.FOUNTAIN)) };var hex by remember { mutableStateOf("") }
    var inkOpacity by rememberSaveable { mutableFloatStateOf(1f) }
    var inkPressure by rememberSaveable { mutableFloatStateOf(1f) }
    var inkStraight by rememberSaveable { mutableStateOf(false) }
    var penColors by rememberSaveable { mutableStateOf(LongArray(InkTool.entries.size) { i -> when(InkTool.entries[i]) {
        InkTool.WATERCOLOR -> 0xFFFFC400L;InkTool.MARKER -> 0xFFFA626CL;else -> 0xFF293844L
    } }) }
    // Width, opacity, pressure and straight mode belong to each physical pen.
    var penProfiles by rememberSaveable { mutableStateOf(FloatArray(InkTool.entries.size*4) { i -> when(i%4) {
        0 -> PaperBrushPresets.initialWidth(InkTool.entries[i/4])
        3 -> 0f;else -> 1f
    } }) }
    LaunchedEffect(Unit) {
        if(penColors.size<InkTool.entries.size) penColors=LongArray(InkTool.entries.size) { penColors.getOrElse(it) { 0xFF293844L } }
        if(penProfiles.size<InkTool.entries.size*4) penProfiles=FloatArray(InkTool.entries.size*4) { penProfiles.getOrElse(it) { if(it%4==0) PaperBrushPresets.initialWidth(InkTool.entries[it/4]) else if(it%4==3) 0f else 1f } }
    }
    var eraserSize by rememberSaveable { mutableFloatStateOf(.035f) };var smoothing by rememberSaveable { mutableFloatStateOf(.35f) }
    var cutting by remember { mutableStateOf(false) };var selectedInk by remember(pageId) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedImage by remember { mutableStateOf<String?>(null) }
    fun defaultEditorPen() {
        penColors=penColors.copyOf().also { it[tool.ordinal]=color }
        penProfiles=penProfiles.copyOf().also { values ->
            val base=tool.ordinal*4;values[base]=inkWidth;values[base+1]=inkOpacity;values[base+2]=inkPressure
        }
        tool=InkTool.FELT
        val base=tool.ordinal*4;color=penColors[tool.ordinal];inkWidth=penProfiles[base]
        inkOpacity=penProfiles[base+1];inkPressure=penProfiles[base+2];inkStraight=false
        erasing=false;cutting=false;selectedInk=emptySet();selectedImage=null
    }
    var selectionReset by remember { mutableIntStateOf(0) }
    var editorPaperBounds by remember { mutableStateOf(Rect.Zero) }
    var editorToolbarBounds by remember { mutableStateOf(Rect.Zero) }
    var editorTrayBounds by remember { mutableStateOf(Rect.Zero) }
    val handwritingFlush=remember { arrayOf<()->Unit>({}) }
    var exporting by rememberSaveable { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val easing=remember { CubicBezierEasing(.22f,.85f,.28f,1f) }
    val page=state.pages.getOrNull(curl.current.coerceIn(0,state.pages.lastIndex.coerceAtLeast(0)))
    val imagesOnly=state.journal.kind==BookKind.SCRAPBOOK
    val portraitDiary=state.journal.kind==BookKind.DIARY || page?.singleFace==true
    var editFace by rememberSaveable { mutableStateOf(PaperFace.RIGHT) }
    val face=if(portraitDiary) editFace else PaperFace.RIGHT
    val editorPage=page?.let { DiaryFaces.page(it,face) }
    var editorBaseline by remember { mutableStateOf<DiaryPage?>(null) }
    var pendingInk by remember { mutableStateOf(false) }
    LaunchedEffect(editorPage?.revision) { pendingInk=false }
    var wallpaperMode by remember { mutableStateOf(false) }
    var wallpaperTray by remember { mutableStateOf(false) }
    var wallpaperDraft by remember { mutableStateOf("") }
    var wallpaperWorking by remember { mutableStateOf(false) }
    val wallpaperTravel=remember { Animatable(0f) }
    val wallpaperCurve=remember { CubicBezierEasing(.22f,.85f,.28f,1f) }
    fun beginWallpapers() {
        if(wallpaperMode || wallpaperWorking || editorPage==null) return
        handwritingFlush[0]();selectionReset++;selectedImage=null;imageEditing=false
        wallpaperDraft=editorPage.wallpaper;wallpaperMode=true
        scope.launch { wallpaperTravel.animateTo(1f,tween(320,easing=wallpaperCurve));wallpaperTray=true }
    }
    fun endWallpapers(apply:Boolean) {
        if(!wallpaperMode || wallpaperWorking || !wallpaperTray) return
        scope.launch {
            wallpaperWorking=true
            try {
                if(apply && page!=null) { model.wallpaper(page.id,wallpaperDraft,face);if(!model.flush()) return@launch }
                wallpaperTray=false;delay(320);wallpaperMode=false
                wallpaperTravel.animateTo(0f,spring(dampingRatio=.78f,stiffness=420f))
            } finally { wallpaperWorking=false }
        }
    }
    val wallpaperPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null && wallpaperMode) scope.launch {
            wallpaperWorking=true
            try { wallpaperDraft=model.importPaperWallpaper(uri.toString()) }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { modeError="壁纸未能导入，请重试。" }
            finally { wallpaperWorking=false }
        }
    }
    val shownEditorPage=editorPage?.let { if(wallpaperMode) it.copy(wallpaper=wallpaperDraft) else it }
    val editorChanged=pendingInk || editorPage?.let { current -> editorBaseline?.let { baseline ->
        current.copy(revision=0,leftFace=null)!=baseline.copy(revision=0,leftFace=null)
    } }==true
    val editorSession=PaperEditorSession(wallpaperMode,wallpaperTravel.value,
        if(wallpaperMode) wallpaperDraft!=editorPage?.wallpaper else editorChanged,
        ::beginWallpapers,{ endWallpapers(true) },{ endWallpapers(false) },{ pendingInk=true })
    val previewIdentity=page?.let { it.id+":"+face.name }
    var transitionArtwork by remember { mutableStateOf<ImageBitmap?>(null) }
    // Retain one immutable return image. Reopening an unchanged page must not
    // allocate a full-screen bitmap on every exit and repeatedly trigger GC.
    var returnArtwork by remember { mutableStateOf<ImageBitmap?>(null) }
    var returnArtworkKey by remember { mutableStateOf<Triple<String,Long,Int>?>(null) }
    var editorTextureWidth by remember { mutableIntStateOf(1080) }
    // Prepare the same artwork before the first expanding frame. Loading only once
    // the overlay mounts produces a blank sheet that breaks the material continuity.
    val needsPaperPreview by remember { derivedStateOf { opened.value>0f || preparingBook } }
    val paperPreview by produceState<Triple<String,Long,ImageBitmap>?>(null,previewIdentity,page?.revision,editing,needsPaperPreview) {
        if(page!=null && !editing && needsPaperPreview && (value?.first!=previewIdentity || value?.second!=page.revision)) value=withContext(Dispatchers.IO) {
            Triple(checkNotNull(previewIdentity),page.revision,PageArtwork.bitmap(checkNotNull(editorPage),600,notes=true).asImageBitmap())
        }
    }
    var stickerPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var stickerPhotoFace by rememberSaveable { mutableStateOf(PaperFace.RIGHT) }
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) {
            val target=stickerPhoto ?: page?.id
            if(target!=null) model.addImage(target,uri.toString(),if(stickerPhoto!=null) stickerPhotoFace else PaperFace.RIGHT,asSticker=stickerPhoto!=null)
            imageEditing=true;note=false
        }
        stickerPhoto=null
    }
    var photoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var photoJournal by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPagesPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoPageFill.MaxImages)) { uris ->
        if(uris.isNotEmpty()) model.addPhotoPages(photoTarget,uris.map { it.toString() },photoJournal ?: state.journalId)
        photoTarget=null;photoJournal=null
    }
    val backup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if(uri!=null) model.backup(uri.toString()) }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) model.restore(uri.toString()) }
    val png=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if(uri!=null) scope.launch { try {
            val source=state.allPages.find { it.id==exporting } ?: return@launch
            withContext(Dispatchers.IO) { DiaryExport.png(context,uri,source) };exportError="纸页已导出"
        } catch(_:Exception) { exportError="未能导出，请重新选择保存位置。" } }
    }
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) scope.launch { try {
            if(!model.flush()) return@launch
            val pages=state.pages.toList();withContext(Dispatchers.IO) { DiaryExport.pdf(context,uri,pages) };exportError="书册已导出"
        } catch(_:Exception) { exportError="未能导出，请重新选择保存位置。" } }
    }
    suspend fun move(value:Animatable<Float,AnimationVector1D>,target:Float,duration:Int=760) {
        val opening=value===opened && value.value==0f && target>0f
        if(opening) {
            preparingBook=true
            val ready=withTimeoutOrNull(2500) { while(bookView?.openingFrameReady(state.journal)!=true) delay(16);true } ?: false
            if(!ready) { preparingBook=false;modeError="书本仍在准备，请再点一次。";return }
        }
        try {
            if(motion) {
                // Opening geometry owns the lift/edge-on/spread timing. A second spring
                // here used to rush past the edge-on frame and delay the final spread.
                if(value===opened && target==0f && value.value>0f) {
                    closing.snapTo(0f);closingBook=true
                    try {
                        closing.animateTo(1f,tween(BookClosingGeometry.DurationMillis,easing=LinearEasing))
                        opened.snapTo(0f)
                    } finally { closingBook=false }
                }
                else if(value===opened) value.animateTo(target,tween(BookOpeningGeometry.DurationMillis,easing=LinearEasing))
                else if(value===removal || value===spread) value.animateTo(target,tween(duration,easing=LiquidMotion.Navigation))
                else value.animateTo(target,LiquidMotion.settle(300f))
            } else value.snapTo(target)
        } finally { if(opening) preparingBook=false }
    }
    suspend fun awaitPageArtwork(target:DiaryPage?):Boolean {
        if(target==null) return true
        // Closed shelves no longer rasterize invisible pages. Request them
        // explicitly before awaiting readiness, for all closed-book entrances.
        val requestedWarmup=opened.value==0f && !preparingBook
        if(requestedWarmup) preparingBook=true
        try {
            return withTimeoutOrNull(2500) {
                while(bookView?.pageReady(target)!=true) delay(16)
                true
            } ?: run { modeError="纸页仍在准备，请再点一次。";false }
        } finally { if(requestedWarmup) preparingBook=false }
    }
    suspend fun awaitPresentedArtwork(target:DiaryPage):Boolean = awaitPageArtwork(target) && withTimeoutOrNull(2500) {
        while(bookView?.pageFrameReady(target)!=true) delay(16);true
    }==true
    fun openBook() { if(!busy && state.hasJournal) { busy=true;scope.launch { try {
        if(awaitPageArtwork(page)) move(opened,1f,BookOpeningGeometry.DurationMillis)
    } finally { busy=false } } } }
    fun addPaper() {
        if(busy || state.working || !state.hasJournal) return
        busy=true;mutatingPaper=true;scope.launch { try {
            if(!model.flush()) return@launch
            paperBeforeAddition=state.pages
            val from=page?.id;val before=state.pages.map { it.id }.toSet()
            model.addPage(from)
            val id=withTimeoutOrNull(6000) {
                while(state.pendingPage==null || state.pendingPage in before || state.pages.none { it.id==state.pendingPage }) delay(16)
                state.pendingPage
            } ?: run { modeError="纸页未能添加，请重试。";return@launch }
            val old=state.pages.indexOfFirst { it.id==from };val next=state.pages.indexOfFirst { it.id==id }
            selected=false
            if(old>=0 && next==old+1 && awaitPageArtwork(state.pages.getOrNull(next))) {
                curl.snapTo(old);curl.addedPage(motion) { paperBeforeAddition=null }
            }
            else curl.snapTo(next.coerceAtLeast(0))
            pageId=id;model.pageShown()
        } finally { paperBeforeAddition=null;mutatingPaper=false;busy=false } }
    }
    fun deleteBook() {
        if(busy || state.working || !state.hasJournal) return
        busy=true;scope.launch { try {
            if(!model.flush()) return@launch
            panel="";selected=false
            if(opened.value>0f) move(opened,0f,BookOpeningGeometry.DurationMillis)
            val id=state.journalId
            removalShelf=visibleJournals;removalFrom=state.journal;removingId=id
            val index=removalShelf.indexOfFirst { it.id==id }
            val direction=BookRemovalGeometry.direction(index,removalShelf.size)
            val next=removalShelf.getOrNull(index+direction).takeIf { direction!=0 }
            move(removal,1f,BookRemovalGeometry.ExitMillis)
            if(next!=null) {
                val ready=withTimeoutOrNull(2500) { while(bookView?.coverReady(next)!=true) delay(16);true } ?: false
                if(!ready) { modeError="书册仍在准备，请重试。";move(removal,0f);return@launch }
            }
            // Commit once the departed cover is hidden and the next is ready;
            // the frozen shelf keeps Room's new cursor from teleporting it.
            model.trashJournal(id)
            if(next!=null) {
                removalTail.snapTo(0f)
                removalRelease=BookCarouselMotion.release(0f,direction,0f,true)
                if(motion) removalTail.animateTo(1f,tween(BookRemovalGeometry.ReplacementMillis,easing=LinearEasing)) else removalTail.snapTo(1f)
            }
            // Keep the original shelf while Room publishes the deletion and
            // selection. The completed motion already equals the new shelf.
            val committed=withTimeoutOrNull(6000) {
                while(state.journals.any { it.id==id && it.deletedAt==0L } || (next!=null && state.journalId!=next.id)) {
                    if(state.error!=null && !state.working) return@withTimeoutOrNull false
                    delay(16)
                }
                true
            } ?: false
            if(!committed) {
                modeError="书册未能删除，请重试。"
                if(state.journals.any { it.id==id && it.deletedAt==0L }) {
                    if(motion && removalRelease!=null) removalTail.animateTo(0f,tween(260,easing=LiquidMotion.Navigation))
                    else removalTail.snapTo(0f)
                    move(removal,0f)
                }
            }
            curl.snapTo(0);pageId=null
        } finally { removalFrom=null;removalShelf=emptyList();removalRelease=null;removalTail.snapTo(0f);removingId=null;removal.snapTo(0f);busy=false } }
    }
    fun deletePaper() {
        if(busy || state.working || page==null) return
        busy=true;mutatingPaper=true;scope.launch { try {
            if(!model.flush()) return@launch
            val id=page.id;val next=state.pages.getOrNull(curl.current+1) ?: state.pages.getOrNull(curl.current-1)
            selected=false
            if(next!=null) { curl.direction=if(state.pages.indexOf(next)>curl.current) 1 else -1;curl.finish(true,motion,360) }
            model.trashPages(listOf(id))
            withTimeoutOrNull(6000) { while(state.pages.any { it.id==id }) delay(16) }
            pageId=next?.id;curl.snapTo(state.pages.indexOfFirst { it.id==pageId }.coerceAtLeast(0))
        } finally { mutatingPaper=false;busy=false } }
    }
    fun createBook(kind:BookKind) {
        if(busy || state.working) return
        busy=true;scope.launch { try {
            if(!model.flush()) return@launch
            val previous=state.journal;val before=state.journals.map { it.id }.toSet()
            focus.clearFocus();keyboard?.hide()
            creationShelf=visibleJournals;creationStarted=false
            creation.snapTo(0f);creationFrom=previous
            model.createJournal(kind,previous.id)
            val ready=withTimeoutOrNull(6000) {
                while(state.journalId in before || state.pages.isEmpty() || state.journal.id!=state.journalId) delay(16)
                true
            } ?: false
            if(!ready) { modeError="书册未能准备好，请重试。";return@launch }
            curl.snapTo(0);selected=false
            withTimeoutOrNull(2500) { while(bookView?.coverReady(state.journal)!=true) delay(16) }
            creationStarted=true
            if(motion) creation.animateTo(1f,tween(BookCarouselMotion.CreationMillis,easing=LinearEasing)) else creation.snapTo(1f)
        } finally { creationFrom=null;creationStarted=false;creationShelf=emptyList();creation.snapTo(1f);busy=false } }
    }
    suspend fun settleLibraryOffset(direction:Int,velocity:Float,commit:Boolean) {
        if(!motion) { librarySlide=if(commit) -direction*BookCarouselMotion.Step else 0f;return }
        val release=BookCarouselMotion.release(librarySlide,direction,velocity,commit)
        libraryTail.snapTo(0f);libraryRelease=release
        try {
            libraryTail.animateTo(1f,tween(release.duration,easing=LinearEasing))
            librarySlide=release.targetOffset
        } finally { if(libraryRelease===release) libraryRelease=null }
    }
    suspend fun settleJournal(direction:Int,velocity:Float=0f) {
        val live=model.state.value
        val journals=live.journals.filter { it.deletedAt==0L };val index=journals.indexOfFirst { it.id==live.journalId }
        val next=journals.getOrNull(index+direction)
        if(next==null) { settleLibraryOffset(direction,velocity,false);return }
        settleLibraryOffset(direction,velocity,true)
        // Keep the spring interruptible until the Room selection handoff. The
        // endpoint and the new journal's zero coordinate describe the same pose.
        libraryRebase=-direction*BookLibraryGeometry.Step;libraryIncoming=next.id
        try {
            if(model.switchShelfNow(next.id) || model.switchJournal(next.id)) {
                curl.snapTo(0);pageId=null
            }
        } finally { librarySlide=0f;libraryIncoming=null;libraryRebase=0f }
    }
    fun launchLibrary(action:suspend ()->Unit) {
        val previous=libraryJob
        val next=scope.launch(start=CoroutineStart.LAZY) { try { action() } finally {
            if(libraryJob===coroutineContext[Job]) { libraryJob=null;busy=false }
        } }
        libraryJob=next;previous?.cancel();next.start()
    }
    fun beginLibraryDrag() {
        librarySlide=libraryRelease?.offset(libraryTail.value) ?: librarySlide
        libraryRelease=null;libraryJob?.cancel();libraryJob=null
        libraryDragOrigin=librarySlide
        bookView?.trackLibrary(librarySlide)
    }
    suspend fun prepareEditor():Boolean {
        editorWasImmersive=currentImmersive
        focus.clearFocus();keyboard?.hide()
        if(portraitDiary) {
            if(currentLandscape) {
                changeImmersive(false)
                val changed=withTimeoutOrNull(4000) { while(currentLandscape) delay(16);true } ?: false
                if(!changed) { modeError="暂时无法切换竖屏编辑。";return false }
            }
            return true
        }
        transitionSource=restPaperSource[0]
        if(!currentLandscape) {
            // Detach the leaf while the book stays still. Retain its actual
            // intermediate quad across configuration, then rotate only paper.
            move(spread,.24f,160)
            withFrameNanos { };withFrameNanos { }
            transitionSource=visiblePaperSource[0] ?: transitionSource
            detachedPaper=true;spread.snapTo(.001f)
            changeImmersive(true)
            val changed=withTimeoutOrNull(4000) { while(!currentLandscape) delay(16);true } ?: false
            if(!changed) { spread.snapTo(0f);detachedPaper=false;transitionSource=null;changeImmersive(editorWasImmersive);modeError="暂时无法切换编辑方向。";return false }
        }
        // Configuration arrives before the resized Vulkan image is presented.
        // Start the lift only after layout and the new viewport agree.
        repeat(2) { withFrameNanos {} }
        val settled=withTimeoutOrNull(2500) { while(bookView?.viewportFrameReady()!=true) delay(16);true } ?: false
        if(!settled) { spread.snapTo(0f);detachedPaper=false;transitionSource=null;modeError="纸页仍在准备，请再点一次。";return false }
        return true
    }
    fun expand() { if(!busy && opened.value==1f && page!=null && curl.progress==0f) {
        busy=true;scope.launch { try {
            val prepared=withTimeoutOrNull(2000) {
                while(paperPreview?.first!=page.id+":"+(if(portraitDiary) editFace else PaperFace.RIGHT).name || paperPreview?.second!=page.revision) delay(16)
                true
            } ?: false
            if(!prepared) { modeError="纸页仍在准备，请再点一次。";return@launch }
            transitionArtwork=paperPreview?.third
            selected=true
            if(!prepareEditor()) return@launch
            model.resetEditingHistory(page.id,face);editorBaseline=DiaryFaces.page(page,face);pendingInk=false
            defaultEditorPen();note=false;imageEditing=false;move(spread,1f,540);editing=true
        } finally { busy=false } }
    } }
    fun closePaper() { if(!busy) { handwritingFlush[0]();busy=true;scope.launch { try {
        focus.clearFocus();keyboard?.hide()
        transitionSource=null;detachedPaper=false
        // Save and freeze in parallel. The live editor never changes size while
        // rasterizing; one uploaded texture owns every shrinking frame.
        coroutineScope {
            val saved=async { model.finishEditing() }
            val latest=model.state.value.pages.find { it.id==page?.id } ?: return@coroutineScope
            val key=Triple(latest.id+":"+face.name,latest.revision,editorTextureWidth)
            transitionArtwork=returnArtwork?.takeIf { returnArtworkKey==key } ?: withContext(Dispatchers.Default) {
                PageArtwork.bitmap(DiaryFaces.page(latest,face),editorTextureWidth,notes=true).asImageBitmap()
            }.also { returnArtwork=it;returnArtworkKey=key }
            editing=false
            val ready=async { awaitPresentedArtwork(latest) }
            move(spread,.001f,440)
            val persisted=saved.await()
            val finalPage=model.state.value.pages.find { it.id==latest.id } ?: latest
            if(!persisted || !ready.await() || (finalPage.revision!=latest.revision && !awaitPresentedArtwork(finalPage))) {
                move(spread,1f,260);editing=true;return@coroutineScope
            }
            move(spread,0f,20)
            model.resetEditingHistory(latest.id,face)
            note=false
            // The native leaf is already in this exact selected pose. Clearing
            // it here teleported the sheet on the final handoff frame.
            if(portraitDiary) changeImmersive(editorWasImmersive) else if(!editorWasImmersive) changeImmersive(false)
        }
    } finally { busy=false } } } }
    fun closeBook() { if(!busy && curl.progress==0f) { busy=true;scope.launch { try { selected=false;move(opened,0f,BookOpeningGeometry.DurationMillis) } finally { busy=false } } } }
    fun launchTurn(action:suspend ()->Unit) {
        busy=true
        turnJob=scope.launch { try { action() } finally {
            // An interrupted spring must not unlock the replacement gesture.
            if(turnJob===currentCoroutineContext()[Job]) { turnJob=null;busy=false }
        } }
    }
    fun finishCloseGesture(completed:Boolean) {
        if(closeDrag<0f) return
        val from=closeDrag
        val commit=completed && BookGestureMotion.commitClose(from)
        scope.launch { try {
            closing.snapTo(from);closeDrag=-1f
            if(motion) closing.animateTo(if(commit) 1f else 0f,
                tween((BookClosingGeometry.DurationMillis*abs((if(commit) 1f else 0f)-from)).roundToInt().coerceAtLeast(90),easing=LinearEasing))
            if(commit) opened.snapTo(0f)
        } finally { closeDrag=-1f;closingBook=false;busy=false } }
    }
    fun showOverview() {
        if(busy || curl.progress!=0f || !state.hasJournal) return
        busy=true;scope.launch { try {
            if(!model.flush() || !awaitPageArtwork(page)) return@launch
            selected=false
            if(opened.value==0f) move(opened,1f)
            if(opened.value==1f) { overviewAnchor=curl.current;overviewScroll=0f;overviewVisible=true;panel="grid" }
        } finally { busy=false } }
    }
    fun returnOverview(id:String?=null,after:()->Unit={}) {
        if(busy || overview.value!=1f) return
        busy=true;scope.launch { try {
            if(!model.flush()) return@launch
            if(id!=null) {
                val index=state.pages.indexOfFirst { it.id==id }
                if(index<0) return@launch
                // At reveal=1 grid slots depend on the retained anchor, not the
                // newly selected book cursor. This prevents a pre-return jump.
                curl.snapTo(index);pageId=id
                if(!awaitPageArtwork(state.pages[index])) return@launch
            }
            selected=false;overviewVisible=false;panel=""
            while(overview.value>0f) delay(16)
        } finally { busy=false } 
            after()
        }
    }
    fun editPaper(text:Boolean=false,photo:Boolean=false) {
        if(busy || page==null || curl.progress!=0f || (text && imagesOnly)) return
        panel="";busy=true
        scope.launch { try {
            if(!model.flush() || !awaitPageArtwork(page)) return@launch
            if(opened.value==0f) move(opened,1f)
            if(opened.value!=1f) return@launch
            val ready=withTimeoutOrNull(2500) {
                while(paperPreview?.first!=page.id+":"+(if(portraitDiary) editFace else PaperFace.RIGHT).name || paperPreview?.second!=page.revision) delay(16)
                true
            } ?: false
            if(!ready) { modeError="纸页仍在准备，请再点一次。";return@launch }
            transitionArtwork=paperPreview?.third
            selected=true;focus.clearFocus();keyboard?.hide()
            if(!prepareEditor()) return@launch
            model.resetEditingHistory(page.id,face);editorBaseline=DiaryFaces.page(page,face);pendingInk=false
            defaultEditorPen();move(spread,1f,540);editing=true;note=false;imageEditing=photo
            if(photo) imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } finally { busy=false } }
    }
    fun immersiveMode(target:Boolean) {
        if(busy || curl.progress!=0f || (target && !state.hasJournal)) return
        busy=true;scope.launch { try {
            modeError=null;focus.clearFocus();keyboard?.hide();if(!model.flush()) return@launch
            editing=false;note=false;if(spread.value>0f) move(spread,0f);selected=false
            if(target && opened.value==0f) move(opened,1f)
            if(motion) rotation.animateTo(if(target) -90f else 90f,tween(420,easing=easing))
            changeImmersive(target)
            val changed=withTimeoutOrNull(4000) { while(currentLandscape!=target) delay(16);true } ?: false
            rotation.snapTo(0f);if(!changed) { changeImmersive(false);modeError="暂时无法切换屏幕方向。" }
        } finally { busy=false } }
    }
    val editCurrent by rememberUpdatedState<(Boolean,Boolean)->Unit>({ text,photo -> editPaper(text,photo) })
    // Persist pending edits without resetting the scene during the tab's slide-out.
    val leave by rememberUpdatedState<suspend ()->Boolean>({
          handwritingFlush[0]()
        focus.clearFocus();keyboard?.hide()
        // Do not close or rebuild the departing scene. The entire current book/paper
        // leaves with the tab; closing here exposed neighbouring covers before sliding.
        model.finishEditing()
    })
    DisposableEffect(exitGate) { val handler:suspend ()->Boolean={ leave() };exitGate.handler=handler;onDispose { if(exitGate.handler===handler) exitGate.handler=null } }
    DisposableEffect(canvas) { onCanvas(canvas);onDispose { if(canvas) onCanvas(false) } }
    // Freeze only the fully opaque editor, never the opening/return transition.
    val opaquePaper by remember { derivedStateOf { editing && spread.value==1f } }
    DisposableEffect(opaquePaper) { onOpaquePaper(opaquePaper);onDispose { if(opaquePaper) onOpaquePaper(false) } }
    BackHandler(bookIsOpen || immersive) { when {
        wallpaperMode -> endWallpapers(false)
        showingOverview -> if(panel!="grid" && panel.isNotEmpty()) panel="grid" else returnOverview()
        panel.isNotEmpty() -> panel="";busy -> Unit;note -> { note=false;focus.clearFocus();keyboard?.hide() }
        spread.value>0f -> closePaper();selected -> selected=false;immersive -> immersiveMode(false);else -> closeBook()
    } }
    LaunchedEffect(pageIds,state.pendingPage,state.journalId) {
        if(mutatingPaper) return@LaunchedEffect
        val pendingIndex=state.pages.indexOfFirst { it.id==state.pendingPage }
        val previousIndex=state.pages.indexOfFirst { it.id==pageId }
        val next=when { pendingIndex>=0 -> pendingIndex;previousIndex>=0 -> previousIndex;else -> curl.current.coerceIn(0,state.pages.lastIndex.coerceAtLeast(0)) }
        curl.snapTo(next);pageId=state.pages.getOrNull(next)?.id
        if(pendingIndex>=0) { model.pageShown();selected=false }
    }
    LaunchedEffect(curl.current,page?.id) { pageId=state.pages.getOrNull(curl.current)?.id;pageId?.let { model.shown(it) } }
    fun turn(forward:Boolean) {
        if(busy || curl.progress!=0f) return
        val next=curl.current+if(forward) 1 else -1
        if(next !in state.pages.indices) return
        selected=false;launchTurn { if(!motion) curl.snapTo(next) else if(forward) curl.next() else curl.prev() }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().then(if(canvas && !portraitDiary) Modifier.imePadding() else Modifier)
        .pointerInput(editing) {
            if(editing) awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                if(!editorPaperBounds.contains(rootOrigin+down.position) && !editorToolbarBounds.contains(rootOrigin+down.position) && !editorTrayBounds.contains(rootOrigin+down.position)) { selectionReset++;selectedImage=null;if(panel!="stickers") imageEditing=false }
                waitForUpOrCancellation(pass=PointerEventPass.Initial)
            }
        }
        .onGloballyPositioned { rootOrigin=it.positionInRoot() }
        .pointerInput(nameEditing,nameBounds) {
            if(nameEditing) awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                if(!nameBounds.contains(rootOrigin+down.position)) { focus.clearFocus();keyboard?.hide() }
                waitForUpOrCancellation(pass=PointerEventPass.Initial)
            }
        }) {
        val bottom=LocalPageBottomInset.current
        val available=(maxHeight-bottom).coerceAtLeast(180.dp)
        val sceneHeight=maxHeight
        val sceneWidth=maxWidth
        val sceneTop=0.dp
        val density=LocalDensity.current
        val statusInset=if(immersive) 0.dp else with(density) { WindowInsets.systemBars.union(WindowInsets.displayCutout).getTop(this).toDp() }
        val systemBottom=if(immersive) 0.dp else with(density) { WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(this).toDp() }
        val fullHeight=maxHeight+statusInset+systemBottom
        val closedCentreY=if(landscape) available*.5f else fullHeight*.513f-statusInset
        val openCentreY=if(landscape) available*.5f else fullHeight*AccordionGeometry.ScreenCentre-statusInset
        val closingClock=if(closeDrag>=0f) closeDrag else closing.value
        val unfold=spread.value;val progress=if(closingBook) BookClosingGeometry.opening(closingClock) else opened.value
        val lift=PaperLiftGeometry.pose(unfold,landscape && !portraitDiary,editorPage?.canvasAspect ?: 1f)
        // Expand only the paper rectangle at its physical aspect, without
        // painting a viewport-sized white surface above or below it.
        val leftFace=portraitDiary && editFace==PaperFace.LEFT
        val (sourceLeft,sourceRight)=remember(portraitDiary,leftFace,maxWidth,sceneHeight,landscape,openCentreY) {
            BookGeometry.project(if(portraitDiary && !leftFace) 0f else -BookGeometry.HalfWidth,BookGeometry.HalfHeight,if(portraitDiary && !leftFace) 1 else -1,0,true,maxWidth.value,sceneHeight.value,!landscape,openCentreY.value) to
                BookGeometry.project(if(leftFace) 0f else BookGeometry.HalfWidth,-BookGeometry.HalfHeight,if(leftFace) -1 else 1,0,true,maxWidth.value,sceneHeight.value,!landscape,openCentreY.value)
        }
        val sourceW=(sourceRight.first-sourceLeft.first).dp
        val sourceH=(sourceRight.second-sourceLeft.second).dp
        val sourcePaper=PaperRect(sourceLeft.first,sourceLeft.second,sourceW.value,sourceH.value)
        val flatPaper=if(portraitDiary) BookPaperLayout.fixedWriting(maxWidth.value,maxHeight.value,editorPage?.canvasAspect ?: NewBook.FaceAspect,
            top=if(immersive) with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp().value }+66f else 66f,
            bottom=104f)
            else if(landscape) BookPaperLayout.editor(maxWidth.value,maxHeight.value,page?.canvasAspect ?: 1f)
            else BookPaperLayout.flattened(sourcePaper,maxWidth.value,maxHeight.value,page?.canvasAspect ?: 1f)
        SideEffect { editorTextureWidth=with(density) { flatPaper.width.dp.toPx().roundToInt().coerceIn(320,1600) } }
        val paperRect=BookPaperLayout.interpolate(sourcePaper,flatPaper,unfold)
        val restQuad=if(portraitDiary) BookPaperProjection.face(curl.current,face,sceneWidth.value,sceneHeight.value,openCentreY.value)
            else BookQuad(floatArrayOf(sourcePaper.left,sourcePaper.top,sourcePaper.left+sourcePaper.width,sourcePaper.top,
                sourcePaper.left+sourcePaper.width,sourcePaper.top+sourcePaper.height,sourcePaper.left,sourcePaper.top+sourcePaper.height))
        val projectedPaper=if(portraitDiary) BookPaperProjection.interpolate(restQuad,flatPaper,unfold)
            else PaperLiftGeometry.project(transitionSource?.viewport(sceneWidth.value,sceneHeight.value,landscape) ?: restQuad,
                PaperLiftGeometry.flat(flatPaper,if(landscape) BookPaperLayout.editorRotation(editorPage?.canvasAspect ?: 1f,1f) else 0f),unfold)
        SideEffect {
            if(unfold==0f) restPaperSource[0]=PaperLiftGeometry.Source(restQuad,sceneWidth.value,sceneHeight.value,landscape)
            visiblePaperSource[0]=PaperLiftGeometry.Source(projectedPaper,sceneWidth.value,sceneHeight.value,landscape)
        }
        val pw=paperRect.width.dp;val ph=paperRect.height.dp
        val centreY=paperRect.centreY.dp;val centreX=paperRect.centreX.dp
        if(unfold==0f && !coverVisible) {
            LiquidGlassEnvironment(surfaceEnvironment) {
            Column(Modifier.align(Alignment.TopCenter).padding(top=if(immersive) 12.dp else if(landscape) available*.13f else fullHeight*.142f-statusInset).graphicsLayer { alpha=BookLibraryGeometry.controlsAlpha(effectiveLibraryOffset.value,if(creationFrom!=null) creation.value else null) }.onGloballyPositioned { overviewTop=it.boundsInParent().bottom+with(density) { 18.dp.toPx() } },horizontalAlignment=Alignment.CenterHorizontally) {
                // Keep the previous name and page count together until the new book takes over.
                val heading=creationFrom?.takeIf { creation.value<.5f }?.name ?: if(state.hasJournal) headingJournal.name else "新建你的日记"
                Box {
                    InlineGlassName(headingJournal.id,heading,enabled=!busy && state.hasJournal,onEditing={ nameEditing=it },onBounds={ nameBounds=it }) { value ->
                        if(value!=state.journal.name) model.updateJournal(state.journal.copy(name=value))
                    }
                    FestivalNameAccent(Modifier.matchParentSize())
                }
                val headingPageCount=journalPageCounts[(creationFrom?.takeIf { creation.value<.5f } ?: headingJournal).id] ?: 0
                Spacer(Modifier.height(8.dp));FestivalJournalCaption(if(showingOverview) "${state.pages.size} 页" else if(progress==0f || state.pages.isEmpty()) "$headingPageCount 页" else "${curl.current+1} / ${state.pages.size}")
            }
            if(!showingOverview && overview.value==0f) {
            Row(Modifier.align(Alignment.TopStart).padding(start=12.dp,top=if(immersive) 8.dp else 18.dp)) {
                DiaryIcon("展开书本",R.drawable.paper_book_open) { if(progress==0f) openBook() }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(end=12.dp,top=if(immersive) 8.dp else 18.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                DiaryIcon("所有纸页",R.drawable.paper_grid) { if(state.hasJournal) showOverview() else if(!busy) panel="library" }
            }
            }
            }
        }
        // Keep the artwork cache alive behind the expanded paper for a continuous return.
        if(shelfJournals.isNotEmpty() || creationFrom!=null) Box(Modifier.offset(y=sceneTop).fillMaxWidth().height(sceneHeight).zIndex(-1f).graphicsLayer {
            alpha=if(state.hasJournal || creationFrom!=null) (if(portraitDiary) 1-unfold else if(detachedPaper) 0f else 1-(unfold/.24f).coerceIn(0f,1f))*(1-coverProgress) else 0f;translationX=-size.width*.28f*coverProgress
        }.captureGlassBackdrop(bookLayer)
            .then(if(unfold==0f && panel!="grid" && overview.value==0f && !coverEditing) Modifier.semantics {
                contentDescription=if(progress==0f) "打开日记本" else "第 ${curl.current+1} 页，点击编辑纸页"
                if(progress==1f && page!=null) text=AnnotatedString("${page.title.ifBlank { "今日留白" }}\n${page.body}")
                onClick { if(progress==0f) openBook() else expand();true }
                if(progress==1f) { customActions=listOf(CustomAccessibilityAction("上一页") { turn(false);true },CustomAccessibilityAction("下一页") { turn(true);true }) }
            } else Modifier.clearAndSetSemantics { }) ) {
            AndroidView(factory={ BookSceneView(it).also { view -> bookView=view } },onRelease={ if(bookView===it) bookView=null;it.release() },modifier=Modifier.fillMaxSize().drawWithContent {
                val cut=if(overviewTop.isFinite()) overviewTop*overview.value else 0f
                clipRect(top=cut,bottom=size.height-(size.height*.16f*overview.value)) { this@drawWithContent.drawContent() }
            },update={ view ->
            // Input rebases the coordinate and ViewModel cursor in the same UI
            // event. Flow collection/recomposition can arrive a frame later.
            // Pair that coordinate with the authoritative cursor, never the old
            // shelfJournal captured by this AndroidView update lambda.
            val observedState=state
            val sceneState=model.state.value
            val sceneJournal=removalFrom ?: sceneState.journal
            val sceneJournals=if(removalFrom!=null) removalShelf else
                if(sceneState.journals===observedState.journals) visibleJournals else sceneState.journals.filter { it.deletedAt==0L }
            val liveScene=BookScene(if(overview.value>0f) overviewPages else paperBeforeAddition ?: sceneState.pages,curl.current,progress,curl.progress,curl.direction,selected,motion && active && unfold==0f && overview.value==0f,!landscape,rotation.value,sceneJournal,sceneJournals,effectiveLibraryOffset.value,selection,creationFrom,if(motion) creation.value else 1f,removingId,removal.value,with(density) { closedCentreY.toPx() },with(density) { openCentreY.toPx() },preparingBook,
                removalRelease?.recoil(removalTail.value) ?: libraryRelease?.recoil(libraryTail.value) ?: 0f,
                pendingPage=if(paperBeforeAddition!=null) state.pages.firstOrNull { it.id==state.pendingPage } else null,
                additionReveal=curl.reveal,closing=if(closingBook) closingClock else -1f,
                overview=overview.value,overviewAnchor=overviewAnchor,overviewScroll=overviewScroll,overviewTop=overviewTop,
                overviewDragId=overviewDragId,overviewDragX=overviewDragOffset.x,overviewDragY=overviewDragOffset.y)
            // Room emits journal/page updates separately. Keep the old rendered shelf
            // stable until the new journal and GPU cover have both finished preparing.
            val renderedScene=if(creationFrom!=null && !creationStarted) liveScene.copy(journal=creationFrom!!,
                journals=creationShelf,creationFrom=null,creationProgress=1f,pendingJournal=state.journal) else liveScene
                view.tap={ index -> if(unfold==0f && !busy && !coverEditing && overview.value==0f && !showingOverview) {
                    if(progress==0f && index!=null) {
                        val current=state.journals.filter { it.deletedAt==0L }.indexOfFirst { it.id==state.journalId }
                        if(index==current) openBook() else { busy=true;launchLibrary { settleJournal(if(index>current) 1 else -1) } }
                    } else if(progress==1f && index!=null) {
                        if(index==curl.current) { selectedImage=null;selectedInk=emptySet();editFace=if(view.tappedSide<0) PaperFace.LEFT else PaperFace.RIGHT;expand() } else { busy=true;selected=false;scope.launch { try {
                            if(!motion) curl.snapTo(index) else while(curl.current!=index) { if(index>curl.current) curl.next() else curl.prev() }
                            selected=true
                        } finally { busy=false } } }
                    }
                } }
                view.grabShelf={
                    if(progress==0f && unfold==0f && libraryJob!=null && libraryRelease!=null && libraryIncoming==null &&
                        !state.working && !coverEditing && overview.value==0f && !showingOverview) {
                        beginLibraryDrag();selected=false;busy=true;true
                    } else false
                }
                view.dragStart={
                    val regrab=progress==1f && turnJob!=null && curl.settling && !curl.adding
                    val shelfRegrab=progress==0f && libraryJob!=null && libraryRelease!=null && libraryIncoming==null
                    if(unfold==0f && (!busy || regrab || shelfRegrab) && !state.working && !coverEditing && overview.value==0f && !showingOverview) {
                        if(progress==1f) {
                            curl.beginDrag();dragTurnOrigin=curl.progress*curl.direction
                            turnJob?.cancel();turnJob=null
                        } else {
                            beginLibraryDrag()
                        }
                        selected=false;busy=true;true
                    } else false
                }
                view.dragMove={ offset ->
                    if(progress==1f) {
                        val signed=BookGestureMotion.turn(dragTurnOrigin,offset)
                        val direction=if(signed>=0) 1 else -1
                        curl.direction=direction
                        val amount=if(curl.current+direction in state.pages.indices) abs(signed).coerceIn(0f,1f) else 0f
                        curl.dragTo(amount);view.trackTurn(amount,direction)
                    } else {
                        var tracked=libraryDragOrigin+offset*view.carouselCoordinateScale
                        var live=model.state.value
                        var index=visibleJournals.indexOfFirst { it.id==live.journalId }
                        // Crossing a full slot rebases the same rigid shelf while
                        // the finger is still down. Repeated fast swipes no longer
                        // hit the old +/-1.15-slot wall and restart that same book.
                        while(abs(tracked)>=BookCarouselMotion.Step) {
                            val direction=if(tracked<0) 1 else -1
                            val next=visibleJournals.getOrNull(index+direction) ?: break
                            if(!model.switchShelfNow(next.id)) break
                            libraryDragOrigin+=direction*BookCarouselMotion.Step
                            tracked+=direction*BookCarouselMotion.Step
                            live=model.state.value;index+=direction;pageId=null
                        }
                        val direction=if(tracked<0) 1 else -1
                        val valid=index+direction in visibleJournals.indices
                        librarySlide=if(valid) tracked.coerceIn(-BookCarouselMotion.Step*1.15f,BookCarouselMotion.Step*1.15f)
                            else tracked.coerceIn(-BookCarouselMotion.Step*.35f,BookCarouselMotion.Step*.35f)*.18f
                        view.trackLibrary(librarySlide,live.journal,live.pages)
                    }
                }
                view.dragFinish={ completed ->
                    if(progress==1f) launchTurn {
                        val velocity=if(completed) -curl.direction*view.dragVelocity*BookGestureMotion.TurnGain else 0f
                        curl.finish(completed && curl.current+curl.direction in state.pages.indices && BookGestureMotion.commits(curl.progress,velocity),motion,velocity=velocity)
                    } else launchLibrary {
                        val direction=if(librarySlide<0) 1 else -1
                        val velocity=if(completed) view.dragVelocity*view.carouselCoordinateScale else 0f
                        if(completed && BookCarouselMotion.commits(librarySlide,velocity)) settleJournal(direction,velocity)
                        else settleLibraryOffset(direction,velocity,false)
                    }
                }
                view.pinchTrack={ scale ->
                    if(unfold==0f && !coverEditing && overview.value==0f && !showingOverview && opened.value==1f && curl.progress==0f && (!busy || closeDrag>=0f)) {
                        if(closeDrag<0f) { selected=false;busy=true;closingBook=true }
                        closeDrag=BookGestureMotion.closing(scale);view.trackClosing(closeDrag);true
                    } else false
                }
                view.pinchFinish={ finishCloseGesture(it) }
                view.pinch={ expanding -> if(!busy && overview.value==0f && !showingOverview) { if(expanding && progress==1f) { selected=true;expand() } else if(expanding && progress==0f) openBook() else if(!expanding && progress==1f) closeBook() } }
                // One retained Vulkan surface owns shelf, carousel, opening and leaves.
                if(editing && unfold==1f) view.setRenderingEnabled(false)
                view.update(renderedScene)
                if(!editing || unfold<1f) view.setRenderingEnabled(true)
            })
        }
        if(coverVisible) {
            val editScene=BookScene(journal=state.journal,journals=visibleJournals,portrait=!landscape,
                closedCentreY=with(density) { closedCentreY.toPx() },openCentreY=with(density) { openCentreY.toPx() })
            val rect=ClosedBookGeometry.rect(0,with(density) { maxWidth.toPx() },with(density) { sceneHeight.toPx() },editScene)
            Box(Modifier.fillMaxSize().graphicsLayer { translationX=size.width*(1-coverProgress) }) {
            BookCoverEditor(state.journal,rect,bottom,onCancel={ coverEditing=false },onConfirm={ journal,source ->
                val saved=model.saveCover(journal,source)
                if(saved) withTimeoutOrNull(3000) {
                    while(state.journal.bandColor!=journal.bandColor || (source!=null && state.journal.coverPath==journal.coverPath)) delay(16)
                    while(bookView?.coverReady(state.journal)!=true) delay(16)
                }
                saved
            })
            }
        }
        if(unfold>0f) Box(Modifier.fillMaxSize().captureGlassBackdrop(paperLayer)) {
        Box(Modifier.fillMaxSize().background(PaperColor.copy(alpha=if(portraitDiary) lift.backdrop else if(detachedPaper) 1f else (unfold/.24f).coerceIn(0f,1f))))
        val snapshot=transitionArtwork ?: paperPreview?.takeIf { it.first==previewIdentity }?.third
        if(!editing && snapshot!=null && unfold>0f) {
            PaperLiftSnapshot(snapshot,projectedPaper,Modifier.fillMaxSize())
        } else if(shownEditorPage!=null && unfold>0f) CompositionLocalProvider(LocalPaperEditorSession provides editorSession) {
        Box(Modifier.offset(flatPaper.left.dp,flatPaper.top.dp).onGloballyPositioned { editorPaperBounds=it.boundsInRoot() }
            .wrapContentSize(Alignment.TopStart,unbounded=true).requiredSize(flatPaper.width.dp,flatPaper.height.dp).graphicsLayer {
            translationX=(paperRect.centreX-flatPaper.centreX).dp.toPx()
            translationY=(paperRect.centreY-flatPaper.centreY).dp.toPx()
            scaleX=paperRect.width/flatPaper.width;scaleY=paperRect.height/flatPaper.height
            rotationX=lift.pitch;rotationZ=lift.roll
            shadowElevation=8.dp.toPx();shape=AppShapes.Paper;clip=true
        }.semantics { contentDescription="编辑纸张";stateDescription=if(portraitDiary) "${if(editFace==PaperFace.LEFT) "左" else "右"}面横线日记，竖屏编辑" else "中央虚线辅助线" }) {
            DiaryPaper(shownEditorPage,unfold,editing,drawing=!note && !busy && !wallpaperMode,erasing=erasing,inkColor=PaperPenDynamics.color(color,inkOpacity),inkWidth=inkWidth,imageEditing=(if(portraitDiary) selectedImage!=null || panel=="stickers" else imageEditing) && !busy,
                model=model,modifier=Modifier.fillMaxSize(),tool=tool,noteVisible=note,cutting=cutting,selectedInk=selectedInk,
                onSelectInk={ selectedInk=it },onSelectImage={ selectedImage=it;if(it==null && panel!="stickers") imageEditing=false },eraserRadius=eraserSize,smoothing=smoothing,preview=transitionArtwork ?: paperPreview?.takeIf { it.first==previewIdentity }?.third,
                centreGuide=!portraitDiary,guideHorizontal=landscape && editorPage.canvasAspect<1f,pressureSensitivity=inkPressure,straightLine=inkStraight,
                onImageEdit={ imageEditing=true;note=false;erasing=false;cutting=false;focus.clearFocus();keyboard?.hide() },face=face,ruledWriting=portraitDiary,selectionReset=selectionReset,onHandwritingReady={ handwritingFlush[0]=it },
                showDeleteSticker=selectedImage!=null || cutting && selectedInk.isNotEmpty(),deleteLabel=if(cutting && selectedInk.isNotEmpty()) "删除笔迹" else "删除贴纸",
                onDeleteSticker={ if(cutting && selectedInk.isNotEmpty()) { model.ink(editorPage.id,editorPage.strokes.filterNot { it.id in selectedInk },face);selectedInk=emptySet() }
                    else { model.stickers(editorPage.id,editorPage.stickers.filterNot { it.id==selectedImage },face);selectedImage=null;imageEditing=false } })
        }
        }
        }
        if(unfold==0f && overview.value==0f && panel!="grid" && !coverVisible) {
            LiquidGlassEnvironment(surfaceEnvironment) {
            val closedActions=minOf(fullHeight*.823f-statusInset,available+4.dp-AppControls.Circle)
            val openActions=minOf(fullHeight*AccordionGeometry.ActionsTop-statusInset,available+4.dp-AppControls.Circle)
            val actionTop=if(landscape) available-58.dp else closedActions+(openActions-closedActions)*BookOpeningGeometry.spread(progress)
            Column(Modifier.offset(y=actionTop).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(state.hasJournal || creationFrom!=null) {
                    DiaryIcon("纸页选项",R.drawable.paper_more_horizontal) { if(!busy) panel="page-options" }
                    DiaryIcon("导出",R.drawable.paper_share) { if(!busy) panel="export" }
                    DiaryIcon(if(progress==0f) "删除书册" else "删除纸页",R.drawable.paper_trash_2) { if(progress==0f) { if(!busy) panel="delete-book" } else deletePaper() }
                    }
                    DiaryIcon(if(progress==0f) "新建书册" else "新的一页",R.drawable.paper_plus) {
                        if(!busy) { if(progress==0f) panel="new-book" else addPaper() }
                    }
                }
            }
            }
        }
        if(editing && unfold==1f && page!=null) CompositionLocalProvider(LocalPaperEditorSession provides editorSession) { PaperEditorGlass(surfaceEnvironment) {
            PaperEditorTools(tool,erasing,cutting,imageEditing,color,inkWidth,eraserSize,!imagesOnly,selectedImage!=null,
                opacity=inkOpacity,pressure=inkPressure,straight=inkStraight,portraitEditor=portraitDiary,textEditing=note,
                stickerLocked=editorPage?.stickers?.find { it.id==selectedImage }?.locked==true,
                onToolbarBounds={ editorToolbarBounds=it },
                onTrayBounds={ editorTrayBounds=it },
                toolColors=penColors,hasInkSelection=selectedInk.isNotEmpty(),
                onLock={ selectedImage?.let { id -> editorPage?.let { art -> model.stickers(page.id,art.stickers.map { if(it.id==id) it.copy(locked=!it.locked) else it },face) } } },
                onTool={ next,erase,cut ->
                    handwritingFlush[0]()
                    if(!erase && !cut && next!=tool) {
                        penColors=penColors.copyOf().also { it[tool.ordinal]=color }
                        penProfiles=penProfiles.copyOf().also { values ->
                            val base=tool.ordinal*4;values[base]=inkWidth;values[base+1]=inkOpacity
                            values[base+2]=inkPressure;values[base+3]=0f
                        }
                        val base=next.ordinal*4;color=penColors[next.ordinal];inkWidth=penProfiles[base]
                        inkOpacity=penProfiles[base+1];inkPressure=penProfiles[base+2]
                    }
                    tool=next;erasing=erase;cutting=cut;selectionReset++;selectedImage=null;imageEditing=false;note=false;focus.clearFocus();keyboard?.hide()
                },
                onColor={ color=it;erasing=false;cutting=false;selectionReset++;selectedImage=null;imageEditing=false;note=false;focus.clearFocus();keyboard?.hide() },onWidth={ inkWidth=it },onEraser={ eraserSize=it },
                onOpacity={ inkOpacity=it },onPressure={ inkPressure=it },onStraight={ inkStraight=it },
                onBack=::closePaper,onDone=::closePaper,
                onUndo={ handwritingFlush[0]();focus.clearFocus();keyboard?.hide();model.undo(page.id,face) },onRedo={ focus.clearFocus();keyboard?.hide();model.redo(page.id,face) },
                onImage={ handwritingFlush[0]();focus.clearFocus();keyboard?.hide();if(portraitDiary) { note=false;imageEditing=true;panel="stickers" } else imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onText={},
                onSticker={ handwritingFlush[0]();focus.clearFocus();keyboard?.hide();note=false;imageEditing=true;panel="stickers" },
                onDeleteImage={
                    if(cutting) { model.ink(page.id,checkNotNull(editorPage).strokes.filterNot { it.id in selectedInk },face);selectedInk=emptySet() }
                    else { model.stickers(page.id,checkNotNull(editorPage).stickers.filterNot { it.id==selectedImage },face);selectedImage=null }
                })
        }
        }
        if(editing && portraitDiary) PaperEditorGlass(surfaceEnvironment) {
            PaperWallpaperPicker(wallpaperTray,wallpaperDraft,wallpaperWorking,
                onCustom={ wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onOriginal={ wallpaperDraft="" },onPreset={ index -> scope.launch {
                    wallpaperWorking=true
                    try {
                        val bytes=withContext(Dispatchers.IO) { context.assets.open(PaperWallpapers.asset(index)).use { it.readBytes() } }
                        wallpaperDraft=model.importPaperWallpaper(bytes,PaperWallpapers.file(index))
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(_:Exception) { modeError="壁纸未能载入，请重试。" }
                    finally { wallpaperWorking=false }
                } },modifier=Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom)))
        }
        (state.error ?: modeError ?: exportError)?.let { message -> SpaceTheme(LocalSpaceColors.current.foreground) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom=if(editing) 174.dp else bottom+8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                SpaceText(message,12.sp)
                if(state.error!=null && state.saving) DiaryControl("重试保存",model::save)
            }
        } }
        LiquidGlassEnvironment(surfaceEnvironment) { SpaceTheme(LocalSpaceColors.current.foreground) {
        if(showingOverview || overview.value>0f) BookOverviewControls(state.copy(pages=overviewPages),overview.value,overviewAnchor,overviewScroll,
            top=overviewTop.takeIf { it.isFinite() } ?: with(density) { (fullHeight*.25f).toPx() },
            onScroll={ overviewScroll=it },onPage={ editFace=PaperFace.RIGHT;returnOverview(it,after={ editCurrent(false,false) }) },onClose={ returnOverview() },
            onRecycle={ if(!busy && overview.value==1f) panel="recycle" },
            onImport={ if(!busy && !state.working) { photoTarget=null;photoJournal=state.journalId;photoPagesPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            onAdd={ if(!busy && !state.working) model.addPage(state.pages.lastOrNull()?.id) },
            onDrag={ id,target,dx,dy ->
                overviewOrderIds=OverviewOrder.move(state.pages,id,target).map { it.id }
                overviewDragId=id;overviewDragOffset=Offset(dx,dy)
            },onCancelDrag={ if(!busy) { overviewOrderIds=null;overviewDragId=null;overviewDragOffset=Offset.Zero } },
            onDrop={ id,target ->
                if(!busy) { busy=true;scope.launch { try {
                    val order=overviewOrderIds ?: OverviewOrder.move(state.pages,id,target).map { it.id }
                    overviewOrderIds=order
                    val x=overviewDragOffset.x;val y=overviewDragOffset.y
                    Animatable(1f).animateTo(0f,spring(dampingRatio=.85f,stiffness=450f)) { overviewDragOffset=Offset(x*value,y*value) }
                    overviewDragId=null
                    if(order!=state.pages.map { it.id }) {
                        model.movePages(listOf(id),state.journalId,order.getOrNull(order.indexOf(id)+1))
                        withTimeoutOrNull(6000) { while(state.pages.map { it.id }!=order && state.error==null) delay(16) }
                    }
                } finally { overviewOrderIds=null;overviewDragId=null;overviewDragOffset=Offset.Zero;busy=false } } }
            },enabled=!state.working && !nameEditing)

        var lastOptions by remember { mutableStateOf("") }
        SideEffect { if(panel.isNotEmpty() && panel!="grid") lastOptions=panel }
        val frosted=panel=="stickers" || panel.isEmpty() && lastOptions=="stickers"
        val panelEnvironment=if(frosted) surfaceEnvironment.copy(
            layeredTintOverride=.94f,
            theme=surfaceEnvironment.theme.copy(glassTint=0xFFFFFFFF),
            groups=surfaceEnvironment.groups.copy(buttons=surfaceEnvironment.groups.buttons.copy(blur=16f,tint=.22f,magnification=1f))) else surfaceEnvironment
        PaperEditorGlass(panelEnvironment,enabled=editing && unfold>0f) {
        GlassPanelTransition(if(panel=="grid") "" else panel) { visiblePanel -> when(visiblePanel) {
            "recycle" -> DiaryRecycleBin(state,model) { panel=if(showingOverview) "grid" else "" }
            "grid-manage" -> DiaryGrid(state,model,{ id -> panel="grid";returnOverview(id) },{ panel="grid" })
            "library" -> JournalLibrary(state,model,{ id -> scope.launch { if(model.flush()) { selected=false;opened.snapTo(0f);curl.snapTo(0);pageId=null;model.selectJournal(id);panel="" } } },{ panel="" },onManage={ panel="data" })
            "delete-book" -> DiaryActionPopup({ panel="" }) {
                DiaryAction("确定",{ panel="";deleteBook() })
                DiaryAction("取消",{ panel="" },last=true)
            }
            "new-book" -> DiaryActionPopup({ panel="" }) {
                DiaryAction("手帐",{ panel="";createBook(BookKind.SCRAPBOOK) })
                DiaryAction("日记",{ panel="";createBook(BookKind.DIARY) },last=true)
            }
            "page-options" -> DiaryActionPopup({ panel="" }) {
                if(!imagesOnly) DiaryAction("手写笔记",{ editPaper() })
                DiaryAction("封面",{
                    panel="";scope.launch {
                        focus.clearFocus();keyboard?.hide()
                        if(opened.value>0f) move(opened,0f)
                        coverEditing=true
                    }
                })
                DiaryAction("移动",{ panel="move-page" })
                DiaryAction("添加图片",{
                    photoTarget=page?.id;photoJournal=state.journalId;panel=""
                    photoPagesPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },last=true)
            }
            "move-page" -> DiarySheet("移动纸页",{ panel="" }) {
                val targets=state.journals.filter { it.deletedAt==0L && it.id!=state.journalId && it.kind==state.journal.kind }
                if(targets.isEmpty()) SpaceText("先在书架创建另一本书册。",14.sp,Modifier.padding(16.dp),alpha=.7f)
                targets.forEach { journal -> DiaryControl(journal.name) { page?.let { model.movePages(listOf(it.id),journal.id) };panel="" } }
            }
            "data" -> DiarySheet("书册管理",{ panel="" }) {
                DiaryControl("复制书册") { model.duplicateJournal();panel="" }
                DiaryControl("备份书册") { backup.launch("晴页日记备份.zip");panel="" }
                DiaryControl("导入备份") { restore.launch(arrayOf("application/zip","application/octet-stream"));panel="" }
            }
            "editor-more" -> DiaryActionPopup({ panel="" }) {
                if(cutting && selectedInk.isNotEmpty()) {
                    DiaryAction("复制选区",{ page?.let { model.saveClip(it.id,selectedInk) };panel="clips" })
                    DiaryAction("剪切选区",{ page?.let { model.saveClip(it.id,selectedInk,true) };selectedInk=emptySet();cutting=false;panel="" })
                    DiaryAction("删除选区",{ page?.let { current -> model.ink(current.id,current.strokes.filterNot { it.id in selectedInk }) };selectedInk=emptySet();cutting=false;panel="" })
                }
                DiaryAction("画笔设置",{ panel="brush" })
                if(!imagesOnly) {
                    DiaryAction("胶带",{ panel="tape" })
                    DiaryAction("贴纸",{ panel="stickers" })
                    DiaryAction("画布片段",{ panel="clips" })
                }
                DiaryAction("拼贴",{ panel="collage" },last=true)
            }
            "brush" -> DiarySheet("画笔设置",{ panel="" }) {
                listOf(InkTool.FOUNTAIN,InkTool.PENCIL,InkTool.MARKER,InkTool.FELT,InkTool.WATERCOLOR,InkTool.DIAGRAM,InkTool.FILL).forEach { option ->
                    DiaryControl(brushName(option)+(if(tool==option) " · 当前" else "")) { tool=option;erasing=false;imageEditing=false;panel="" };Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("细" to .004f,"中" to .008f,"粗" to .018f).forEach { (label,value) -> DiaryControl(label,{ inkWidth=value },Modifier.weight(1f)) } }
                Spacer(Modifier.height(12.dp));SpaceText("橡皮大小",12.sp,alpha=.6f)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("小" to .022f,"中" to .035f,"大" to .062f).forEach { (label,value) -> DiaryControl(label,{ eraserSize=value },Modifier.weight(1f)) } }
                Spacer(Modifier.height(12.dp));SpaceText("笔迹稳定",12.sp,alpha=.6f)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("关" to 0f,"中" to .35f,"强" to .6f).forEach { (label,value) -> DiaryControl(label,{ smoothing=value },Modifier.weight(1f)) } }
                Spacer(Modifier.height(16.dp));DiaryField(hex,{ hex=it },"自定义颜色 · 六位 HEX")
                Spacer(Modifier.height(8.dp));DiaryControl("应用颜色") { hex.removePrefix("#").takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) }?.let { color=0xFF000000L or it.toLong(16);panel="" } }
            }
            "tape" -> DiarySheet("胶带",{ panel="" }) {
                TapePattern.entries.forEach { pattern ->
                    DiaryControl(tapeName(pattern)) { page?.let { model.addTape(it.id,pattern) };imageEditing=true;panel="" }
                    Spacer(Modifier.height(8.dp))
                }
            }
            "stickers" -> DiarySheet("贴纸",{ panel="" },paperContent=true,frosted=true) {
                DiaryStickerLibrary(onImage={
                    stickerPhoto=page?.id;stickerPhotoFace=face;panel=""
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },onText={ value ->
                    page?.let { model.addTextSticker(it.id,value,face) };note=false;imageEditing=true;panel=""
                }) { art ->
                    page?.let { model.addSticker(it.id,art,context.assets,face) }
                    note=false;imageEditing=true;panel=""
                }
            }

            "collage" -> DiarySheet("拼贴网格",{ panel="" }) {
                val images=(page?.stickers ?: emptyList()).filter { it.kind==StickerKind.IMAGE || it.kind==StickerKind.STICKER }
                if(images.size<2) SpaceText("先放入至少两张贴图或贴纸，再选择网格。",14.sp,alpha=.6f)
                listOf("两张 · 上下" to (1 to 2),"两张 · 左右" to (2 to 1),"四张 · 田字" to (2 to 2),"六张 · 六格" to (2 to 3),"九张 · 九格" to (3 to 3)).forEach { (label,grid) ->
                    DiaryControl(label) {
                        page?.let { model.collage(it.id,images.map { element -> element.id }.take(grid.first*grid.second),grid.first,grid.second) }
                        imageEditing=true;panel=""
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            "templates" -> GlassOptionsPopup({ panel="" }) {
                BackHandler { panel="" }
                PaperStyle.entries.forEach { style -> GlassMenuOption(paperName(style),{ page?.let { model.paper(it.id,style) };panel="" },Modifier.fillMaxWidth()) }
                GlassMenuOption("设为默认",{ if(page!=null) model.updateJournal(state.journal.copy(template=page.style));panel="" },Modifier.fillMaxWidth())
            }
            "export" -> DiaryActionPopup({ panel="" }) {
                DiaryAction("导出绘图",{ if(page!=null) { exporting=page.id;png.launch("${page.title.ifBlank { "晴页" }}.png");panel="" } },description="导出纸页 PNG",rowHeight=48.dp)
                DiaryAction("导出 PDF",{ pdf.launch("${state.journal.name}.pdf");panel="" },description="导出整本 PDF",last=true,rowHeight=48.dp)
            }
            "clips" -> DiarySheet("画布片段",{ panel="" }) {
                if(state.clips.isEmpty()) SpaceText("用剪刀围住笔画，复制后可在其他纸页复用。",14.sp,alpha=.6f)
                state.clips.forEach { clip ->
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DiaryControl("粘贴 · ${clip.name}") { page?.let { model.pasteClip(it.id,clip) };cutting=false;panel="" }
                        DiaryIcon("删除片段 ${clip.name}",R.drawable.paper_trash_2) { model.deleteClip(clip.id) }
                    };Spacer(Modifier.height(12.dp))
                }
            }
        } }
        }
        } }
    }
}
internal fun brushName(tool:InkTool)=when(tool) { InkTool.FOUNTAIN->"钢笔";InkTool.PENCIL->"铅笔";InkTool.MARKER->"马克笔";InkTool.FELT->"签字笔";InkTool.WATERCOLOR->"水彩";InkTool.DIAGRAM->"智能形状";InkTool.FILL->"填色 · 绘制闭合区域";InkTool.BLEND->"混色";InkTool.ERASER->"橡皮";InkTool.TECHNICAL->"针筒笔";InkTool.CRAYON->"蜡笔";InkTool.INK->"墨水笔";InkTool.GLYPH_ERASER->"字迹橡皮" }
internal fun paperName(style:PaperStyle)=when(style) { PaperStyle.PLAIN->"素纸";PaperStyle.LINED->"横线";PaperStyle.DOTTED->"点阵";PaperStyle.GRID->"方格";PaperStyle.MUSIC->"五线谱" }
internal fun tapeName(pattern:TapePattern)=when(pattern) { TapePattern.SOLID->"纯色";TapePattern.STRIPE->"斜纹";TapePattern.DOT->"圆点";TapePattern.CHECK->"格纹";TapePattern.LACE->"蕾丝" }

@Composable internal fun DiaryControl(label:String,onClick:()->Unit)=DiaryControl(label,onClick,Modifier)
@Composable internal fun DiaryControl(label:String,onClick:()->Unit,modifier:Modifier) {
    if(LocalGlassOptions.current) {
        GlassMenuOption(label,onClick,modifier)
        return
    }
    val measured=rememberTextMeasurer().measure(label,TextStyle(fontSize=13.sp))
    val width=with(LocalDensity.current) { measured.size.width.toDp()+36.dp }.coerceAtLeast(64.dp)
    GlassButton(onClick,modifier.width(width).heightIn(min=48.dp)) {
        BasicText(label,Modifier.align(Alignment.Center).padding(horizontal=14.dp,vertical=12.dp),style=TextStyle(color=LocalSpaceColors.current.foreground,fontSize=13.sp),maxLines=1,softWrap=false)
    }
}

