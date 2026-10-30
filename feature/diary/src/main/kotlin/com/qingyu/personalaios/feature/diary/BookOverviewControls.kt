package com.qingyu.personalaios.feature.diary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Transparent controls over the same flat, thick Vulkan sheets. Long press
 * captures one whole sheet; preview order and renderer positions move together. */
@Composable internal fun BookOverviewControls(state:DiaryState,progress:Float,anchor:Int,scroll:Float,top:Float,
    onScroll:(Float)->Unit,onPage:(String)->Unit,onClose:()->Unit,onRecycle:()->Unit,
    onImport:()->Unit,onAdd:()->Unit,onDrag:(String,Int,Float,Float)->Unit,onDrop:(String,Int)->Unit,
    onCancelDrag:()->Unit,enabled:Boolean=true) {
    val scope=rememberCoroutineScope()
    val liveScroll by rememberUpdatedState(scroll)
    val liveChange by rememberUpdatedState(onScroll)
    val livePages by rememberUpdatedState(state.pages)
    val dragChange by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    val cancel by rememberUpdatedState(onCancelDrag)
    var settlement by remember { mutableStateOf<Job?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableIntStateOf(0) }
    var previousCount by remember { mutableIntStateOf(state.pages.size) }
    DisposableEffect(Unit) { onDispose { settlement?.cancel() } }
    BackHandler(onBack=onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density=LocalDensity.current
        val width=with(density) { maxWidth.toPx() };val height=with(density) { maxHeight.toPx() }
        val bounds=BookOverviewGeometry.scrollBounds(width,height,state.pages.size,anchor,top)
        val liveBounds by rememberUpdatedState(bounds)
        fun updateDrag() {
            val id=dragged ?: return
            target=OverviewOrder.target(pointer.x,pointer.y,width,height,livePages.size,anchor,liveScroll,top)
            val slot=BookOverviewGeometry.slot(target,width,height,anchor,liveScroll,top)
            dragChange(id,target,pointer.x-grab.x-slot.centreX,pointer.y-grab.y-slot.centreY)
        }
        val drag=rememberDraggableState { delta ->
            if(dragged==null) {
                val outside=liveScroll<liveBounds.start || liveScroll>liveBounds.endInclusive
                liveChange(liveScroll+delta*if(outside) .24f else 1f)
            }
        }
        fun settle(velocity:Float) {
            settlement?.cancel()
            settlement=scope.launch {
                if(liveScroll in liveBounds && abs(velocity)>30f) AnimationState(initialValue=liveScroll,initialVelocity=velocity).animateDecay(exponentialDecay(frictionMultiplier=2.2f)) {
                    liveChange(value);if(value !in liveBounds) cancelAnimation()
                }
                Animatable(liveScroll).animateTo(liveScroll.coerceIn(liveBounds.start,liveBounds.endInclusive),spring(dampingRatio=.85f,stiffness=350f)) { liveChange(value) }
            }
        }
        LaunchedEffect(state.pages.size) {
            if(state.pages.size>previousCount && progress==1f) {
                settlement?.cancel()
                Animatable(liveScroll).animateTo(bounds.start,spring(dampingRatio=.9f,stiffness=380f)) { liveChange(value) }
            }
            previousCount=state.pages.size
        }
        LaunchedEffect(dragged) {
            if(dragged!=null) {
                var previous=withFrameNanos { it }
                while(dragged!=null) {
                    val now=withFrameNanos { it };val seconds=((now-previous)/1e9f).coerceAtMost(.05f);previous=now
                    val band=with(density) { 60.dp.toPx() };val bottom=height*.82f
                    val speed=when { pointer.y<top+band -> (top+band-pointer.y).coerceAtMost(band)*5
                        pointer.y>bottom-band -> -(pointer.y-bottom+band).coerceAtMost(band)*5;else -> 0f }
                    if(speed!=0f) { liveChange((liveScroll+speed*seconds).coerceIn(liveBounds.start,liveBounds.endInclusive));updateDrag() }
                }
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(state.journalId,progress==1f,enabled,top) {
            if(progress==1f && enabled) detectDragGesturesAfterLongPress(
                onDragStart={ point ->
                    settlement?.cancel()
                    val index=livePages.indices.firstOrNull { i ->
                        val slot=BookOverviewGeometry.slot(i,width,height,anchor,liveScroll,top)
                        point.x in slot.left..slot.left+slot.width && point.y in slot.top..slot.top+slot.height && point.y>=top
                    }
                    if(index!=null) {
                        val slot=BookOverviewGeometry.slot(index,width,height,anchor,liveScroll,top)
                        pointer=point;grab=point-Offset(slot.centreX,slot.centreY);dragged=livePages[index].id;target=index;updateDrag()
                    }
                },onDragEnd={ dragged?.let { drop(it,target) };dragged=null },
                onDragCancel={ dragged=null;cancel() }) { change,delta ->
                    if(dragged!=null) { change.consume();pointer+=delta;updateDrag() }
                }
        }.draggable(drag,Orientation.Vertical,enabled=progress==1f && dragged==null,
            onDragStarted={ settlement?.cancel() },onDragStopped={ settle(it) })) {
            for(index in BookOverviewGeometry.visible(width,height,state.pages.size,anchor,scroll,top)) {
                val page=state.pages[index];val slot=BookOverviewGeometry.slot(index,width,height,anchor,scroll,top)
                if(slot.top+slot.height>top && slot.top<height*.84f) Box(Modifier.offset(with(density) { slot.left.toDp() },with(density) { slot.top.toDp() })
                    .size(with(density) { slot.width.toDp() },with(density) { slot.height.toDp() })
                    .semantics { contentDescription="纸页 ${index+1} ${page.title}";selected=dragged==page.id
                        customActions=listOf(CustomAccessibilityAction("移到开头") { onDrag(page.id,0,0f,0f);onDrop(page.id,0);true },
                            CustomAccessibilityAction("移到末尾") { val end=state.pages.lastIndex;onDrag(page.id,end,0f,0f);onDrop(page.id,end);true }) }
                    .clickable(enabled=progress==1f && enabled && dragged==null,interactionSource=null,indication=null) { onPage(page.id) })
            }
        }
        // A full-screen semantic overlay hides the still-visible glass name
        // from accessibility. Keep the scroll action in the header gap instead.
        Spacer(Modifier.offset(y=with(density) { top.toDp()-2.dp }).fillMaxWidth().height(1.dp).semantics {
            contentDescription="所有纸张总览"
            verticalScrollAxisRange=ScrollAxisRange({ -liveScroll },{ -liveBounds.start })
            scrollBy { _,y -> liveChange((liveScroll-y).coerceIn(liveBounds.start,liveBounds.endInclusive));true }
        })
        val controls=((progress-.12f)/.28f).coerceIn(0f,1f)
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,top=18.dp).graphicsLayer { alpha=controls },horizontalArrangement=Arrangement.SpaceBetween) {
            DiaryIcon("返回书本",R.drawable.paper_book_open,onClick=onClose)
            DiaryIcon("所有纸张",R.drawable.paper_grid) { settlement?.cancel();scope.launch { Animatable(liveScroll).animateTo(0f,spring()) { liveChange(value) } } }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom=LocalPageBottomInset.current+16.dp)
            .graphicsLayer { alpha=controls },horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            DiaryIcon("回收站",R.drawable.paper_more_horizontal,onClick=onRecycle)
            DiaryIcon("添加新照片",R.drawable.paper_image,onClick=onImport)
            DiaryIcon("新的一页",R.drawable.paper_plus,onClick=onAdd)
        }
    }
}
