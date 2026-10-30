package com.qingyu.personalaios.feature.diary

import android.graphics.Bitmap
import com.qingyu.personalaios.core.glass.*
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.ui.AppShapes
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration

internal val PaperColor=Color.White
internal val PaperInk=Color(0xFF293844)
internal fun date(page:DiaryPage):String=DateTimeFormatter.ofPattern("yyyy / MM / dd").format(Instant.ofEpochMilli(page.createdAt).atZone(ZoneId.systemDefault()))

@Composable internal fun DiaryPaper(page:DiaryPage,large:Float=0f,editing:Boolean=false,drawing:Boolean=false,
    erasing:Boolean=false,inkColor:Long=0xFF243749,inkWidth:Float=.008f,imageEditing:Boolean=false,
    model:DiaryViewModel?=null,modifier:Modifier=Modifier,tool:InkTool=InkTool.FELT,noteVisible:Boolean=false,
    cutting:Boolean=false,selectedInk:Set<String> = emptySet(),onSelectInk:(Set<String>)->Unit={},onSelectImage:(String?)->Unit={},
    eraserRadius:Float=.035f,smoothing:Float=0f,preview:ImageBitmap?=null,centreGuide:Boolean=false,guideHorizontal:Boolean=false,
    pressureSensitivity:Float=1f,straightLine:Boolean=false,onImageEdit:()->Unit={},face:PaperFace=PaperFace.RIGHT,ruledWriting:Boolean=false,selectionReset:Int=0,
    onHandwritingReady:((()->Unit)->Unit)={},showDeleteSticker:Boolean=false,onDeleteSticker:()->Unit={},deleteLabel:String="删除贴纸") {
    val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
    val session=LocalPaperEditorSession.current
    val handwriting=remember(page.id,face) { RuledWritingBridge() }
    val ruler=remember(page.id,face) { PaperRulerState() }
    val inputDensity=LocalDensity.current
    val keyboardVisible=WindowInsets.ime.getBottom(inputDensity)>0
    val padVisible=editing && ruledWriting && !landscape && !imageEditing && !cutting && !keyboardVisible
    val currentPadVisible by rememberUpdatedState(padVisible)
    SideEffect { onHandwritingReady { handwriting.flush() } }
    LaunchedEffect(padVisible) { if(!padVisible) handwriting.flush() }
    val handleCoordinates=remember(page.id,face) { arrayOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var padOffset by remember(page.id,face) { mutableStateOf(Offset.Zero) }
    val density=LocalDensity.current
    var zoom by remember(page.id,editing) { mutableFloatStateOf(1f) }
    var pan by remember(page.id,editing) { mutableStateOf(Offset.Zero) }
    var liveReady by remember(page.id,face,editing) { mutableStateOf(false) }
    val liveAlpha by androidx.compose.animation.core.animateFloatAsState(if(liveReady) 1f else 0f,
        androidx.compose.animation.core.tween(100),label="paper content handoff")
    BoxWithConstraints(modifier.background(PaperColor).clip(AppShapes.Paper).pointerInput(session.wallpapers) {
        if(session.wallpapers) awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial).consume()
            do { val event=awaitPointerEvent(PointerEventPass.Initial);event.changes.forEach { it.consume() } } while(event.changes.any { it.pressed })
        }
    }) {
        val boardW=minOf(maxWidth,maxHeight*page.canvasAspect)
        val boardH=boardW/page.canvasAspect
        val padSide=(boardW*.56f).coerceIn(120.dp,220.dp)
        val padW=with(density) { padSide.toPx() }
        val paperW=with(density) { boardW.toPx() };val paperH=with(density) { boardH.toPx() }
        val baseX=paperW*.935f-padW;val baseY=paperH-paperW/12-padW
        Box(Modifier.align(Alignment.Center).size(boardW,boardH).paperRulerGestures(ruler,editing && straightLine && !imageEditing) {
            if(currentPadVisible) androidx.compose.ui.geometry.Rect(baseX+padOffset.x,baseY+padOffset.y-with(density) { 48.dp.toPx() },baseX+padOffset.x+padW,baseY+padOffset.y+padW) else null
        }.graphicsLayer {
            scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y
        }) {
            if(!editing) {
                preview?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds) }
            } else {
            if(preview!=null && liveAlpha<1f) Image(preview,null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
            DiaryCollage(page,imageEditing,{ model?.stickers(page.id,it,face) },Modifier.fillMaxSize().graphicsLayer { alpha=if(preview==null) 1f else liveAlpha }.paperRulerDrawing(ruler,editing && straightLine && !imageEditing,with(density) { 30.dp.toPx() }),controls=false,onSelected=onSelectImage,onActivate=onImageEdit,showNotes=!ruledWriting,allowSelection=ruledWriting,selectionReset=selectionReset,onReady={ liveReady=true },
                beforeStickers={ visible -> if(ruledWriting) RuledDiaryWriting(page,face,model,Modifier.fillMaxSize(),visible,handwriting=handwriting) }) {
                InkBoard(page,drawing && !imageEditing && PaperInkPolicy.canDrawOnPaper(tool,erasing,cutting),erasing,inkColor,inkWidth,tool,
                    { model?.ink(page.id,it,face) },Modifier.fillMaxSize(),gestures=!imageEditing,cutting=cutting,selectedInk=selectedInk,onSelectInk=onSelectInk,
                    eraserRadius=eraserRadius,smoothing=smoothing,pressureSensitivity=pressureSensitivity,straightLine=straightLine,ruler=ruler,handwriting=if(ruledWriting) handwriting else null,
                    onImageTap={ id -> handwriting.flush();onSelectImage(id);onImageEdit() },
                    onPaperTap=if(editing && ruledWriting && !imageEditing && !cutting && !erasing) { point -> handwriting.flush();handwriting.place(point.x,point.y) } else null,
                    onZoom={ factor,offset ->
                        if(!ruledWriting) {
                        val next=(zoom*factor).coerceIn(1f,4f)
                        zoom=next;pan=if(next<=1.01f) Offset.Zero else Offset((pan.x+offset.x).coerceIn(-sizeBound(boardW.value,next),sizeBound(boardW.value,next)),(pan.y+offset.y).coerceIn(-sizeBound(boardH.value,next),sizeBound(boardH.value,next)))
                        }
                    })
            }
            }
            if(editing && ruledWriting && showDeleteSticker) {
                PaperEditorGlass(checkNotNull(LocalGlassEnvironment.current)) {
                    DiaryIcon(deleteLabel,R.drawable.paper_trash_2,Modifier.align(Alignment.BottomEnd).padding(8.dp),onClick=onDeleteSticker)
                }
            }
            AnimatedVisibility(padVisible,Modifier.align(Alignment.BottomEnd).graphicsLayer {
                translationX=paperW*1.5f*session.toolTravel;alpha=(1f-session.toolTravel).coerceIn(0f,1f)
            }
                .padding(end=boardW*.065f,bottom=boardW/12)
                .offset { IntOffset(padOffset.x.roundToInt(),padOffset.y.roundToInt()) },
                enter=slideInHorizontally(spring(dampingRatio=.82f,stiffness=420f)) { paperW.roundToInt() },
                exit=slideOutHorizontally(spring(dampingRatio=.9f,stiffness=480f)) { paperW.roundToInt() }) {
                Box(Modifier.size(padSide,padSide+48.dp)) {
                    RuledHandwritingPad(handwriting,tool,inkColor,inkWidth,erasing,eraserRadius,pressureSensitivity,straightLine,
                        Modifier.align(Alignment.BottomCenter).size(padSide),smoothing=smoothing,referenceWidth=paperW)
                    val material=checkNotNull(LocalGlassEnvironment.current)
                    CompositionLocalProvider(LocalGlassEdgeColor provides Color.Black.copy(.48f),LocalGlassInteractionColor provides Color(0xFF8E8E93)) {
                    LiquidGlassEnvironment(paperEditorEnvironment(material)) {
                    GlassButton({},Modifier.align(Alignment.TopCenter).size(44.dp).border(.85.dp,Color.White,GlassGeometry.Circle.shape())
                        .semantics { contentDescription="按住移动田字格" }
                        .onGloballyPositioned { handleCoordinates[0]=it }
                        .pointerInput(page.id,paperW,paperH,padW) {
                            awaitEachGesture {
                                val down=awaitFirstDown(requireUnconsumed=false)
                                var previous=handleCoordinates[0]?.localToRoot(down.position) ?: down.position
                                down.consume()
                                do {
                                    val event=awaitPointerEvent(PointerEventPass.Initial)
                                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                                    val current=handleCoordinates[0]?.localToRoot(change.position) ?: change.position
                                    val delta=current-previous;previous=current;change.consume()
                                    padOffset=Offset((padOffset.x+delta.x).coerceIn(-baseX,paperW-padW-baseX),
                                        (padOffset.y+delta.y).coerceIn(with(density) { 48.dp.toPx() }-baseY,paperH-padW-baseY))
                                } while(event.changes.any { it.pressed })
                            }
                        },geometry=GlassGeometry.Circle) {
                        Canvas(Modifier.align(Alignment.Center).size(20.dp)) {
                            val c=center;val r=size.width*.42f;val line=Color(0xFF1C1C1E);val w=1.5.dp.toPx()
                            drawLine(line,Offset(c.x-r,c.y),Offset(c.x+r,c.y),w,StrokeCap.Round)
                            drawLine(line,Offset(c.x,c.y-r),Offset(c.x,c.y+r),w,StrokeCap.Round)
                            for(i in 0..3) rotate(i*90f,c) {
                                drawLine(line,Offset(c.x+r,c.y),Offset(c.x+r*.6f,c.y-r*.35f),w,StrokeCap.Round)
                                drawLine(line,Offset(c.x+r,c.y),Offset(c.x+r*.6f,c.y+r*.35f),w,StrokeCap.Round)
                            }
                        }
                    }
                    }
                    }
                }
            }
        }
        // The guide is visual only: a full-size accessibility node here would
        // occlude the image and ink hit targets beneath it.
        if(centreGuide) Canvas(Modifier.align(Alignment.Center).size(boardW,boardH).graphicsLayer {
            scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y
        }) {
            val start=if(guideHorizontal) Offset(0f,size.height/2) else Offset(size.width/2,0f)
            val end=if(guideHorizontal) Offset(size.width,size.height/2) else Offset(size.width/2,size.height)
            drawLine(PaperInk.copy(.20f),start,end,strokeWidth=1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),5.dp.toPx())))
        }
    }
}
private fun sizeBound(size:Float,zoom:Float)=size*(zoom-1)*2

@Composable private fun InkBoard(page:DiaryPage,enabled:Boolean,erasing:Boolean,color:Long,width:Float,tool:InkTool,
    onChange:(List<InkStroke>)->Unit,modifier:Modifier,gestures:Boolean,cutting:Boolean,selectedInk:Set<String>,
    onSelectInk:(Set<String>)->Unit,onZoom:(Float,Offset)->Unit,eraserRadius:Float=.035f,smoothing:Float=0f,
    pressureSensitivity:Float=1f,straightLine:Boolean=false,onImageTap:(String)->Unit={},onPaperTap:((Offset)->Unit)?=null,ruler:PaperRulerState?=null,handwriting:RuledWritingBridge?=null) {
    val currentPage by rememberUpdatedState(page)
    val callback by rememberUpdatedState(onChange)
    val transform by rememberUpdatedState(onZoom)
    val selection by rememberUpdatedState(onSelectInk)
    val selectedNow by rememberUpdatedState(selectedInk)
    val imageTap by rememberUpdatedState(onImageTap)
    val paperTap by rememberUpdatedState(onPaperTap)
    var movingInk by remember(page.id) { mutableStateOf(Offset.Zero) }
    // The stroke being traced lives in a plain buffer plus an integer revision. Only the draw
    // phase reads the revision, so every new point repaints the board without recomposing it and
    // without rebuilding a point list per touch event.
    val trace=remember(page.id) { BoundedInkTrace() }
    var revision by remember(page.id) { mutableIntStateOf(0) }
    val ink=if(cutting) page.strokes.filterNot { it.id in selectedInk } else page.strokes
    var raster by remember(page.id) { mutableStateOf<RetainedInkRaster?>(null) }
    DisposableEffect(raster) { val retained=raster;onDispose { retained?.release() } }
    LaunchedEffect(raster,ink) { raster?.accept(ink);revision++ }
    var eraserTip by remember(page.id) { mutableStateOf<Offset?>(null) }
    Canvas(modifier.clipToBounds().onSizeChanged { size ->
        if(size.width>0 && size.height>0 && (raster?.width!=size.width || raster?.height!=size.height)) raster=RetainedInkRaster(size.width,size.height).also { it.accept(ink) }
    }.then(if(gestures) Modifier.semantics { contentDescription="日记画布，${page.strokes.size} 条笔画" } else Modifier)
        .then(if(gestures) Modifier.pointerInput(page.id,enabled,erasing,color,width,tool,cutting,eraserRadius,smoothing,pressureSensitivity,straightLine,raster) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=true)
                if(erasing) handwriting?.flush?.invoke()
                val caret=handwriting?.caret
                if(!erasing && !cutting && caret!=null && abs(down.position.x-caret.x)<max(18.dp.toPx(),size.width*.025f) &&
                    down.position.y in (caret.y-handwriting.fontHeight)..(caret.y+12.dp.toPx())) {
                    val held=awaitLongPressOrCancellation(down.id)
                    if(held!=null) {
                        handwriting.flush();handwriting.dragging=true;held.consume()
                        try {
                            handwriting.dragPoint=held.position;handwriting.moveCursor(held.position.x,held.position.y)
                            do {
                                val event=awaitPointerEvent();val change=event.changes.firstOrNull { it.id==down.id } ?: break
                                handwriting.dragPoint=change.position;handwriting.moveCursor(change.position.x,change.position.y);change.consume()
                            } while(event.changes.any { it.pressed })
                        } finally { handwriting.dragging=false;handwriting.dragPoint=Offset.Unspecified }
                    } else paperTap?.invoke(down.position)
                    return@awaitEachGesture
                }
                val rulerEdge=if(straightLine && !erasing && !cutting) ruler?.edge(down.position,20.dp.toPx()) else null
                val points=mutableListOf<InkPoint>()
                fun point(p:Offset,pressure:Float=1f)=InkPoint((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f),pressure.coerceIn(0f,2f))
                val retained=raster ?: return@awaitEachGesture
                retained.accept(if(cutting) currentPage.strokes.filterNot { it.id in selectedNow } else currentPage.strokes)
                points.add(point(down.position));var multiple=false
                val moving=cutting && currentPage.strokes.any { it.id in selectedNow && it.hits(points.first(),.05f,currentPage.canvasAspect) }
                // A short exponential filter removes the tremor of a finger without adding
                // latency: the pen tip is the raw sample, the ink trails a little behind it.
                var lastSample:InkSample?=null
                fun traceTo(p:Offset,pressure:Float=1f) {
                    val projected=if(rulerEdge!=null) checkNotNull(ruler).project(p,rulerEdge) else p
                    val sample=InkSample(projected.x/size.width,projected.y/size.height,pressure)
                    val before=lastSample
                    val raw=if(smoothing>0f && !erasing && before?.inside==true && sample.inside)
                        InkSample(before.x+(sample.x-before.x)*(1-smoothing),before.y+(sample.y-before.y)*(1-smoothing),pressure) else sample
                    lastSample=raw
                    trace.add(raw)
                    retained.append(trace.paths)
                    if(erasing) {
                        eraserTip=p
                        handwriting?.erase?.invoke(trace.paths,eraserRadius,false)
                    }
                    revision++
                }
                fun clearTrace() { if(trace.paths.isNotEmpty()) { trace.clear();revision++ } }
                var previous=down.position;var previousTime=down.uptimeMillis
                if(enabled) down.consume()
                try {
                    if(enabled) retained.begin(if(erasing && !cutting) InkTool.ERASER else if(cutting) InkTool.FELT else tool,
                        if(cutting) 0xFF6599BC else color,if(erasing && !cutting) eraserRadius else if(cutting) .003f else width)
                    if(enabled && !moving) traceTo(down.position)
                    do {
                        val event=awaitPointerEvent()
                        // A photo/sticker promoted this touch to its transform
                        // gesture in Initial pass. Never also zoom the paper.
                        if(event.changes.any { it.isConsumed }) { multiple=true;clearTrace();retained.cancel();continue }
                        if(event.changes.count { it.pressed }>=2) {
                            multiple=true;clearTrace();retained.cancel()
                            transform(event.calculateZoom(),event.calculatePan());event.changes.forEach { it.consume() }
                        } else if(!multiple && (enabled || paperTap!=null)) {
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            val elapsed=(change.uptimeMillis-previousTime).coerceAtLeast(1)
                            val speed=(change.position-previous).getDistance()/size.width/elapsed
                            val pressure=if(change.type==PointerType.Stylus) change.pressure else PaperPenDynamics.fingerPressure(speed)
                            val p=point(change.position,PaperPenDynamics.pressure(pressure,pressureSensitivity))
                            if(p!=points.last() && points.size<8000) points.add(p)
                            if(moving) movingInk=Offset((change.position.x-down.position.x)/size.width,(change.position.y-down.position.y)/size.height)
                            else if(enabled) traceTo(change.position,p.pressure)
                            previous=change.position;previousTime=change.uptimeMillis;change.consume()
                        }
                    } while(event.changes.any { it.pressed })
                    val image=if(!multiple && !erasing && !cutting && down.type!=PointerType.Stylus &&
                        (points.last().x-points.first().x).let { abs(it)*size.width }<viewConfiguration.touchSlop &&
                        abs(points.last().y-points.first().y)*size.height<viewConfiguration.touchSlop)
                        PaperLayers.ordered(currentPage.stickers).lastOrNull { s ->
                            val angle=-s.rotation*PI.toFloat()/180;val dx=points.first().x-(s.x+s.width/2);val dy=(points.first().y-(s.y+s.height/2))/currentPage.canvasAspect
                            val x=dx*cos(angle)-dy*sin(angle);val y=dx*sin(angle)+dy*cos(angle)
                            abs(x)<=s.width/2 && abs(y)<=s.height/currentPage.canvasAspect/2
                        } else null
                    if(enabled && !multiple && moving) {
                        val moved=currentPage.strokes.filter { it.id in selectedNow }.shifted(movingInk.x,movingInk.y).associateBy { it.id }
                        callback(currentPage.strokes.map { moved[it.id] ?: it })
                    } else if(enabled && image!=null) imageTap(image.id)
                    else if(enabled && !multiple && cutting) selection(currentPage.strokes.filter { stroke -> stroke.points.any { inkInside(points,it) } }.map { it.id }.toSet())
                    else if(!multiple && !erasing && !cutting && paperTap!=null &&
                        points.all { hypot((it.x-points.first().x)*size.width,(it.y-points.first().y)*size.height)<viewConfiguration.touchSlop }) {
                        paperTap?.invoke(down.position)
                    }
                    else if(enabled && !multiple) {
                        val next=retained.strokes+trace.paths.filter { it.isNotEmpty() }.map { path -> InkStroke(color=color,width=if(erasing) eraserRadius else width,
                            points=if(!erasing && tool==InkTool.DIAGRAM) diagram(path,currentPage.canvasAspect) else PaperPenDynamics.points(path.toList(),false),tool=if(erasing) InkTool.ERASER else tool) }
                        callback(next);retained.commit(next)
                        if(erasing) handwriting?.erase?.invoke(trace.paths,eraserRadius,true)
                    }
                } finally { handwriting?.cancelErase?.invoke();retained.cancel();clearTrace();movingInk=Offset.Zero;eraserTip=null }
            }
        } else Modifier)) {
            val frame=revision
            if(frame>=0) raster?.let { drawImage(it.image) }
            if(cutting) drawIntoCanvas { canvas -> page.strokes.filter { it.id in selectedInk }.forEach {
                val moved=listOf(it).shifted(movingInk.x,movingInk.y).single()
                PageArtwork.stroke(canvas.nativeCanvas,moved.copy(color=0xAA6599BC),size.width,size.height)
            } }
            eraserTip?.let { tip -> drawCircle(Color(0xFF54616D).copy(.45f),eraserRadius*size.width,tip,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
        }
}
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.paperGuides(style:PaperStyle) {
    drawIntoCanvas { PageArtwork.guides(it.nativeCanvas,style,size.width,size.height) }
}
