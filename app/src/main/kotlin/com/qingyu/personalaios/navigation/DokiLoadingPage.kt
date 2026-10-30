package com.qingyu.personalaios.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingyu.personalaios.SpaceViewModel
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.feature.diary.VulkanLoadingBook
import kotlinx.coroutines.delay

/** Holds the opening book until settings/wallpaper and a real native frame are ready.
 * A short readable cycle prevents a flash; no fabricated progress percentage. */
@Composable internal fun DokiLoadingPage(ready:Boolean,onFinished:()->Unit) {
    val model:SpaceViewModel=viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val theme=state.theme()
    val opacity=remember { Animatable(1f) }
    var rendered by remember { mutableStateOf(false) }
    var written by remember { mutableStateOf(false) }
    val finish=rememberUpdatedState(onFinished)
    LaunchedEffect(ready,rendered,written) {
        if(ready && rendered && written) {
            delay(180)
            opacity.animateTo(0f,tween(220))
            finish.value()
        }
    }
    SpaceTheme(Color(theme.foreground)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha=opacity.value }
            .background(Brush.verticalGradient(listOf(Color(theme.backgroundTop),Color(theme.backgroundBottom))))
            .semantics { contentDescription="Doki 正在加载";paneTitle="加载";liveRegion=LiveRegionMode.Polite }
            .pointerInput(Unit) { awaitPointerEventScope { while(true) awaitPointerEvent().changes.forEach { it.consume() } } },
            contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                VulkanLoadingBook(Modifier.width(240.dp).height(200.dp),onReady={ rendered=true },
                    onUnavailable={ rendered=true })
                Spacer(Modifier.height(10.dp))
                HelloWord("Doki",Modifier.width(134.dp).height(68.dp),
                    colors=intArrayOf(0xFF49BDF2.toInt(),0xFF7978F4.toInt(),0xFFEE77BA.toInt(),0xFFFF8B55.toInt(),0xFFFFC548.toInt()),
                    retainColors=true,durationMillis=1550,onWritten={ written=true })
            }
        }
    }
}
