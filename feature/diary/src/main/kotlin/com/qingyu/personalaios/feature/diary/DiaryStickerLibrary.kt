package com.qingyu.personalaios.feature.diary

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.StickerArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val StickerBlue=Color(0xFF007AFF)
@Composable internal fun ColumnScope.DiaryStickerLibrary(onImage:()->Unit={},onText:(String)->Unit={},onAdd:(StickerArt)->Unit) {
    var category by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf("伙伴") }
    var kaomoji by remember { mutableStateOf("") }
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val assets=LocalContext.current.assets
    fun submit() { if(kaomoji.isNotBlank()) { focus.clearFocus();keyboard?.hide();onText(kaomoji) } }
    CompositionLocalProvider(LocalControlTextSize provides null) {
        Row(Modifier.fillMaxWidth().background(Color(0xFFF0F2F5),AppShapes.Medium).padding(3.dp)) {
            listOf("贴纸","表情","颜文字").forEachIndexed { i,label ->
                val tint by animateColorAsState(if(category==i) StickerBlue else Color.Transparent,tween(180),label="sticker category")
                Box(Modifier.weight(1f).background(tint,AppShapes.Small)
                    .semantics { contentDescription="${label}分类";selected=category==i }
                    .clickable(interactionSource=null,indication=null) { category=i }.padding(vertical=9.dp),contentAlignment=Alignment.Center) {
                    SpaceTheme(if(category==i) Color.White else PaperInk) { SpaceText(label,13.sp) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(category==0,enter=fadeIn(tween(150))+expandVertically(tween(220)),exit=fadeOut(tween(110))+shrinkVertically(tween(220))) {
            Column { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf("伙伴","慢生活","手绘","像素").forEach { label ->
                    Box(Modifier.background(if(group==label) StickerBlue.copy(.12f) else Color.Transparent,AppShapes.Small)
                        .semantics { contentDescription="${label}贴纸组";selected=group==label }
                        .clickable(interactionSource=null,indication=null) { group=label }.padding(horizontal=10.dp,vertical=6.dp)) {
                        SpaceTheme(if(group==label) StickerBlue else PaperInk) { SpaceText(label,11.sp) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp)) }
        }
        AnimatedContent(targetState=category to (if(category==0) group else ""),
            modifier=Modifier.fillMaxWidth().weight(1f,fill=false).height(184.dp),
            transitionSpec={
                val direction=if(targetState.first>initialState.first || targetState.first==initialState.first && targetState.second>initialState.second) 1 else -1
                (fadeIn(tween(190,50))+slideInHorizontally(tween(240,easing=CubicBezierEasing(.22f,.85f,.28f,1f))) { direction*it/14 }) togetherWith
                    (fadeOut(tween(120))+slideOutHorizontally(tween(200)) { -direction*it/14 })
            },label="sticker library contents") { (shownCategory,shownGroup) ->
        val grid=rememberLazyGridState()
        LazyVerticalGrid(GridCells.Fixed(if(shownCategory==1) 6 else if(shownCategory==2) 2 else 4),state=grid,
            modifier=Modifier.fillMaxSize(),
            verticalArrangement=Arrangement.spacedBy(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(shownCategory==0) items(StickerArt.entries.filter { it.group==shownGroup },key={it.name}) { art ->
                val preview by produceState<ImageBitmap?>(null,art) { value=withContext(Dispatchers.IO) { StickerPack.bitmap(assets,art,true).asImageBitmap() } }
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFF5F6F8),AppShapes.Small)
                    .semantics { contentDescription="添加${art.label}贴纸" }
                    .clickable(interactionSource=null,indication=null) { onAdd(art) }.padding(5.dp),contentAlignment=Alignment.Center) {
                    preview?.let { Image(it,null,Modifier.fillMaxSize()) }
                }
            }
            if(shownCategory==1) items(listOf("🥰","😊","😂","🥺","😎","🤍","✨","🌷","🍀","☀️","🎉","🫶")) { value ->
                Box(Modifier.height(46.dp).semantics { contentDescription="贴上表情 $value" }
                    .clickable(interactionSource=null,indication=null) { onText(value) },contentAlignment=Alignment.Center) { SpaceText(value,25.sp) }
            }
            if(shownCategory==2) items(listOf("(｡･ω･｡)ﾉ♡","(๑˃̵ᴗ˂̵)و","(づ｡◕‿‿◕｡)づ","(´･ω･`)","( •̀ ω •́ )✧","(つ﹏⊂)")) { value ->
                Box(Modifier.height(44.dp).background(Color(0xFFF0F2F5),AppShapes.Small)
                    .clickable(interactionSource=null,indication=null) { onText(value) },contentAlignment=Alignment.Center) { SpaceText(value,12.sp) }
            }
        }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().background(Color.White,AppShapes.Medium).border(1.dp,PaperInk.copy(.32f),AppShapes.Medium)
            .padding(3.dp),verticalAlignment=Alignment.CenterVertically) {
            BasicTextField(kaomoji,{ kaomoji=it.take(120) },
                Modifier.weight(1f).padding(horizontal=10.dp,vertical=10.dp).semantics { contentDescription="自定义颜文字" },
                textStyle=TextStyle(color=PaperInk,fontSize=14.sp),cursorBrush=SolidColor(StickerBlue),singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={ submit() }),
                decorationBox={ field -> Box { if(kaomoji.isEmpty()) SpaceText("输入颜文字…",14.sp,alpha=.65f);field() } })
            DiaryIcon("从相册添加表情包",R.drawable.paper_file) { focus.clearFocus();keyboard?.hide();onImage() }
        }
    }
}
