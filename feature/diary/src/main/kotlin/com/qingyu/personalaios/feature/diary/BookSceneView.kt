package com.qingyu.personalaios.feature.diary

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.qingyu.personalaios.domain.DiaryPage


import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

internal data class BookScene(val pages: List<DiaryPage> = emptyList(), val current: Int = 0,
    val open: Float = 0f, val turn: Float = 0f, val direction: Int = 1, val selected: Boolean = false,
    val motion: Boolean = true, val portrait: Boolean = true, val roll: Float = 0f,
    val journal: com.qingyu.personalaios.domain.DiaryJournal = com.qingyu.personalaios.domain.DiaryJournal(id="main",name="我的日记"),
    val journals:List<com.qingyu.personalaios.domain.DiaryJournal> = emptyList(),val libraryOffset:Float=0f,
    val selection:Float=if(selected) 1f else 0f,
    val creationFrom:com.qingyu.personalaios.domain.DiaryJournal?=null,val creationProgress:Float=1f,
    val removingId:String?=null,val removal:Float=0f,
    val closedCentreY:Float=Float.NaN,val openCentreY:Float=Float.NaN,val preparingOpen:Boolean=false,
    val libraryRecoil:Float=0f,val pendingJournal:com.qingyu.personalaios.domain.DiaryJournal?=null,
    val pendingPage:DiaryPage?=null,val additionReveal:Float=1f,
    val closing:Float=-1f,val overview:Float=0f,val overviewAnchor:Int=current,val overviewScroll:Float=0f,val overviewTop:Float=Float.NaN,
    val overviewDragId:String?=null,val overviewDragX:Float=0f,val overviewDragY:Float=0f,val thumbnail:Boolean=false)

/** Retained transparent Vulkan surface, composited with the shared liquid glass backdrop. */
internal class BookSceneView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    var onFirstFrame: () -> Unit = {}
    var onRenderFailure: () -> Unit = {}
    var tap: (Int?) -> Unit = {}
    var tappedSide:Int=1
        private set
    var dragStart: () -> Boolean = { false }
    var grabShelf: () -> Boolean = { false }
    var dragMove: (Float) -> Unit = {}
    var dragFinish: (Boolean) -> Unit = {}
    var pinch:(Boolean)->Unit={}
    var pinchTrack:(Float)->Boolean={ false }
    var pinchFinish:(Boolean)->Unit={}
    var dragOffset=0f;private set
    val carouselCoordinateScale get()=BookCarouselMotion.coordinateScale(width.toFloat(),height.toFloat(),scene.portrait)
    var fastFling=false;private set
    var dragVelocity=0f;private set
    private val moveSamples=java.util.ArrayDeque<Pair<Long,Float>>()
    private fun movementVelocity(event:MotionEvent) {
        fun sample(time:Long,x:Float) {
            if(moveSamples.peekLast()?.first==time) moveSamples.removeLast()
            moveSamples.addLast(time to x)
            while(moveSamples.size>2 && time-moveSamples.first.first>85) moveSamples.removeFirst()
        }
        for(i in 0 until event.historySize) sample(event.getHistoricalEventTime(i),event.getHistoricalX(i))
        sample(event.eventTime,event.x)
        val first=moveSamples.first;val last=moveSamples.last
        dragVelocity=(last.second-first.second)/(last.first-first.first).coerceAtLeast(1)*1000/width.coerceAtLeast(1)
    }
    private var pinchDistance=0f
    private var pinchScale=1f
    private var multiple=false
    private var followingPinch=false
    private var consumedPinch=false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f; private var downY = 0f
    private var moved = false; private var dragging = false
    private var clickX=0f; private var clickY=0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX=event.x;downY=event.y;moved=false;dragging=false;multiple=false;followingPinch=false;consumedPinch=false;dragOffset=0f;fastFling=false
                moveSamples.clear();moveSamples.addLast(event.eventTime to event.x);dragVelocity=0f
                // A moving shelf stops under the finger on DOWN, rather than
                // advancing until the next MOVE exceeds touch slop.
                if(scene.open==0f) dragging=grabShelf()
            }
            MotionEvent.ACTION_POINTER_DOWN -> { if(event.pointerCount==2) {
                multiple=true;moved=true;pinchDistance=hypot(event.getX(1)-event.getX(0),event.getY(1)-event.getY(0)).coerceAtLeast(1f);pinchScale=1f
                if(dragging) { dragFinish(false);dragging=false }
            } }
            MotionEvent.ACTION_MOVE -> {
                movementVelocity(event)
                if(multiple) {
                    if(event.pointerCount>=2) pinchScale=hypot(event.getX(1)-event.getX(0),event.getY(1)-event.getY(0))/pinchDistance
                    if(!consumedPinch && (followingPinch || pinchScale<.98f)) followingPinch=pinchTrack(pinchScale) || followingPinch
                    return true
                }
                val dx=event.x-downX;val dy=event.y-downY
                val threshold=slop*if(scene.open==1f && scene.overview==0f) .55f else 1f
                if(!moved && max(abs(dx),abs(dy))>threshold) {
                    moved=true
                }
                // A brief selection/save handoff can reject the first MOVE.
                // Retry subsequent horizontal moves instead of losing this touch.
                if(moved && abs(dx)>abs(dy) && !dragging) dragging=dragStart()
                if(dragging) { dragOffset=dx/width.coerceAtLeast(1);dragMove(dragOffset) }
            }
            MotionEvent.ACTION_POINTER_UP -> if(followingPinch) {
                pinchFinish(true);followingPinch=false;consumedPinch=true
            }
            MotionEvent.ACTION_UP -> {
                // The supplied HTML samples the last 85ms of movement, not the stationary UP.
                // Keeping the last move velocity also avoids slow dispatch turning a fast flick into zero.
                if(moveSamples.isNotEmpty() && event.eventTime-moveSamples.last.first>150) dragVelocity=0f
                fastFling=abs(dragVelocity)>.8f && abs(dragOffset)>.035f
                if(android.util.Log.isLoggable("DiaryBookGesture",android.util.Log.DEBUG))
                    android.util.Log.d("DiaryBookGesture","release offset=$dragOffset velocity=$dragVelocity")
                if(followingPinch) { pinchFinish(true);followingPinch=false;consumedPinch=true }
                else if(consumedPinch) Unit
                else if(multiple && (pinchScale>1.13f || pinchScale<.87f)) pinch(pinchScale>1f)
                else if(dragging) dragFinish(true) else if(!moved) { clickX=event.x;clickY=event.y;performClick() }
                dragging=false
            }
            MotionEvent.ACTION_CANCEL -> { if(followingPinch) pinchFinish(false);followingPinch=false;consumedPinch=true;if(dragging) dragFinish(false);dragging=false }
        }
        return true
    }
    override fun performClick(): Boolean {
        super.performClick()
        val s=scene
        val hit=if(s.open==1f && s.turn==0f) BookPagePicker.pickFace(clickX,clickY,width.toFloat(),height.toFloat(),s.current,s.pages.size,s.selected,s.portrait,s.openCentreY.takeIf { it.isFinite() } ?: BookLibraryGeometry.centreY(height.toFloat(),s.portrait)) else null
        tappedSide=hit?.side ?: 1
        tap(if(s.open==0f) BookShelfGeometry.pick(clickX,clickY,width.toFloat(),height.toFloat(),s)
            else hit?.index)
        return true
    }
    private val thread = HandlerThread("DiaryBookVulkan",android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val worker = Handler(thread.looper)
    private val pending = AtomicBoolean(false)
    private val requested = AtomicLong(0)
    private val gpu = VulkanBookBackend()
    private val renderer = SolidBookRenderer(gpu) { requestFrame() }
    fun pageReady(page:DiaryPage)=renderer.pageReady(page)
    fun coverReady(journal:com.qingyu.personalaios.domain.DiaryJournal)=renderer.coverReady(journal)
    @Volatile private var submittedWarmup:BookScene?=null
    @Volatile private var submittedCover:String?=null
    @Volatile private var submittedFrame:BookPresentedFrame?=null
    @Volatile private var presentedFrame:BookPresentedFrame?=null
    fun viewportFrameReady()=presentedFrame?.viewportMatches(width,height)==true
    fun pageFrameReady(page:DiaryPage)=presentedFrame?.matches(page.id,page.revision,width,height)==true
    private var contentRevealed=false
    private fun sameOpening(value:BookScene?)=value!=null && value.journal==scene.journal &&
        value.pages===scene.pages && value.current==scene.current && scene.preparingOpen && scene.open==0f
    // Upload barriers, the warm frame and all following draws share one queue.
    // A successful submission is the readiness fence; a TextureView callback
    // can be delayed when the closed image itself has not changed.
    fun openingFrameReady(journal:com.qingyu.personalaios.domain.DiaryJournal)=sameOpening(submittedWarmup) && scene.journal==journal
    @Volatile private var scene = BookScene()
    @Volatile private var released = false
    @Volatile private var renderingEnabled=true
    @Volatile private var scenePublishedAt=0L
    private var lastSlowFrameLog=0L
    private var nativeSurface:Surface?=null
    private var surfaceReady=false
    private val displayManager=context.getSystemService(android.hardware.display.DisplayManager::class.java)
    private var displayObserved=false
    private var requestedRate=0f
    @Volatile private var highRefresh=false
    private val ui=Handler(android.os.Looper.getMainLooper())
    private var refreshUntil=0L
    private var refreshReleaseQueued=false
    private val releaseRefresh=object:Runnable {
        override fun run() {
            val remaining=refreshUntil-android.os.SystemClock.uptimeMillis()
            if(remaining>0 && !released && renderingEnabled) { ui.postDelayed(this,remaining);return }
            refreshReleaseQueued=false;setHighRefresh(false)
        }
    }
    private fun setHighRefresh(enabled:Boolean) {
        if(highRefresh==enabled) return
        highRefresh=enabled
        if(android.os.Build.VERSION.SDK_INT>=35) setRequestedFrameRate(if(enabled) REQUESTED_FRAME_RATE_CATEGORY_HIGH else REQUESTED_FRAME_RATE_CATEGORY_DEFAULT)
        worker.post { nativeSurface?.takeIf { it.isValid }?.setFrameRate(if(highRefresh) requestedRate else 0f,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) }
    }
    private fun requestInteractiveRefresh() {
        setHighRefresh(true);refreshUntil=android.os.SystemClock.uptimeMillis()+250
        if(!refreshReleaseQueued) { refreshReleaseQueued=true;ui.postDelayed(releaseRefresh,250) }
    }
    private val displayListener=object:android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(id:Int)=Unit
        override fun onDisplayRemoved(id:Int)=Unit
        override fun onDisplayChanged(id:Int) { if(id==display?.displayId) updateDisplayRate() }
    }
    private fun updateDisplayRate():Float {
        // Follow the active display, shared with Compose. Asking a separate
        // TextureView to force the maximum advertised mode caused mode changes
        // while the window/animation producer could still be on another cadence.
        val rate=BookRenderPolicy.displayRate(display?.refreshRate ?: 60f)
        if(rate!=requestedRate) {
            requestedRate=rate
            worker.post { nativeSurface?.takeIf { it.isValid }?.setFrameRate(if(highRefresh) rate else 0f,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS) }
        }
        return rate
    }
    private fun stopDisplayObservation() {
        if(displayObserved) displayManager.unregisterDisplayListener(displayListener)
        displayObserved=false;requestedRate=0f
    }
    // Worker-owned: publish buffer size and projection size together, in queue order.
    // Updating these on the UI thread let a queued old frame use the new aspect ratio
    // before its corresponding native resize, briefly stretching the book on rotation.
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    // Worker-owned idle debounce. No whole-book preload and no changed shelf pose.
    private var idleWarmScene:BookScene?=null
    private var idleWarmAfter=0L
    private fun warmWhileClosed(drawing:BookScene):Boolean {
        if(!BookOpeningResources.canWarmWhileClosed(drawing)) { idleWarmScene=null;return false }
        if(!BookOpeningResources.sameArtwork(idleWarmScene,drawing)) {
            idleWarmScene=drawing;idleWarmAfter=android.os.SystemClock.uptimeMillis()+350
            worker.postDelayed({
                if(!released && surfaceReady && BookOpeningResources.canWarmWhileClosed(scene) &&
                    BookOpeningResources.sameArtwork(drawing,scene)) requestFrame()
            },350)
        }
        return android.os.SystemClock.uptimeMillis()>=idleWarmAfter
    }
    private fun resizeBuffer(texture:SurfaceTexture,width:Int,height:Int) {
        surfaceWidth=width.coerceAtLeast(1);surfaceHeight=height.coerceAtLeast(1)
        texture.setDefaultBufferSize(surfaceWidth,surfaceHeight)
    }
    init {
        isOpaque=false;surfaceTextureListener=this;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        // Keep TextureView updating while its initial asynchronous frames are
        // effectively invisible. Reveal only a presented, real-cover frame.
        alpha=.001f
    }
    // Compose supplies every motion frame. An unchanged resting scene needs no
    // Vulkan redraw; the retained transparent texture still shows live weather below.
    fun update(value: BookScene) {
        if(released || scene==value) return
        if(renderingEnabled && !value.thumbnail) requestInteractiveRefresh()
        if(value.preparingOpen && !scene.preparingOpen) submittedWarmup=null
        scenePublishedAt=android.os.SystemClock.elapsedRealtimeNanos()
        scene=value;requestFrame()
    }
    /** Keep the surface/resources, but do not redraw a book fully hidden by
     * the editor. Resume from the newest scene before the paper returns. */
    fun setRenderingEnabled(enabled:Boolean) {
        if(renderingEnabled==enabled) return
        renderingEnabled=enabled
        if(!enabled) { ui.removeCallbacks(releaseRefresh);refreshReleaseQueued=false;setHighRefresh(false) }
        if(enabled) requestFrame()
    }
    /** Publish finger position before Compose's next frame; peers and shadows
     * are derived from this same coordinate on the Vulkan worker. */
    fun trackLibrary(offset:Float,journal:com.qingyu.personalaios.domain.DiaryJournal=scene.journal,
        pages:List<DiaryPage> =scene.pages)=update(scene.copy(libraryOffset=offset,libraryRecoil=0f,
            journal=journal,pages=pages,current=if(journal.id==scene.journal.id) scene.current else 0))
    /** Gesture frames reach the render worker on this input event, without a
     * coroutine or Compose frame first. Compose retains the identical state. */
    fun trackTurn(progress:Float,direction:Int) {
        update(scene.copy(turn=progress,direction=direction,selected=false))
    }
    fun trackClosing(clock:Float) {
        update(scene.copy(open=BookClosingGeometry.opening(clock),closing=clock,selected=false))
    }
    private fun requestFrame() {
        requested.incrementAndGet()
        enqueueFrame()
    }
    private fun enqueueFrame() {
        if (released || !renderingEnabled || !pending.compareAndSet(false, true)) return
        // Compose animation and batched touch already publish on the UI display
        // clock. A second worker Choreographer delayed each scene by another
        // refresh. Render the newest publication immediately; Vulkan's bounded
        // frame slots/FIFO provide backpressure, with no idle render loop.
        worker.post {
            val version=requested.get()
            try {
                if(!surfaceReady || released || !renderingEnabled) return@post
                val drawing=scene
                val warmOpening=warmWhileClosed(drawing)
                val started=android.os.SystemClock.elapsedRealtimeNanos()
                val sceneAge=(started-scenePublishedAt)/1_000_000f
                android.os.Trace.beginSection(when {
                    (drawing.preparingOpen || warmOpening) && drawing.open==0f -> "DiaryBook.PreparingFrame"
                    drawing.open>0f && drawing.open<1f -> "DiaryBook.OpeningFrame"
                    else -> "DiaryBook.SettledFrame"
                })
                try {
                    android.os.Trace.beginSection("DiaryBook.Vulkan")
                    try { renderer.draw(surfaceWidth,surfaceHeight,drawing,warmOpening) } finally { android.os.Trace.endSection() }
                    val submitted=android.os.SystemClock.elapsedRealtimeNanos()
                    val ready=if(drawing.preparingOpen && drawing.open==0f && renderer.openingResourcesReady(drawing)) drawing else null
                    android.os.Trace.beginSection("DiaryBook.Present")
                    val presented=try { gpu.present() } finally { android.os.Trace.endSection() }
                    val finished=android.os.SystemClock.elapsedRealtimeNanos()
                    // Local, rate-limited diagnostics distinguish CPU preparation
                    // from native acquire/fence/present waits on a real phone.
                    // Never sample runtime/thermal services on normal frames.
                    if(finished-started>35_000_000L && finished-lastSlowFrameLog>5_000_000_000L) {
                        lastSlowFrameLog=finished
                        val thermal=context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus
                        android.util.Log.i("DiaryBookPerf","cpuMs=${(submitted-started)/1_000_000} nativeMs=${(finished-submitted)/1_000_000} sceneAgeMs=${sceneAge.toInt()} rate=$requestedRate thermal=$thermal gc=${android.os.Debug.getRuntimeStat("art.gc.gc-count")} uploads=${gpu.stats().toList()}")
                    }
                    if(presented) {
                        val page=drawing.pages.getOrNull(drawing.current)?.takeIf { renderer.pageReady(it) }
                        submittedFrame=BookPresentedFrame(surfaceWidth,surfaceHeight,page?.id,page?.revision ?: -1)
                        submittedWarmup=ready
                        submittedCover=if(renderer.coverReady(drawing.journal)) drawing.journal.id else null
                    }
                    else requestFrame()
                } finally { android.os.Trace.endSection() }

            } catch (error: Exception) {
                android.util.Log.e("DiaryBookVulkan", "Book rendering failed", error)
                post { if(!released) onRenderFailure() }
            }
            finally {
                pending.set(false)
                // Coalesce rapid updates but never drop the final settled scene.
                if(!released && requested.get()!=version) enqueueFrame()
            }
        }
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        animate().cancel();alpha=.001f;contentRevealed=false;submittedCover=null
        if(!displayObserved) { displayManager.registerDisplayListener(displayListener,Handler(android.os.Looper.getMainLooper()));displayObserved=true }
        val rate=updateDisplayRate()
        worker.post {
            if(released) return@post
            try {
                resizeBuffer(texture,width,height)
                val window=Surface(texture);nativeSurface=window
                window.setFrameRate(if(highRefresh) rate else 0f,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
                gpu.create(window,context.assets,surfaceWidth,surfaceHeight,java.io.File(context.cacheDir,"book-pipelines.v1").absolutePath);renderer.contextCreated();surfaceReady=true
                requestFrame()
            } catch(error:Exception) {
                destroyContext();android.util.Log.e("DiaryBookVulkan","Cannot create book surface",error)
                post { if(!released) onRenderFailure() }
            }
        }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        presentedFrame=null
        worker.post { if(surfaceReady && !released) {
            resizeBuffer(texture,width,height);gpu.resize(surfaceWidth,surfaceHeight);requestFrame()
        } }
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        presentedFrame=submittedFrame
        if(!contentRevealed && submittedCover==scene.journal.id && renderer.coverReady(scene.journal)) {
            contentRevealed=true
            if(scene.motion) animate().alpha(1f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            else alpha=1f
            onFirstFrame()
        }
        // A texture callback may beat the worker publishing its presentation
        // token. Request a follow-up instead of leaving the surface hidden.
        if(!contentRevealed && renderer.coverReady(scene.journal)) requestFrame()
    }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        stopDisplayObservation()
        worker.post { destroyContext(); texture.release() }
        return false
    }
    private fun destroyContext() {
        surfaceReady=false;submittedWarmup=null;submittedCover=null;idleWarmScene=null;submittedFrame=null;presentedFrame=null
        gpu.destroy();nativeSurface?.release();nativeSurface=null
    }
    fun release() {
        released = true
        ui.removeCallbacks(releaseRefresh);refreshReleaseQueued=false;setHighRefresh(false)
        stopDisplayObservation()
        animate().cancel()
        worker.post { destroyContext(); renderer.release(); thread.quitSafely() }
    }
}
