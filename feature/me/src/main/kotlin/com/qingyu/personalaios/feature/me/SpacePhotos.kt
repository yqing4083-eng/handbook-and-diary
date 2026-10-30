package com.qingyu.personalaios.feature.me

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animate
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.core.glass.*
import kotlinx.coroutines.*
import java.io.File

/** Bounded thumbnail cache survives tab navigation; decode stays off the UI thread. */
internal object SpacePhotoCache {
    private val cache=object:LruCache<String,ImageBitmap>(24*1024*1024) {
        override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4
    }
    fun key(path:String,large:Boolean)="$path|${File(path).lastModified()}|$large"
    fun peek(path:String,large:Boolean)=cache.get(key(path,large))
    fun load(path:String,large:Boolean):ImageBitmap? {
        val key=key(path,large);cache.get(key)?.let { return it }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        val edge=if(large) 2800 else 1200
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>edge) sample*=2
        val image=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample })?.asImageBitmap()
        if(image!=null) cache.put(key,image)
        return image
    }
}

suspend fun warmSpaceResources(space:com.qingyu.personalaios.domain.PersonalSpace)=withContext(Dispatchers.IO) {
    (listOfNotNull(space.avatar)+space.posts.take(2).flatMap { it.images }.take(4)).forEach { path ->
        runCatching { SpacePhotoCache.load(path,false) }
    }
}

@Composable private fun photo(path:String,large:Boolean=false):ImageBitmap? {
    val image by produceState(SpacePhotoCache.peek(path,large),path,large) {
        value=withContext(Dispatchers.IO) { runCatching { SpacePhotoCache.load(path,large) }.getOrNull() }
    }
    return image
}

@Composable internal fun PostPhotos(images:List<String>,onOpen:(Int)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val availableWidth=maxWidth
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        // Keep a large album out of one eagerly composed feed item. The viewer
        // retains all images and lazily decodes only nearby pages.
        images.take(4).chunked(2).forEachIndexed { row,paths ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                paths.forEachIndexed { column,path ->
                    val bitmap=photo(path)
                    val ratio=bitmap?.let { it.width.toFloat()/it.height } ?: 1f
                    // aspectRatio cannot satisfy a tight row width plus a capped
                    // portrait height: it draws beyond the reported bounds.
                    // Explicit height keeps Fit content inside its measured item.
                    val height=if(images.size==1) (availableWidth/ratio).coerceIn(64.dp,360.dp) else 210.dp
                    Box(Modifier.weight(1f).height(height).clip(AppShapes.Medium)
                        .background(LocalSpaceColors.current.foreground.copy(.045f))
                        .semantics { contentDescription="查看动态图片 ${row*2+column+1}，共 ${images.size} 张" }
                        .clickable(interactionSource=null,indication=null,role=Role.Button) { onOpen(row*2+column) },contentAlignment=Alignment.Center) {
                        if(bitmap!=null) Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                        else SpaceText("照片加载中",12.sp,alpha=.5f)
                        if(images.size>4 && row*2+column==3) SpaceTheme(
                            if(LocalSpaceColors.current.foreground==Color.Black) Color.Black else Color.White) {
                            SpaceText("+${images.size-4}",18.sp,
                                Modifier.align(Alignment.BottomEnd).background(
                                    if(LocalSpaceColors.current.foreground==Color.Black) Color.White.copy(.75f) else Color.Black.copy(.55f)).padding(10.dp))
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable internal fun SpacePhotoViewer(images:List<String>,initial:Int,onClose:()->Unit) {
    val pager=rememberPagerState(initialPage=initial.coerceIn(images.indices)) { images.size }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        // A Dialog has its own window and does not inherit the Activity's
        // transient navigation policy.
        val photoWindow=(LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(photoWindow) {
            photoWindow?.let { window ->
                window.isNavigationBarContrastEnforced=false
                androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                    systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                }
            }
            onDispose { }
        }
        val photoBackdrop=rememberGlassBackdrop()
        val environment=checkNotNull(LocalGlassEnvironment.current)
        SpaceTheme(if(environment.dayMode) Color.Black else Color.White) {
            Box(Modifier.fillMaxSize().background(Color(0xFF101217))) {
                Box(Modifier.fillMaxSize().captureGlassBackdrop(photoBackdrop)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF101217)))
                HorizontalPager(pager,Modifier.fillMaxSize(),beyondViewportPageCount=1) { index ->
                    val path=images[index];val bitmap=photo(path,true)
                    var zoom by remember(path) { mutableFloatStateOf(1f) }
                    var pan by remember(path) { mutableStateOf(Offset.Zero) }
                    val scope=rememberCoroutineScope()
                    LaunchedEffect(pager.currentPage) { if(pager.currentPage!=index) { zoom=1f;pan=Offset.Zero } }
                    val transform=rememberTransformableState { scale,offset,_ ->
                        zoom=(zoom*scale).coerceIn(1f,6f)
                        pan=if(zoom<=1f) Offset.Zero else pan+offset
                    }
                    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()
                        .transformable(transform,canPan={ zoom>1.01f })
                        .pointerInput(path) { detectTapGestures(onDoubleTap={
                            scope.launch { val target=if(zoom>1.1f) 1f else 2.5f;animate(zoom,target) { value,_ -> zoom=value };if(target==1f) pan=Offset.Zero }
                        }) },contentAlignment=Alignment.Center) {
                        if(bitmap!=null) Image(bitmap,"动态照片 ${index+1}",Modifier.fillMaxSize().graphicsLayer {
                            scaleX=zoom;scaleY=zoom
                            translationX=pan.x.coerceIn(-size.width*(zoom-1)/2,size.width*(zoom-1)/2)
                            translationY=pan.y.coerceIn(-size.height*(zoom-1)/2,size.height*(zoom-1)/2)
                        },contentScale=ContentScale.Fit)
                        else SpaceText("照片加载中",14.sp,alpha=.6f)
                    }
                }
                }
                LiquidGlassEnvironment(environment.copy(backdrop=photoBackdrop)) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    SpaceText("${pager.currentPage+1} / ${images.size}",14.sp,Modifier.weight(1f))
                    GlassButton(onClose,Modifier.size(AppControls.Circle).semantics { contentDescription="关闭照片查看" },GlassGeometry.Circle) {
                        val ink=LocalSpaceColors.current.foreground
                        Canvas(Modifier.size(AppControls.Icon).padding(4.dp)) {
                            val stroke=1.6.dp.toPx()
                            drawLine(ink,Offset.Zero,Offset(size.width,size.height),stroke,StrokeCap.Round)
                            drawLine(ink,Offset(size.width,0f),Offset(0f,size.height),stroke,StrokeCap.Round)
                        }
                    }
                }
                }
                SpaceText("左右切换 · 双击或双指缩放",12.sp,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),alpha=.6f)
            }
        }
    }
}
