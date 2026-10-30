package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.delay

/** Shared with native typing; one insertion cursor and one saved vector document. */
internal class RuledWritingBridge {
    var openKeyboard:(String,Int)->Unit={ _,_-> }
    var sourceWidthScale=1f/.56f
    var cursor by mutableStateOf<Int?>(null)
    var insert:(List<InkStroke>)->Boolean={ false }
    var place:(Float,Float)->Unit={ _,_-> }
    var flush:()->Unit={}
    var caret by mutableStateOf<Offset?>(null)
    var fontHeight=0f
    var dragging by mutableStateOf(false)
    var dragPoint by mutableStateOf(Offset.Unspecified)
    var moveCursor:(Float,Float)->Unit={ _,_-> }
    var freeCaret by mutableStateOf<Offset?>(null)
    var erase:(List<List<InkPoint>>,Float,Boolean)->Unit={ _,_,_-> }
    var cancelErase:()->Unit={}
}

/** The reference is a square with cross guides, a 700 ms inter-character pause,
 * and original ink transported to the paper. It never recognizes/replaces handwriting. */
@Composable internal fun RuledHandwritingPad(bridge:RuledWritingBridge,tool:InkTool,color:Long,width:Float,
    erasing:Boolean,eraserRadius:Float,pressure:Float,straight:Boolean,modifier:Modifier=Modifier,smoothing:Float=0f,referenceWidth:Float=0f) {
    val strokes=remember(bridge) { mutableStateListOf<InkStroke>() }
    val trace=remember(bridge) { BoundedInkTrace() }
    var revision by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var raster by remember { mutableStateOf<RetainedInkRaster?>(null) }
    val latestTool by rememberUpdatedState(tool)
    val inkActivity by rememberUpdatedState(LocalPaperEditorSession.current.onInkActivity)
    val latestColor by rememberUpdatedState(color)
    val latestWidth by rememberUpdatedState(width)
    val latestErase by rememberUpdatedState(erasing)
    val latestRadius by rememberUpdatedState(eraserRadius)
    val latestPressure by rememberUpdatedState(pressure)
    val latestSmoothing by rememberUpdatedState(smoothing)
    val latestReferenceWidth by rememberUpdatedState(referenceWidth)
    LaunchedEffect(referenceWidth,raster) { raster?.let { it.inkWidthScale=if(referenceWidth>0) referenceWidth/it.width else 1f;revision++ } }
    fun commit(clearRaster:Boolean=true) {
        if(touching || strokes.isEmpty()) return
        if(strokes.all { it.tool==InkTool.ERASER } || bridge.insert(strokes.toList())) {
            strokes.clear();if(clearRaster) raster?.accept(emptyList());revision++
        }
    }
    SideEffect { bridge.flush={ commit() };bridge.sourceWidthScale=raster?.inkWidthScale ?: 1f/.56f }
    // Raster disposal order must not affect rotation/background saves.
    DisposableEffect(bridge) { onDispose { commit(clearRaster=false);bridge.flush={} } }
    DisposableEffect(raster) { val current=raster;onDispose { current?.release() } }
    LaunchedEffect(revision,touching) {
        if(!touching && strokes.isNotEmpty()) { delay(700);commit() }
    }
    Canvas(modifier.background(Color.White.copy(.97f)).border(.6.dp,Color(0xFFABB2B8).copy(.6f))
        .semantics { contentDescription="田字格手写，停顿后保留原笔迹写入虚线" }
        .onSizeChanged { size ->
            if(size.width>0 && size.height>0 && (raster?.width!=size.width || raster?.height!=size.height))
                raster=RetainedInkRaster(size.width,size.height).also { it.inkWidthScale=if(referenceWidth>0) referenceWidth/size.width else 1f;it.accept(strokes.toList()) }
        }.pointerInput(bridge,raster) {
            awaitEachGesture {
                val down=awaitFirstDown();val retained=raster ?: return@awaitEachGesture
                touching=true;down.consume();trace.clear();inkActivity()
                val erase=latestErase;val inkTool=if(erase) InkTool.ERASER else latestTool
                val brushWidth=if(erase) latestRadius else latestWidth.coerceIn(.001f,.05f)
                retained.begin(inkTool,latestColor,brushWidth)
                var previous=down.position;var previousTime=down.uptimeMillis;var lastSample:InkSample?=null
                fun add(change:PointerInputChange) {
                    val speed=(change.position-previous).getDistance()/(latestReferenceWidth.takeIf { it>0 } ?: size.width.toFloat())/(change.uptimeMillis-previousTime).coerceAtLeast(1)
                    val p=if(change.type==PointerType.Stylus) change.pressure else if(change===down) 1f else PaperPenDynamics.fingerPressure(speed)
                    val sample=InkSample(change.position.x/size.width,change.position.y/size.height,if(change===down) 1f else PaperPenDynamics.pressure(p,latestPressure))
                    val before=lastSample
                    val raw=if(latestSmoothing>0f && !erase && before?.inside==true && sample.inside)
                        InkSample(before.x+(sample.x-before.x)*(1-latestSmoothing),before.y+(sample.y-before.y)*(1-latestSmoothing),sample.pressure) else sample
                    lastSample=raw;previous=change.position;previousTime=change.uptimeMillis
                    trace.add(raw)
                    retained.append(trace.paths);revision++
                }
                var cancelled=false
                try {
                    add(down)
                    do {
                        val event=awaitPointerEvent()
                        if(event.changes.count { it.pressed }>1) { cancelled=true;break }
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        add(change);change.consume()
                    } while(event.changes.any { it.pressed })
                    if(!cancelled) {
                        val next=strokes.toList()+trace.paths.filter { it.isNotEmpty() }.map {
                            InkStroke(color=latestColor,width=brushWidth,points=it.toList(),tool=inkTool)
                        }
                        strokes.clear();strokes.addAll(next);retained.commit(next);retained.accept(next)
                    }
                } finally { retained.cancel();trace.clear();touching=false;revision++ }
            }
        }) {
        val dash=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx()))
        val line=Color(0xFFABB2B8).copy(.6f)
        drawLine(line,Offset(size.width/2,0f),Offset(size.width/2,size.height),.5.dp.toPx(),pathEffect=dash)
        drawLine(line,Offset(0f,size.height/2),Offset(size.width,size.height/2),.5.dp.toPx(),pathEffect=dash)
        drawLine(line.copy(.3f),Offset.Zero,Offset(size.width,size.height),.5.dp.toPx(),pathEffect=dash)
        drawLine(line.copy(.3f),Offset(size.width,0f),Offset(0f,size.height),.5.dp.toPx(),pathEffect=dash)
        val frame=revision
        if(frame>=0) raster?.let { drawImage(it.image) }
    }
}
