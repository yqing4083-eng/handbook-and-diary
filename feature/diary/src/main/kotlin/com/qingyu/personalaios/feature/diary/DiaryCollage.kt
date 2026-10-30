package com.qingyu.personalaios.feature.diary

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable
internal fun DiaryCollage(page: DiaryPage, enabled: Boolean, onChange: (List<DiarySticker>) -> Unit,
    modifier: Modifier = Modifier,controls:Boolean=true,onSelected:(String?)->Unit={},onActivate:()->Unit={},showNotes:Boolean=true,allowSelection:Boolean=false,ruleScroll:androidx.compose.foundation.ScrollState?=null,selectionReset:Int=0,
    onReady:()->Unit={},beforeStickers:@Composable BoxScope.(List<DiarySticker>)->Unit={},content: @Composable BoxScope.() -> Unit) {
    val latest by rememberUpdatedState(page)
    val update by rememberUpdatedState(onChange)
    val selectable by rememberUpdatedState(enabled || allowSelection)
    val activate by rememberUpdatedState(onActivate)
    var selected by remember(page.id) { mutableStateOf<String?>(null) }
    var previousCount by remember(page.id) { mutableIntStateOf(page.stickers.size) }
    var cutting by remember(page.id) { mutableStateOf(false) }
    var cutPoints by remember(page.id) { mutableStateOf<List<InkPoint>>(emptyList()) }
    var draggingSticker by remember(page.id) { mutableStateOf<DiarySticker?>(null) }
    val density=LocalDensity.current
    LaunchedEffect(page.stickers) { if(page.stickers.any { it==draggingSticker }) draggingSticker=null }
    LaunchedEffect(selected) { onSelected(selected) }
    LaunchedEffect(selectionReset) { if(selectionReset>0) selected=null }
    LaunchedEffect(page.stickers.size) {
        if(page.stickers.size>previousCount) selected=page.stickers.lastOrNull()?.id
        previousCount=page.stickers.size
    }
    fun change(id:String, block:(DiarySticker)->DiarySticker) { update(latest.stickers.map { if(it.id==id) block(it) else it }) }
    fun reorder(id:String,delta:Int) {
        val list=latest.stickers.toMutableList();val from=list.indexOfFirst { it.id==id }
        if(from<0) return
        val to=(from+delta).coerceIn(0,list.lastIndex)
        if(to==from) return
        list.add(to,list.removeAt(from));update(list)
    }
    fun croppable(s:DiarySticker)=s.kind==StickerKind.IMAGE || s.kind==StickerKind.STICKER
    BoxWithConstraints(modifier) {
        val textMeasurer=androidx.compose.ui.text.rememberTextMeasurer()
        val paperWidth=with(density) { maxWidth.toPx() }
        val paperHeight=with(density) { maxHeight.toPx() }
        val pagePhotoPaths=remember(page.stickers) { page.stickers.filter { it.fillPage && it.kind==StickerKind.IMAGE }.map { it.path }.distinct() }
        val photoAspects by produceState<Map<String,Float>>(emptyMap(),pagePhotoPaths) {
            value=withContext(Dispatchers.IO) { pagePhotoPaths.mapNotNull { path ->
                DiaryImages.load(path)?.let { path to it.width.toFloat()/it.height }
            }.toMap() }
        }
        // Text uses the same fitted frame for rendering, hit testing and movement.
        // Old saved kaomoji had a .84-page frame regardless of their actual length.
        val fitted=remember(page.stickers,paperWidth,paperHeight,density,textMeasurer,photoAspects) {
            PaperLayers.ordered(page.stickers).map { s ->
                if(s.fillPage && s.kind==StickerKind.IMAGE) {
                    val aspect=photoAspects[s.path] ?: DiaryImages.peek(s.path)?.let { it.width.toFloat()/it.height }
                    if(aspect==null) s else StickerPlacement.fitPageImage(s,aspect,page.canvasAspect)
                } else if(s.kind!=StickerKind.TEXT || s.text.isEmpty()) s else {
                    val layout=textMeasurer.measure(s.text,TextStyle(fontSize=with(density) { (s.fontSize*paperWidth).toSp() }),
                        constraints=androidx.compose.ui.unit.Constraints(maxWidth=(paperWidth*s.width).roundToInt().coerceAtLeast(1)))
                    val right=(0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: 0f
                    StickerPlacement.fitText(s,ceil(right+2f)/paperWidth,layout.size.height/paperHeight,page.canvasAspect)
                }
            }
        }
        val latestFitted by rememberUpdatedState(fitted)
        // Gesture coordinates belong to the stationary paper, never to the moving
        // sticker. Moving an element cannot move its own gesture coordinate frame.
        BoxWithConstraints(Modifier.fillMaxSize().pointerInput(page.id) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=true,pass=PointerEventPass.Initial)
                val hit=latestFitted.map { draggingSticker?.takeIf { drag -> drag.id==it.id } ?: it }.lastOrNull {
                    (selectable || it.kind==StickerKind.IMAGE && down.type!=PointerType.Stylus) &&
                    StickerPlacement.hit(it,down.position.x/size.width,down.position.y/size.height,latest.canvasAspect)
                }
                if(hit==null) { selected=null;return@awaitEachGesture }
                var ownsGesture=selectable
                if(ownsGesture) { selected=hit.id;activate();down.consume() }
                var current:DiarySticker=hit
                var pending=Offset.Zero;var dragging=false
                if(cutting && croppable(hit)) cutPoints=listOf(StickerPlacement.local(hit,down.position.x/size.width,down.position.y/size.height,latest.canvasAspect).clipped())
                var committed=false
                try {
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    if(!ownsGesture && event.changes.count { it.pressed }>=2 && hit.kind==StickerKind.IMAGE && !hit.locked) {
                        ownsGesture=true;current=latestFitted.firstOrNull { it.id==hit.id } ?: hit
                        selected=hit.id;activate()
                    }
                    if(!ownsGesture) continue
                    val pan=event.calculatePan();val zoom=event.calculateZoom();val turn=event.calculateRotation()
                    pending+=pan
                    if(!dragging && (pending.getDistance()>viewConfiguration.touchSlop || abs(zoom-1f)>.005f || abs(turn)>.3f)) dragging=true
                    if(cutting && croppable(hit)) {
                        event.changes.firstOrNull { it.id==down.id }?.let { c ->
                            cutPoints=cutPoints+StickerPlacement.local(hit,c.position.x/size.width,c.position.y/size.height,latest.canvasAspect).clipped()
                        }
                    } else if(dragging && !hit.locked) {
                        val delta=if(pending!=Offset.Zero) pending else pan;pending=Offset.Zero
                        val centre=event.calculateCentroid(useCurrent=false)
                        current=StickerPlacement.move(current,delta.x/size.width,delta.y/size.height,zoom,turn,
                            if(centre.isSpecified) centre.x/size.width else current.x+current.width/2,
                            if(centre.isSpecified) centre.y/size.height else current.y+current.height/2,latest.canvasAspect)
                        draggingSticker=current
                    }
                    event.changes.forEach { it.consume() }
                } while(event.changes.any { it.pressed })
                if(ownsGesture && dragging && !cutting && !hit.locked) { change(hit.id) { current };committed=true }
                if(ownsGesture && cutting && croppable(hit) && cutPoints.size>2) { change(hit.id) { it.copy(crop=cutPoints) };cutPoints=emptyList();cutting=false }
                } finally { if(!committed) draggingSticker=null }
            }
        }) {
            val noteWidth=with(density) { maxWidth.roundToPx() }
            val note by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,page.id,page.title,page.body,page.bodyHtml,page.canvasAspect,showNotes,noteWidth) {
                value=if(showNotes) withContext(Dispatchers.IO) { PageArtwork.noteBitmap(page,noteWidth)?.asImageBitmap() } else null
            }
            val mediaPaths=remember(page.stickers,page.wallpaper) { (page.stickers.map { it.path }+page.wallpaper).filter { it.isNotBlank() }.distinct() }
            val mediaReady by produceState(false,mediaPaths) {
                withContext(Dispatchers.IO) { mediaPaths.forEach { DiaryImages.load(it) } };value=true
            }
            val readyCallback by rememberUpdatedState(onReady)
            LaunchedEffect(mediaReady,note,showNotes,page.title,page.body) {
                if(mediaReady && (!showNotes || page.title.isBlank() && page.body.isBlank() || note!=null)) {
                    withFrameNanos {};readyCallback()
                }
            }
            val visibleStickers=fitted.map { draggingSticker?.takeIf { drag -> drag.id==it.id } ?: it }
            val wallpaper by produceState<androidx.compose.ui.graphics.ImageBitmap?>(DiaryImages.peek(page.wallpaper)?.asImageBitmap(),page.wallpaper) {
                value=withContext(Dispatchers.IO) { DiaryImages.load(page.wallpaper)?.asImageBitmap() }
            }
            wallpaper?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
            Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { PageArtwork.guides(it.nativeCanvas,page.style,size.width,size.height,ruleScroll?.value ?: 0) } }
            if(showNotes) note?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds) }
            beforeStickers(visibleStickers)
            fun Modifier.movingLayer(s:DiarySticker)=graphicsLayer {
                val current=draggingSticker?.takeIf { it.id==s.id } ?: s
                translationX=(current.x-s.x+(current.width-s.width)/2)*paperWidth
                translationY=(current.y-s.y+(current.height-s.height)/2)*paperHeight
                scaleX=current.width/s.width;scaleY=current.height/s.height
                rotationZ=current.rotation;alpha=current.alpha
            }
            @Composable fun renderElements(elements:List<DiarySticker>) {
            elements.forEach { s ->
                val box=Modifier.offset(maxWidth*s.x,maxHeight*s.y).wrapContentSize(Alignment.TopStart,unbounded=true)
                    .requiredSize(maxWidth*s.width,maxHeight*s.height).movingLayer(s)
                    .clip(remember(s.crop) { CropShape(s.crop) })
                when(s.kind) {
                    StickerKind.TEXT -> {
                        val style=TextStyle(color=Color(s.color),fontSize=with(density){ (s.fontSize*maxWidth.toPx()).toSp() })
                        BasicText(s.text,box,style=style)
                    }
                    StickerKind.TAPE -> Canvas(box) {
                        drawIntoCanvas { PageArtwork.tape(it.nativeCanvas,s,RectF(0f,0f,size.width,size.height),Paint().apply { alpha=255 }) }
                    }
                    else -> {
                        val bitmap by key(s.path) { produceState<androidx.compose.ui.graphics.ImageBitmap?>(DiaryImages.peek(s.path)?.asImageBitmap(),s.path) {
                            value=withContext(Dispatchers.IO) { runCatching { DiaryImages.load(s.path)?.asImageBitmap() }.getOrNull() }
                        } }
                        bitmap?.let { Image(it,null,box,contentScale=if(s.fillPage && s.kind==StickerKind.IMAGE) ContentScale.Crop else ContentScale.FillBounds) }
                    }
                }
            }
            }
            renderElements(PaperLayers.base(fitted))
            content()
            renderElements(PaperLayers.top(fitted))
            // Selection is above ink/text; it uses exactly the image's transformed
            // rectangle plus its alpha/crop contour, without transparent padding.
            fitted.forEachIndexed { index,s ->
                val bitmap by key(s.path) { produceState<android.graphics.Bitmap?>(DiaryImages.peek(s.path),s.path) {
                    if(s.path.isNotBlank()) value=withContext(Dispatchers.IO) { runCatching { DiaryImages.load(s.path) }.getOrNull() }
                } }
                val outline by produceState<android.graphics.Path?>(null,bitmap,s.crop,s.fillPage,s.kind) {
                    value=withContext(Dispatchers.Default) { StickerContour.create(bitmap,s.crop,s.fillPage) }
                }
                val textLayout=if(s.kind==StickerKind.TEXT) androidx.compose.ui.text.rememberTextMeasurer().measure(s.text,
                    TextStyle(color=Color(s.color),fontSize=with(density) { (s.fontSize*maxWidth.toPx()).toSp() }),
                    constraints=androidx.compose.ui.unit.Constraints(maxWidth=with(density) { (maxWidth*s.width).roundToPx() }.coerceAtLeast(1))) else null
                Box(Modifier.offset(maxWidth*s.x,maxHeight*s.y).wrapContentSize(Alignment.TopStart,unbounded=true)
                    .requiredSize(maxWidth*s.width,maxHeight*s.height).movingLayer(s)
                    .semantics {
                        contentDescription="${elementName(s)} ${index+1}${if(enabled && selected==s.id) "，已选中" else ""}"
                        onClick { selected=s.id;activate();true }
                    }.drawWithCache {
                        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF007AFF.toInt();style=Paint.Style.STROKE;strokeWidth=1.5.dp.toPx() }
                        val path=android.graphics.Path(outline ?: android.graphics.Path().apply { addRect(0f,0f,1f,1f,android.graphics.Path.Direction.CW) })
                        path.transform(android.graphics.Matrix().apply { setScale(size.width,size.height) })
                        onDrawBehind { if(enabled && selected==s.id) drawIntoCanvas {
                            if(textLayout!=null) {
                                val right=(0 until textLayout.lineCount).maxOfOrNull { textLayout.getLineRight(it) } ?: 0f
                                it.nativeCanvas.drawRect(0f,0f,right.coerceAtMost(size.width),textLayout.size.height.toFloat().coerceAtMost(size.height),paint)
                            } else {
                                it.nativeCanvas.drawPath(path,Paint(paint).apply { style=Paint.Style.FILL;alpha=22 })
                                it.nativeCanvas.drawPath(path,paint)
                            }
                        } }
                    })
            }
        }
        if(enabled && controls && page.stickers.isNotEmpty()) {
            val current=selected?.let { id -> latest.stickers.find { it.id==id } }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                when(current?.kind) {
                    StickerKind.TAPE -> {
                        DiaryControl("换图案") { selected?.let { id -> change(id) { it.copy(tape=TapePattern.entries[(it.tape.ordinal+1)%TapePattern.entries.size]) } } }
                        DiaryControl("变长") { selected?.let { id -> change(id) { it.transformed(zoom=1.2f) } } }
                        DiaryControl("变短") { selected?.let { id -> change(id) { it.transformed(zoom=.83f) } } }
                        DiaryControl("旋转胶带") { selected?.let { id -> change(id) { it.transformed(rotationDelta=15f) } } }
                    }
                    StickerKind.TEXT -> {
                        DiaryControl("字号放大") { selected?.let { id -> change(id) { it.copy(fontSize=(it.fontSize*1.18f).coerceIn(.015f,.4f)) } } }
                        DiaryControl("字号缩小") { selected?.let { id -> change(id) { it.copy(fontSize=(it.fontSize/1.18f).coerceIn(.015f,.4f)) } } }
                        DiaryControl("文字换色") { selected?.let { id -> change(id) { it.copy(color=nextInkColor(it.color)) } } }
                        DiaryControl("完成文字") { selected=null }
                    }
                    StickerKind.IMAGE, StickerKind.STICKER -> {
                        DiaryControl(if(cutting) "沿图片画裁剪轮廓" else "裁剪贴图") { cutting=!cutting }
                        DiaryControl("还原裁剪") { selected?.let { id -> change(id) { it.copy(crop=emptyList()) } } }
                        DiaryControl("旋转贴图") { selected?.let { id -> change(id) { it.transformed(rotationDelta=15f) } } }
                        DiaryControl("缩小贴图") { selected?.let { id -> change(id) { it.transformed(zoom=.85f) } } }
                    }
                    null -> Unit
                }
                DiaryControl("透明度") { selected?.let { id -> change(id) { it.copy(alpha=nextAlpha(it.alpha)) } } }
                DiaryControl("上移一层") { selected?.let { id -> reorder(id,1) } }
                DiaryControl("下移一层") { selected?.let { id -> reorder(id,-1) } }
                DiaryControl("置顶") { selected?.let { id -> val s=latest.stickers.find { it.id==id };if(s!=null) update(latest.stickers.filterNot { it.id==id }+s) } }
                DiaryControl("置底") { selected?.let { id -> val s=latest.stickers.find { it.id==id };if(s!=null) update(listOf(s)+latest.stickers.filterNot { it.id==id }) } }
                DiaryControl("移除") { selected?.let { id -> update(latest.stickers.filterNot { it.id==id });selected=null } }
            }
        }
    }
}

internal fun elementName(s:DiarySticker)=when(s.kind) {
    StickerKind.TAPE -> "胶带"
    StickerKind.TEXT -> "文字"
    StickerKind.STICKER -> "贴纸"
    StickerKind.IMAGE -> "贴图"
}

/** Alpha and colour walk small fixed ladders, so one button covers "make it lighter". */
internal fun nextAlpha(alpha:Float):Float {
    val steps=listOf(1f,.85f,.65f,.45f,.25f)
    return steps[(steps.indexOfFirst { abs(it-alpha)<.01f }+1)%steps.size]
}

internal fun nextInkColor(color:Long):Long {
    val palette=listOf(0xFF243749,0xFFB4553F,0xFF3F6FA8,0xFF2F7A56,0xFF8A5AA8,0xFFC08A2E)
    return palette[(palette.indexOf(color)+1)%palette.size]
}

private class CropShape(private val points:List<InkPoint>):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        if(points.size<3) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f,0f,size.width,size.height))
        return Outline.Generic(Path().apply { points.forEachIndexed { i,p -> if(i==0) moveTo(p.x*size.width,p.y*size.height) else lineTo(p.x*size.width,p.y*size.height) };close() })
    }
}
