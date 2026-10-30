package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun DiaryRecycleBin(state:DiaryState,model:DiaryViewModel,onClose:()->Unit) {
    val pages=remember(state.allPages,state.journalId) { state.allPages.filter { it.journalId==state.journalId && it.deletedAt>0 }.sortedByDescending { it.deletedAt } }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(pages) { selected=selected.intersect(pages.map { it.id }.toSet()) }
    DiarySheet("回收站",onClose,paperContent=true) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            SpaceText("${pages.size} 张纸页",12.sp,alpha=.55f)
            if(pages.isNotEmpty()) DiaryControl(if(selected.size==pages.size) "取消全选" else "全选") {
                selected=if(selected.size==pages.size) emptySet() else pages.map { it.id }.toSet()
            }
        }
        Spacer(Modifier.height(12.dp))
        if(pages.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical=38.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            SpaceText("还没有删除的纸页",16.sp)
            Spacer(Modifier.height(10.dp));SpaceText("删除的内容会保留在这里",12.sp,alpha=.5f)
        } else {
            LazyVerticalGrid(GridCells.Fixed(2),Modifier.fillMaxWidth().heightIn(max=370.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(pages,key={ it.id }) { page ->
                    val image by produceState<ImageBitmap?>(null,page.id,page.revision) { value=withContext(Dispatchers.IO) { PageArtwork.sheetBitmap(page,400).asImageBitmap() } }
                    Column(Modifier.semantics { contentDescription="回收纸页 ${page.order+1}";this.selected=page.id in selected }
                        .clickable(interactionSource=null,indication=null) { selected=if(page.id in selected) selected-page.id else selected+page.id }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(AppShapes.Paper).background(PaperColor),contentAlignment=Alignment.Center) {
                            image?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds) }
                            if(page.id in selected) SpaceTheme(PaperInk) { SpaceText("✓",22.sp,Modifier.align(Alignment.TopEnd).padding(6.dp)) }
                        }
                        Spacer(Modifier.height(6.dp));SpaceText(page.title.ifBlank { "纸页 ${page.order+1}" },12.sp,alpha=.8f)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            DiaryControl(if(selected.isEmpty()) "选择要恢复的纸页" else "恢复所选 ${selected.size} 张",{
                if(selected.isNotEmpty() && !state.working) { model.trashPages(selected.toList(),restore=true);selected=emptySet() }
            },Modifier.fillMaxWidth())
        }
    }
}
