package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable internal fun DiaryIcon(label:String,icon:Int,modifier:Modifier=Modifier,size:Dp=AppControls.Circle,onClick:()->Unit) {
    if(LocalPaperEditorChrome.current) {
        PaperChromeIcon(label,icon,modifier,size,onClick);return
    }
    GlassButton(onClick,modifier.size(size).semantics { contentDescription=label },geometry=GlassGeometry.Circle) {
        Image(painterResource(icon),null,Modifier.align(Alignment.Center).size(size*.5f),colorFilter=ColorFilter.tint(LocalSpaceColors.current.foreground))
    }
}

@Composable internal fun DiarySheet(title:String,onClose:()->Unit,paperContent:Boolean=false,frosted:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    BackHandler { onClose() }
    val simple=title in listOf("导出","纸张模板","画布片段")
    val body:@Composable ColumnScope.()->Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal=if(frosted) 8.dp else 20.dp,vertical=if(frosted) 4.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
            SpaceText(title,AppTypography.Menu,Modifier.weight(1f),alpha=if(frosted) 1f else .55f)
        }
        Column(Modifier.fillMaxWidth().then(if(frosted) Modifier.weight(1f,fill=false) else Modifier)
            .padding(horizontal=if(simple || frosted) 0.dp else 8.dp)) { content() }
    }
    Box(Modifier.fillMaxSize().imePadding()) {
    GlassOptionsPopup(onClose,width=if(simple) Dp.Unspecified else if(frosted) 288.dp else 304.dp,scrollable=!frosted) {
        if(paperContent) SpaceTheme(PaperInk) {
            Column(Modifier.fillMaxWidth().then(if(frosted) Modifier.heightIn(max=340.dp).semantics { contentDescription="贴纸选项面板" } else Modifier)
                .background(PaperColor.copy(if(frosted) .38f else .96f),AppShapes.Large).padding(if(frosted) 8.dp else 10.dp),content=body)
        } else body()
    }
    }
}

/** Paper's compact popovers: four button diameters wide, generous centered rows. */
@Composable internal fun DiaryActionPopup(onClose:()->Unit,content:@Composable ColumnScope.()->Unit) {
    BackHandler { onClose() }
    GlassOptionsPopup(onClose,content=content)
}

@Composable internal fun DiaryAction(label:String,onClick:()->Unit,last:Boolean=false,description:String=label,rowHeight:Dp=48.dp) {
    GlassMenuOption(label,onClick,Modifier.fillMaxWidth().heightIn(min=rowHeight).semantics { contentDescription=description },centered=true)
    if(!last) Box(Modifier.fillMaxWidth().padding(horizontal=8.dp).height(.5.dp).background(LocalSpaceColors.current.foreground.copy(alpha=.13f)))
}

@Composable internal fun JournalLibrary(state:DiaryState,model:DiaryViewModel,onSelect:(String)->Unit,onClose:()->Unit,onManage:()->Unit={}) {
    DiarySheet("书架",onClose) {
        state.journals.filter { it.deletedAt!=0L }.forEach { journal ->
            DiaryControl("恢复书册 · ${journal.name}") { model.updateJournal(journal.copy(deletedAt=0)) }
            Spacer(Modifier.height(12.dp))
        }
        state.journals.filter { it.deletedAt==0L }.forEach { journal ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(journal.id) }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                val image by produceState<ImageBitmap?>(null,journal) { value=withContext(Dispatchers.IO) { BookTextures.cover(journal).asImageBitmap() } }
                image?.let { Image(it,null,Modifier.size(54.dp,81.dp).clip(AppShapes.Paper)) }
                Column(Modifier.padding(start=18.dp).weight(1f)) {
                    SpaceText(journal.name,18.sp);SpaceText("${state.allPages.count { it.journalId==journal.id && it.deletedAt==0L }} 页",12.sp,alpha=.5f)
                }
                DiaryIcon("选择${journal.name}",R.drawable.paper_book_open) { onSelect(journal.id) }
            }
        }
        Spacer(Modifier.height(20.dp))
        DiaryControl("新建手帐") { model.createJournal(BookKind.SCRAPBOOK);onClose() }
        Spacer(Modifier.height(12.dp));DiaryControl("新建日记") { model.createJournal(BookKind.DIARY);onClose() }
        Spacer(Modifier.height(12.dp));DiaryControl("书册管理",onManage)
    }
}

@Composable internal fun DiaryField(value:String,onChange:(String)->Unit,label:String) {
    GlassSurface(Modifier.fillMaxWidth()) {
        BasicTextField(value,onChange,Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription=label },
            textStyle=TextStyle(color=LocalSpaceColors.current.foreground,fontSize=LocalControlTextSize.current ?: 16.sp),cursorBrush=SolidColor(LocalSpaceColors.current.foreground),
            decorationBox={ field -> Box { if(value.isBlank()) SpaceText(label,16.sp,alpha=.4f);field() } })
    }
}

@Composable internal fun DiaryGrid(state:DiaryState,model:DiaryViewModel,onPage:(String)->Unit,onClose:()->Unit,
    fullScreen:Boolean=false,reveal:Float=1f,sourcePageId:String?=null,sourcePreview:ImageBitmap?=null,sourceBounds:Rect=Rect.Zero,onText:()->Unit={},onImport:()->Unit={},onAdd:()->Unit={}) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recycle by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf(Rect.Zero) }
    val density=LocalDensity.current
    val pages=state.allPages.filter { it.journalId==state.journalId && (it.deletedAt!=0L)==recycle &&
        (query.isBlank() || it.title.contains(query,true) || it.body.contains(query,true)) }
    val gridState=rememberLazyGridState(initialFirstVisibleItemIndex=(pages.indexOfFirst { it.id==sourcePageId }.coerceAtLeast(0)/2)*2)
    val body:@Composable ColumnScope.()->Unit={
        if(!fullScreen || searching) {
        DiaryField(query,{ query=it },"搜索日记")
        Spacer(Modifier.height(12.dp))
        }
        if(!fullScreen || recycle || selected.isNotEmpty()) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DiaryControl(if(recycle) "回到纸页" else "回收站") { recycle=!recycle;selected=emptySet() }
            DiaryControl(if(selected.isEmpty()) "多选" else "取消多选") { selected=if(selected.isEmpty() && pages.isNotEmpty()) setOf(pages.first().id) else emptySet() }
        }
        Spacer(Modifier.height(16.dp))
        }
        LazyVerticalGrid(GridCells.Fixed(2),Modifier.fillMaxWidth().then(if(fullScreen) Modifier.weight(1f) else Modifier.heightIn(min=140.dp,max=340.dp)),
            state=gridState,contentPadding=if(fullScreen) PaddingValues(horizontal=28.dp) else PaddingValues(0.dp),
            horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            items(pages,key={ it.id }) { page ->
                var rect by remember(page.id) { mutableStateOf(Rect.Zero) }
                val thumbnail by produceState<ImageBitmap?>(if(page.id==sourcePageId) sourcePreview else null,page.id,page.revision) {
                    value=withContext(Dispatchers.IO) { PageArtwork.sheetBitmap(page,480,notes=true).asImageBitmap() }
                }
                // Measure an untransformed parent, so the shrinking child cannot feed its
                // own animated bounds back into the next frame's scale calculation.
                Box(Modifier.fillMaxWidth().onGloballyPositioned { rect=it.boundsInRoot() }) {
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    if(fullScreen) {
                        val q=reveal.coerceIn(0f,1f)
                        alpha=if(page.id==sourcePageId) q else ((q-.18f)/.82f).coerceIn(0f,1f)
                        if(rect.width>0f && root.width>0f) {
                            val origin=if(sourceBounds.width>0f) sourceBounds else Rect(root.left+root.width*.14f,root.top+root.height*.36f,root.right-root.width*.14f,root.top+root.height*.64f)
                            val distance=(pages.indexOf(page)-pages.indexOfFirst { it.id==sourcePageId }).coerceIn(-3,3)
                            val travel=if(page.id==sourcePageId) q else ((q-.06f*kotlin.math.abs(distance)) / (1f-.06f*kotlin.math.abs(distance))).coerceIn(0f,1f)
                            val scale=1f+(origin.width/rect.width-1f)*(1f-travel)
                            scaleX=scale;scaleY=1f+(origin.height/rect.height-1f)*(1f-travel)
                            translationX=(origin.center.x+origin.width*.12f*distance-rect.center.x)*(1f-travel)
                            translationY=(origin.center.y-rect.center.y)*(1f-travel)
                        }
                    }
                }.semantics { contentDescription="纸页 ${state.pages.indexOfFirst { it.id==page.id }+1} ${page.title}" }
                    .clickable { if(selected.isNotEmpty() || recycle) selected=if(page.id in selected) selected-page.id else selected+page.id else onPage(page.id) }
                    .pointerInput(page.id,pages.map { it.id }) {
                        var distance=0f
                        detectDragGesturesAfterLongPress(onDragStart={ selected=setOf(page.id);distance=0f },onDragEnd={
                            val index=pages.indexOfFirst { it.id==page.id }
                            val target=if(distance<0) pages.getOrNull((index-2).coerceAtLeast(0))?.id else pages.getOrNull(index+3)?.id
                            if(abs(distance)>40 && !(distance<0 && index==0)) model.movePages(listOf(page.id),state.journalId,target)
                        }) { change,delta -> change.consume();distance+=delta.y }
                    }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(if(fullScreen) 4f/3f else page.canvasAspect*(if(page.singleFace) 2 else 1)).clip(AppShapes.Paper).background(PaperColor)) {
                        thumbnail?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=if(fullScreen) ContentScale.Fit else ContentScale.FillBounds) }
                    }
                    if(!fullScreen || page.id in selected) {
                        Spacer(Modifier.height(6.dp));SpaceText((if(page.id in selected) "已选 · " else "")+page.title.ifBlank { "未命名纸页" },12.sp)
                    }
                }
                }
            }
        }
        if(pages.isEmpty()) SpaceText(if(recycle) "没有删除的纸页" else "没有匹配的纸页",14.sp,alpha=.5f)
        if(selected.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DiaryControl(if(recycle) "恢复" else "删除纸页") { model.trashPages(selected.toList(),recycle);selected=emptySet() }
                if(!recycle) DiaryControl("复制纸页") { model.duplicatePages(selected.toList());selected=emptySet();onClose() }
            }
            if(!recycle) {
                Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    DiaryControl("移动到书册") { moving=!moving }
                    DiaryControl("移到开头") { model.movePages(selected.toList(),state.journalId,pages.firstOrNull { it.id !in selected }?.id);selected=emptySet() }
                }
            }
            if(moving) Column(Modifier.fillMaxWidth().heightIn(max=180.dp).verticalScroll(rememberScrollState())) { state.journals.filter { it.deletedAt==0L && it.id!=state.journalId && it.kind==state.journal.kind }.forEach { journal ->
                Spacer(Modifier.height(8.dp));DiaryControl(journal.name) { model.movePages(selected.toList(),journal.id);selected=emptySet();moving=false }
            } }
        }
        if(fullScreen) {
            Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=16.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                DiaryIcon(if(recycle) "回到纸页" else "多选纸页",R.drawable.paper_more_horizontal) {
                    if(recycle) recycle=false else selected=if(selected.isEmpty()) pages.firstOrNull()?.let { setOf(it.id) } ?: emptySet() else emptySet()
                }
                Spacer(Modifier.width(12.dp));DiaryIcon("导入图片",R.drawable.paper_image,onClick=onImport)
                Spacer(Modifier.width(12.dp));DiaryIcon("新的一页",R.drawable.paper_plus,onClick=onAdd)
            }
        }
    }
    if(fullScreen) {
        BackHandler { onClose() }
        Column(Modifier.fillMaxSize().onGloballyPositioned { root=it.boundsInRoot() }
            .clickable(interactionSource=null,indication=null) {}.padding(start=12.dp,end=12.dp,bottom=LocalPageBottomInset.current)) {
            Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                DiaryIcon("返回书本",R.drawable.paper_book_open,onClick=onClose)
                DiaryIcon("搜索纸页",R.drawable.paper_search) { searching=!searching;if(!searching) query="" }
            }
            Column(Modifier.fillMaxWidth().padding(top=20.dp,bottom=28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                SpaceText(state.journal.name,22.sp)
                Spacer(Modifier.height(8.dp));SpaceText("${state.pages.size} 页",12.sp,alpha=.6f)
            }
            body()
        }
    } else DiarySheet(if(recycle) "回收站" else "所有纸页",onClose,content=body)
}
