package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.ui.*
import kotlin.math.*

val LocalGlassOptions = staticCompositionLocalOf { false }

/** One minimal row inside the shared glass body, with no extra blur/render pass per option. */
@Composable fun GlassMenuOption(label:String,onClick:()->Unit,modifier:Modifier=Modifier,icon:@Composable (() ->Unit)?=null,centered:Boolean=false) {
    val frame=LocalGlassMorph.current
    var location by remember { mutableStateOf(Rect.Zero) }
    val color=LocalSpaceColors.current.foreground
    val distance=frame?.target?.let { (it.bottom-location.bottom)/max(1f,location.height) } ?: 0f
    val ready=frame==null || LiquidMenuMotion.optionReveal(frame.visibility.value,distance)>=.85f && !frame.returning
    Row(modifier.heightIn(min=48.dp).onGloballyPositioned { location=it.boundsInRoot() }
        .graphicsLayer {
            val distance=frame?.target?.let { (it.bottom-location.bottom)/max(1f,location.height) } ?: 0f
            val q=if(frame==null) 1f else LiquidMenuMotion.optionReveal(frame.visibility.value,distance)
            alpha=q;translationY=(1f-q)*8.dp.toPx()
        }.clickable(enabled=ready,interactionSource=null,indication=null,role=Role.Button,onClick=onClick)
        .padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(centered) Arrangement.Center else Arrangement.spacedBy(12.dp)) {
        if(!centered && icon!=null) Box(Modifier.size(AppControls.Icon),contentAlignment=Alignment.Center) { icon() }
        else if(!centered) Canvas(Modifier.size(AppControls.Icon)) {
            val w=1.6.dp.toPx()
            fun point(x:Float,y:Float)=Offset(size.width*x,size.height*y)
            fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(color,point(x1,y1),point(x2,y2),w,StrokeCap.Round)
            when {
                label.contains("删除") -> {
                    line(.18f,.28f,.82f,.28f);line(.38f,.12f,.62f,.12f)
                    line(.25f,.28f,.3f,.88f);line(.75f,.28f,.7f,.88f);line(.3f,.88f,.7f,.88f)
                    line(.43f,.43f,.43f,.72f);line(.57f,.43f,.57f,.72f)
                }
                label.contains("允许") || label.contains("保存") -> { line(.18f,.53f,.4f,.75f);line(.4f,.75f,.82f,.25f) }
                label.contains("稍后") || label.contains("保留") || label.contains("取消") -> { line(.25f,.25f,.75f,.75f);line(.75f,.25f,.25f,.75f) }
                label.contains("复制") -> {
                    drawRect(color,point(.18f,.18f),Size(size.width*.48f,size.height*.48f),style=Stroke(w))
                    drawRect(color,point(.36f,.36f),Size(size.width*.48f,size.height*.48f),style=Stroke(w))
                }
                label.contains("导出") || label.contains("备份") || label.contains("上传") || label.contains("导入") -> {
                    val down=label.contains("导入")
                    val top=if(down) .66f else .12f;val tip=if(down) .48f else .3f
                    line(.5f,top,.5f,if(down) .12f else .66f);line(.5f,top,.3f,tip);line(.5f,top,.7f,tip)
                    line(.15f,.58f,.15f,.87f);line(.15f,.87f,.85f,.87f);line(.85f,.87f,.85f,.58f)
                }
                else -> drawCircle(color,size.minDimension*.32f,style=Stroke(w))
            }
        }
        SpaceText(label,AppTypography.Menu)
    }
}

/** Anchor menu to its source button, then clamp it to the visible safe page. */
@Composable fun GlassOptionsPopup(onClose:()->Unit,width:Dp=Dp.Unspecified,pointer:Boolean=false,scrollable:Boolean=true,sideways:Boolean=false,compactPointer:Boolean=false,cornerRadius:Dp=26.dp,preferBelow:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    val density=LocalDensity.current
    val frame=LocalGlassMorph.current
    val launch=LocalGlassLaunch.current?.value
    var root by remember { mutableStateOf(Rect.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val bottomInset=LocalPageBottomInset.current
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { root=it.boundsInRoot() }
        .clickable(interactionSource=null,indication=null,onClick=onClose)) {
        // Compact options need room for an icon, gap and text even at larger system font sizes.
        // Forms retain their requested width. Both are capped to the current viewport.
        val sizing=if(width==Dp.Unspecified) Modifier.widthIn(min=96.dp,max=maxWidth-32.dp).width(IntrinsicSize.Max) else Modifier.width(minOf(width,maxWidth-32.dp))
        val padding=with(density) { 16.dp.toPx() }
        val anchor=(frame?.returnAnchor ?: launch?.bounds ?: Rect(root.center,Size.Zero)).translate(-root.topLeft)
        val safe=Rect(padding,padding,max(padding,root.width-padding),max(padding,root.height-padding-with(density) { bottomInset.toPx() }))
        val onLeft=anchor.center.x>safe.center.x
        val point=if(sideways) Offset(
            (if(onLeft) anchor.center.x-with(density) { 6.dp.toPx() }-menuSize.width else anchor.center.x+with(density) { 6.dp.toPx() })
                .coerceIn(safe.left,max(safe.left,safe.right-menuSize.width)),
            (anchor.center.y-menuSize.height/2).coerceIn(safe.top,max(safe.top,safe.bottom-menuSize.height)))
            else LiquidMenuMotion.place(anchor,Size(menuSize.width.toFloat(),menuSize.height.toFloat()),safe,with(density) { 10.dp.toPx() },preferBelow)
        val above=point.y<anchor.center.y
        val pointerExtent=if(compactPointer) 6.dp else 10.dp
        val tip=if(pointer && menuSize.width>0) GlassMenuPointer(
            if(sideways) (anchor.center.y-point.y)/menuSize.height.coerceAtLeast(1) else (anchor.center.x-point.x)/menuSize.width,
            if(sideways) !onLeft else !above,with(density) { pointerExtent.toPx() },sideways) else null
        // A large corner and broad arrow used to clamp the tip away from tools
        // near a screen edge. Fit both inside the available edge distance.
        val menuCorner=if(compactPointer && tip!=null) with(density) {
            val extent=if(sideways) menuSize.height.toFloat() else menuSize.width.toFloat()
            (min(extent*tip.fraction,extent*(1f-tip.fraction)).toDp()-pointerExtent*1.5f).coerceIn(6.dp,26.dp)
        } else cornerRadius
        GlassSurface(sizing.heightIn(max=(maxHeight-bottomInset-32.dp).coerceAtLeast(48.dp)).onSizeChanged { menuSize=it }
            .offset {
                IntOffset(point.x.roundToInt(),point.y.roundToInt())
            }.clickable(interactionSource=null,indication=null) { },GlassGeometry.Corner(menuCorner,pointer=tip)) {
            CompositionLocalProvider(LocalGlassOptions provides true,LocalControlTextSize provides AppTypography.Menu) {
                Column(Modifier.fillMaxWidth().padding(
                    start=if(pointer && sideways && !onLeft) pointerExtent else 0.dp,end=if(pointer && sideways && onLeft) pointerExtent else 0.dp,
                    top=6.dp+if(pointer && !sideways && !above) pointerExtent else 0.dp,bottom=6.dp+if(pointer && !sideways && above) pointerExtent else 0.dp)
                    .then(if(scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),content=content)
            }
        }
    }
}
