package com.qingyu.personalaios.feature.diary

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.qingyu.personalaios.domain.DiaryJournal
import com.qingyu.personalaios.domain.DiaryPage

/** Real Vulkan book geometry, lighting, page thickness and shadows. No video frames
 * or CPU-rendered animation bitmap. The surface is released with this composition. */
@Composable fun VulkanLoadingBook(modifier:Modifier=Modifier,onReady:()->Unit={},onUnavailable:()->Unit={}) {
    val owner=LocalLifecycleOwner.current
    val ready=rememberUpdatedState(onReady);val unavailable=rememberUpdatedState(onUnavailable)
    var presented by remember { mutableStateOf(false) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    val pages=remember { List(4) { DiaryPage(id="loading-page-$it",order=it,journalId="loading-book") } }
    val journal=remember { DiaryJournal(id="loading-book",name="Doki",coverColor=0xFF839CA8,bandColor=0xFFEDBC74) }
    LaunchedEffect(owner,presented) {
        if(presented) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous=0L
            while(true) withFrameNanos { now ->
                if(previous!=0L) elapsed+=((now-previous)/1_000_000_000.0).coerceIn(0.0,.05)
                previous=now
            }
        }
    }
    val frame=LoadingBookMotion.at(elapsed)
    val scene=BookScene(pages=pages,journal=journal,current=frame.current,
        journals=listOf(journal),open=1f,turn=frame.turn,direction=frame.direction,portrait=false,thumbnail=true)
    AndroidView(factory={ context -> BookSceneView(context).apply {
        onFirstFrame={
            if(!presented) {
                presented=true
                android.util.Log.i("DokiLoading","First book frame presented; renderer=Vulkan")
                ready.value()
            }
        }
        onRenderFailure={ unavailable.value() }
        isClickable=false
    } },modifier=modifier.semantics {
        contentDescription="Vulkan 翻页加载图案"
        stateDescription=if(presented) "正在翻页" else "初始化"
    },update={ it.update(scene) },onRelease={ it.release() })
}
