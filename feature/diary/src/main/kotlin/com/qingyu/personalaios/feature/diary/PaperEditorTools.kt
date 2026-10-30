@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.qingyu.personalaios.feature.diary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.domain.InkTool
import com.qingyu.personalaios.domain.InkPoint
import com.qingyu.personalaios.domain.InkStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.launch

/** High resolution reference-restored tools. Rigid bodies slide toward the
 * paper; only the settings surface expands, never the pen itself. */
@Composable internal fun BoxScope.PaperEditorTools(tool:InkTool,erasing:Boolean,cutting:Boolean,
    imageEditing:Boolean,color:Long,width:Float,eraserSize:Float,canType:Boolean,hasImage:Boolean,
    opacity:Float,pressure:Float,straight:Boolean,
    onTool:(InkTool,Boolean,Boolean)->Unit,onColor:(Long)->Unit,onWidth:(Float)->Unit,onEraser:(Float)->Unit,
    onOpacity:(Float)->Unit,onPressure:(Float)->Unit,onStraight:(Boolean)->Unit,
    onBack:()->Unit,onDone:()->Unit,onUndo:()->Unit,onRedo:()->Unit,onImage:()->Unit,
    onText:()->Unit,onDeleteImage:()->Unit,portraitEditor:Boolean=false,textEditing:Boolean=false,
    stickerLocked:Boolean=false,onLock:()->Unit={},onToolbarBounds:(Rect)->Unit={},onSticker:()->Unit={},onTrayBounds:(Rect)->Unit={},toolColors:LongArray?=null,hasInkSelection:Boolean=false) {
    var settings by remember { mutableStateOf("") }
    val session=LocalPaperEditorSession.current
    val hideTools=Modifier.graphicsLayer {
        if(portraitEditor) translationY=size.height*1.5f*session.toolTravel else translationX=size.width*1.5f*session.toolTravel
        alpha=(1f-session.toolTravel).coerceIn(0f,1f)
    }.pointerInput(session.wallpapers) {
        if(session.wallpapers) awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false,pass=androidx.compose.ui.input.pointer.PointerEventPass.Initial).consume()
            do { val event=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial);event.changes.forEach { it.consume() } } while(event.changes.any { it.pressed })
        }
    }
    LaunchedEffect(session.wallpapers) { if(session.wallpapers) settings="" }
    var spectrum by remember { mutableStateOf(false) }
    var armedPen by remember { mutableIntStateOf(-1) }
    val recent=remember { mutableStateListOf<Long>() }
    val dockScroll=rememberScrollState()
    BackHandler(settings.isNotEmpty()) { settings="" }
    val labels=listOf("签字笔","针筒笔","水彩笔","橡皮","套索","直尺","蜡笔","钢笔","墨水笔")
    val tools=listOf(InkTool.FELT,InkTool.TECHNICAL,InkTool.WATERCOLOR,tool,tool,tool,InkTool.CRAYON,InkTool.FOUNTAIN,InkTool.INK)
    val atlas=ImageBitmap.imageResource(R.drawable.editor_tools_atlas)
    val sprites=remember(atlas) { ToolSpriteAtlas.extract(atlas.asAndroidBitmap()) }
    val tips=remember(sprites) { ToolSpriteAtlas.tips(sprites) }
    val bands=remember(sprites) { ToolSpriteAtlas.bands(sprites) }
    val density=LocalDensity.current
    val nibOffset=with(density) { 4.dp.toPx() }
    val sourceSize=with(density) { 20.dp.toPx() }
    val penOrigin:(Rect)->Rect={ bounds ->
        val tip=if(portraitEditor) Offset(bounds.center.x,bounds.top+nibOffset-if(armedPen>=0) with(density) { 9.dp.toPx() } else 0f) else Offset(bounds.left+nibOffset-if(armedPen>=0) with(density) { 9.dp.toPx() } else 0f,bounds.center.y)
        Rect(tip-Offset(sourceSize/2,sourceSize/2),Size(sourceSize,sourceSize))
    }
    val selected=if(imageEditing || textEditing) -1 else if(erasing) 3 else if(cutting) 4 else when(tool) {
        InkTool.FELT -> 0;InkTool.TECHNICAL -> 1;InkTool.WATERCOLOR -> 2;InkTool.CRAYON,InkTool.PENCIL -> 6;InkTool.FOUNTAIN -> 7;InkTool.INK -> 8;else -> -1
    }
    if(portraitEditor) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
        .padding(horizontal=8.dp,vertical=8.dp).onGloballyPositioned { onToolbarBounds(it.boundsInRoot()) },
        horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        DiaryIcon(if(session.wallpapers) "取消纸页壁纸" else "关闭编辑",R.drawable.paper_x,onClick=if(session.wallpapers) session.onCancel else onBack)
        if(!session.wallpapers) {
        PaperUndoRedo(onUndo,onRedo)
        Spacer(Modifier.weight(1f))
        DiaryIcon("更换纸页壁纸",R.drawable.paper_wallpaper,onClick=session.onWallpapers)
        DiaryIcon(if(stickerLocked) "解锁贴纸" else "锁定贴纸",if(stickerLocked) R.drawable.paper_lock else R.drawable.paper_unlock,
            Modifier.alpha(if(hasImage) 1f else .45f),onClick={ if(hasImage) onLock() })
        DiaryIcon(if(portraitEditor) "添加贴纸" else "添加图片",if(portraitEditor) R.drawable.paper_sticker else R.drawable.paper_image,onClick=onImage)
        } else Spacer(Modifier.weight(1f))
        DiaryIcon(if(session.wallpapers) "确认纸页壁纸" else "完成编辑",R.drawable.paper_check,onClick=if(session.wallpapers) session.onConfirm else onDone)
    }
    if(!portraitEditor) Box(Modifier.align(Alignment.TopStart).fillMaxHeight()
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start+WindowInsetsSides.Top+WindowInsetsSides.Bottom))
        .padding(start=8.dp,top=8.dp,bottom=8.dp).width(142.dp)) {
        Column(Modifier.align(Alignment.TopStart).onGloballyPositioned { onToolbarBounds(it.boundsInRoot()) },
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                DiaryIcon("完成编辑",R.drawable.paper_check,onClick=onDone)
                PaperUndoRedo(onUndo,onRedo)
            }
            DiaryIcon(if(stickerLocked) "解锁贴纸" else "锁定贴纸",if(stickerLocked) R.drawable.paper_lock else R.drawable.paper_unlock,
                Modifier.alpha(if(hasImage) 1f else .45f),onClick={ if(hasImage) onLock() })
            DiaryIcon("添加贴纸",R.drawable.paper_sticker,onClick=onSticker)
            DiaryIcon("添加图片",R.drawable.paper_image,onClick=onImage)
            if(hasImage || hasInkSelection) DiaryIcon(if(hasInkSelection) "删除笔迹" else "移除图片",R.drawable.paper_trash_2,onClick=onDeleteImage)
        }
        DiaryIcon("关闭编辑",R.drawable.paper_x,Modifier.align(Alignment.BottomStart),onClick=onBack)
    }
    val toolCell:@Composable (Int,Dp,Dp,Boolean)->Unit={ index,cellW,cellH,vertical ->
        val tap=remember { Animatable(0f) };val toolScope=rememberCoroutineScope()
        val chosen=if(index==5) straight else selected==index
        val extension by animateFloatAsState(if(chosen && (index==5 || armedPen==index)) -9f else 0f,spring(.6f,550f),label="pen extension")
        GlassMenuAnchor(onClick={
            toolScope.launch { tap.snapTo(-3f);tap.animateTo(0f,spring(.4f,650f)) }
            if(index==5) { settings="";onStraight(!straight) }
            else if(selected==index && armedPen==index) settings=if(settings=="width") "" else "width"
            else { armedPen=index;settings="";onTool(tools[index],index==3,index==4) }
        },modifier=Modifier.size(cellW,cellH).semantics { contentDescription=labels[index];this.selected=chosen },launchBounds=penOrigin,geometry=GlassGeometry.Circle) {
            val body=Modifier.fillMaxSize().graphicsLayer {
                if(vertical) translationY=(tap.value+extension).dp.toPx() else translationX=(tap.value+extension).dp.toPx()
            }
            val nibColor=if(index==selected) color else toolColors?.getOrNull(tools[index].ordinal) ?: if(index==2) 0xFFFFC400L else 0xFF293844L
            ReferenceToolSprite(sprites[index],tips[index],bands[index],nibColor,body,vertical)
            if(chosen) Canvas(Modifier.align(if(vertical) Alignment.BottomCenter else Alignment.CenterEnd).padding(bottom=if(vertical) 3.dp else 0.dp,end=if(vertical) 0.dp else 3.dp).size(4.dp)) { drawCircle(Color(0xFF007AFF)) }
        }
    }
    val colorCell:@Composable (Dp,Dp)->Unit={ cellW,cellH ->
        GlassMenuAnchor(onClick={ val target=if(erasing) "width" else "color";settings=if(settings==target) "" else target },
            modifier=Modifier.size(cellW,cellH).semantics { contentDescription=if(erasing) "橡皮大小调节" else "画笔颜色" },
            launchBounds={ bounds -> val diameter=with(density) { 28.dp.toPx() };Rect(bounds.center-Offset(diameter/2,diameter/2),Size(diameter,diameter)) },geometry=GlassGeometry.Circle) {
            Canvas(Modifier.align(Alignment.Center).size(28.dp)) {
                if(erasing) {
                    drawCircle(Color(0xFF626268),13.dp.toPx(),style=Stroke(1.dp.toPx()))
                    drawCircle(Color(0xFF626268),(4f+((eraserSize-.008f)/.092f).coerceIn(0f,1f)*5f).dp.toPx(),style=Stroke(1.5.dp.toPx()))
                } else {
                    drawCircle(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)),14.dp.toPx(),style=Stroke(2.dp.toPx()))
                    drawCircle(Color(color),10.dp.toPx())
                }
            }
        }
    }
    if(portraitEditor) {
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal))
            .height(100.dp).then(hideTools).padding(start=10.dp,end=10.dp,bottom=8.dp).onGloballyPositioned {
                val rect=it.boundsInRoot();onTrayBounds(Rect(rect.left,rect.top-with(density) { 24.dp.toPx() },rect.right,rect.bottom))
            }) {
            val slot=((maxWidth-8.dp)/6).coerceIn(32.dp,64.dp)
            PaperPenDock(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(84.dp)) {
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.Bottom) {
                    Row(Modifier.weight(1f).horizontalScroll(dockScroll)) { labels.indices.forEach { toolCell(it,slot,72.dp,true) } }
                    colorCell(slot,72.dp)
                }
            }
        }
    } else BoxWithConstraints(Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        .windowInsetsPadding(WindowInsets.systemBars
            .union(WindowInsets.displayCutout).only(WindowInsetsSides.Top+WindowInsetsSides.Bottom+WindowInsetsSides.End))
        .padding(top=8.dp,bottom=8.dp,end=8.dp).width(84.dp).then(hideTools).onGloballyPositioned { onTrayBounds(it.boundsInRoot()) }) {
        // Match portrait's five visible pens plus colour. Overflow scrolls;
        // squeezing all nine tools into one column made the spacing too small.
        val pitch=((maxHeight-8.dp)/6).coerceIn(32.dp,64.dp)
        PaperPenDock(Modifier.align(Alignment.CenterEnd).width(84.dp).fillMaxHeight()) {
            Column(Modifier.align(Alignment.CenterEnd).width(72.dp).fillMaxHeight().padding(vertical=4.dp)) {
                Column(Modifier.weight(1f).verticalScroll(dockScroll)) { labels.indices.forEach { toolCell(it,72.dp,pitch,false) } }
                colorCell(72.dp,pitch)
            }
        }
    }
    val home=LocalPaperToolGlassEnvironment.current ?: checkNotNull(LocalGlassEnvironment.current)
    LiquidGlassEnvironment(home.copy(groups=home.groups.copy(buttons=home.groups.buttons.copy(blur=3f)),surfaceTintOverride=null,layeredTintOverride=null)) {
    GlassPanelTransition(settings,vanishOnClose=true) { active ->
        if(active.isNotEmpty()) GlassOptionsPopup(onClose={ settings="" },width=if(active=="color") 240.dp else 224.dp,pointer=false,sideways=!portraitEditor,cornerRadius=30.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(active=="color") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                    listOf("网格","光谱").forEachIndexed { index,label ->
                        SpaceText(label,12.sp,Modifier.background(if(spectrum==(index==1)) Color(0xFFE7EDF2) else Color.Transparent,AppShapes.Small)
                            .clickable(interactionSource=null,indication=null) { spectrum=index==1 }.padding(horizontal=16.dp,vertical=6.dp),
                            alpha=if(spectrum==(index==1)) 1f else .55f)
                    }
                }
                PaperColorField(spectrum) { ink ->
                    onColor(ink);recent.remove(ink);recent.add(0,ink);if(recent.size>8) recent.removeAt(8)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    recent.take(8).forEach { ink -> PenColorDot(ink,color,18) { onColor(ink) } }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf(0xFF151719L,0xFFFA626CL,0xFFFFD65DL,0xFF74CE7FL,0xFF489EEDL,0xFFFFFFFFL).forEach { ink ->
                        PenColorDot(ink,color,27) { onColor(ink) }
                    }
                }
            } else if(cutting) {
                SpaceText("套索",13.sp,Modifier.align(Alignment.CenterHorizontally))
                SpaceText("圈选后拖动，或清除圈选笔画",12.sp,alpha=.7f)
                GlassMenuOption("清除圈选笔画",onClick={ onDeleteImage();settings="" })
            } else {
                ReferenceBrushPanel(tool,erasing,color,width,eraserSize,opacity,onWidth,onEraser,onOpacity)

            }
        }
    }
    }
    }
}

