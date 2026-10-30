package com.qingyu.personalaios.feature.diary

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.DiaryJournal
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Draft-only editor. No journal or private media changes until the checkmark succeeds. */
@Composable internal fun BookCoverEditor(journal:DiaryJournal,rect:ClosedBookRect,bottom:Dp,
    onCancel:()->Unit,onConfirm:suspend (DiaryJournal,String?)->Boolean) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val scope=rememberCoroutineScope()
    val artwork=rememberGlassBackdrop()
    val combined=rememberGlassContentBackdrop(artwork)
    val glass=checkNotNull(LocalGlassEnvironment.current).copy(backdrop=combined)
    var band by remember { mutableLongStateOf(if(journal.bandColor==0xFF34434BL) 0xFFFFBA00L else journal.bandColor) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember { mutableStateOf(CoverCrop()) }
    var paletteOpen by remember { mutableStateOf(false) }
    val paletteProgress by animateFloatAsState(if(paletteOpen) 1f else 0f,LiquidMotion.settle(),label="palette grows down")
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val original by produceState<Bitmap?>(null,journal) { value=withContext(Dispatchers.IO) { BookTextures.cover(journal) } }
    val contour=remember { android.graphics.Path().apply {
        BookPlane(BookGeometry.CoverWidth,BookGeometry.CoverHeight,.13f,straightEdge=-1).perimeter.forEachIndexed { i,(x,y) ->
            val px=(x/BookGeometry.CoverWidth+.5f)*768;val py=(.5f-y/BookGeometry.CoverHeight)*1152
            if(i==0) moveTo(px,py) else lineTo(px,py)
        };close()
    } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if(uri!=null) scope.launch {
        loading=true;error=null
        try {
            source=withContext(Dispatchers.IO) { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
                val scale=minOf(1f,2048f/maxOf(info.size.width,info.size.height))
                decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()))
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            } };crop=CoverCrop()
        } catch(cancel:CancellationException) { throw cancel }
        catch(failure:Exception) { android.util.Log.e("DiaryCover","Cannot read selected image",failure);error="图片未能读取，请重新选择。" }
        finally { loading=false }
    } }
    fun confirm() { if(!saving && !loading) scope.launch {
        saving=true;error=null
        var temporary:File?=null
        try {
            val path=source?.let { image -> withContext(Dispatchers.IO) {
                val output=Bitmap.createBitmap(768,1152,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(output)
                CoverArtwork.photo(canvas,image,crop)
                val file=File(context.cacheDir,"cover-draft-${UUID.randomUUID()}.png");temporary=file
                try { file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { output.recycle() }
                Uri.fromFile(file).toString()
            } }
            if(onConfirm(journal.copy(bandColor=band),path)) onCancel() else error="封面未能保存，请重试。"
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { error="封面未能保存，请重试。" }
        finally { temporary?.delete();saving=false }
    } }
    BackHandler { if(paletteOpen) paletteOpen=false else if(!saving) onCancel() }
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription="封面编辑" }) {
        // Exact shelf coordinates. Hiding navigation does not change the viewport or book size.
        Box(Modifier.fillMaxSize().captureGlassBackdrop(artwork)) {
        GlassMenuAnchor({ if(!saving && !loading) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },Modifier.offset { IntOffset(rect.left.toInt(),rect.top.toInt()) }
            .size(with(density) { rect.width.toDp() },with(density) { rect.height.toDp() })
            .semantics { contentDescription="添加封面图片" }
            .pointerInput(source,saving) {
                if(source!=null && !saving) detectTransformGestures { _,pan,zoom,_ ->
                    crop=crop.copy(zoom=crop.zoom*zoom,x=crop.x+pan.x/rect.width,y=crop.y+pan.y/rect.height)
                        .constrained(source!!.width.toFloat()/source!!.height)
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val native=canvas.nativeCanvas
                    native.save();native.scale(size.width/768,size.height/1152);native.clipPath(contour)
                    original?.let { native.drawBitmap(it,0f,0f,null) }
                    source?.let { CoverArtwork.photo(native,it,crop) }
                    CoverArtwork.spine(native,band.toInt())
                    native.restore()
                }
            }
        }
        }
        LiquidGlassEnvironment(glass) {
        DiaryIcon("取消封面编辑",R.drawable.paper_x,Modifier.align(Alignment.TopStart).padding(start=12.dp,top=18.dp)) { if(!saving) onCancel() }
        DiaryIcon("保存封面",R.drawable.paper_check,Modifier.align(Alignment.TopEnd).padding(end=12.dp,top=18.dp)) { confirm() }
        SpaceText(error ?: if(saving) "保存中…" else if(loading) "正在读取图片…" else if(source!=null) "双指缩放 · 拖动调整封面" else "轻点书本，添加封面图片",12.sp,
            Modifier.align(Alignment.TopCenter).padding(top=84.dp),alpha=.65f)
        // The top edge is fixed below the actual book. Growing downward never covers its artwork.
        val paletteTop=with(density) { rect.bottom.toDp() }+12.dp
        val fieldHeight=(maxHeight-paletteTop-88.dp).coerceIn(48.dp,144.dp)
        val panelWidth=216.dp+64.dp*paletteProgress.coerceIn(0f,1f)
        val panelHeight=48.dp+(fieldHeight+34.dp)*paletteProgress.coerceIn(0f,1f)
        Box(Modifier.align(Alignment.TopCenter).offset(y=paletteTop).width(panelWidth).height(panelHeight)) {
            if(!paletteOpen && paletteProgress==0f) GlassButton({ if(!saving) paletteOpen=true },Modifier.fillMaxSize()
                .semantics { contentDescription="更换书脊颜色" },GlassGeometry.Corner(16.dp)) {
                SpaceText("更换书脊颜色",AppTypography.Menu)
            } else GlassSurface(Modifier.fillMaxSize(),GlassGeometry.Corner(16.dp)) {
                Column(Modifier.fillMaxSize().padding(horizontal=12.dp)) {
                    Box(Modifier.fillMaxWidth().height(36.dp).clickable(interactionSource=null,indication=null) { paletteOpen=!paletteOpen },contentAlignment=Alignment.Center) {
                        SpaceText("更换书脊颜色",AppTypography.Menu)
                    }
                    Box(Modifier.graphicsLayer { alpha=paletteProgress.coerceIn(0f,1f) }) {
                        SpinePalette(band,fieldHeight) { band=it }
                    }
                }
            }
        }
        }
    }
}

private object CoverArtwork {
    private val paints=ThreadLocal.withInitial { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
    private val paint get()=checkNotNull(paints.get())
    fun photo(canvas:android.graphics.Canvas,image:Bitmap,crop:CoverCrop) {
        val ratio=maxOf(768f/image.width,1152f/image.height)*crop.zoom
        val w=image.width*ratio;val h=image.height*ratio
        val x=(768-w)/2+crop.x*768;val y=(1152-h)/2+crop.y*1152
        paint.shader=null;paint.color=android.graphics.Color.WHITE
        canvas.drawBitmap(image,null,android.graphics.RectF(x,y,x+w,y+h),paint)
    }
    fun spine(canvas:android.graphics.Canvas,color:Int) {
        fun shade(f:Float)=android.graphics.Color.rgb((android.graphics.Color.red(color)*f).toInt().coerceIn(0,255),(android.graphics.Color.green(color)*f).toInt().coerceIn(0,255),(android.graphics.Color.blue(color)*f).toInt().coerceIn(0,255))
        paint.color=android.graphics.Color.WHITE
        paint.shader=android.graphics.LinearGradient(0f,0f,91.2f,0f,intArrayOf(shade(.88f),shade(1.16f),shade(.82f),shade(1.08f),color),floatArrayOf(0f,.15f,.3f,.48f,1f),android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,91.2f,1152f,paint);paint.shader=null
        paint.color=0x4CFFFFFF;paint.strokeWidth=1.92f;canvas.drawLine(91.2f,0f,91.2f,1152f,paint)
        paint.color=0x24FFFFFF;canvas.drawLine(16.8f,0f,16.8f,1152f,paint)
    }
}

/** Camera-style two-axis colour/tone pad, dotted grid and live crosshair, plus hue rail. */
@Composable private fun SpinePalette(color:Long,height:Dp,onColor:(Long)->Unit) {
    val hsv=FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(),it) }
    val latestHsv by rememberUpdatedState(hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    val palette=remember((hue/4).toInt()) {
        val pixels=IntArray(256*144)
        for(y in 0 until 144) for(x in 0 until 256) {
            pixels[y*256+x]=android.graphics.Color.HSVToColor(floatArrayOf(hue,x/255f,1-y/143f))
        }
        Bitmap.createBitmap(pixels,256,144,Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Column {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription="滑动调节书脊颜色" }
            .pointerInput(hue) { awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                fun update(x:Float,y:Float) {
                    onColor(android.graphics.Color.HSVToColor(floatArrayOf(hue,(x/size.width).coerceIn(0f,1f),1-(y/size.height).coerceIn(0f,1f))).toLong() and 0xFFFFFFFFL)
                }
                update(down.position.x,down.position.y);down.consume()
                drag(down.id) { change -> update(change.position.x,change.position.y);change.consume() }
            } }) {
            drawImage(palette,dstSize=IntSize(size.width.toInt(),size.height.toInt()))
            val step=14.dp.toPx()
            var y=step/2
            while(y<size.height) { var x=step/2;while(x<size.width) { drawCircle(Color.White.copy(.38f),.65.dp.toPx(),androidx.compose.ui.geometry.Offset(x,y));x+=step };y+=step }
            val point=androidx.compose.ui.geometry.Offset(size.width*hsv[1],size.height*(1-hsv[2]))
            val stroke=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
            drawCircle(Color.Black.copy(.35f),11.dp.toPx(),point,style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawCircle(Color.White,10.dp.toPx(),point,style=stroke)
            drawLine(Color.White.copy(.75f),androidx.compose.ui.geometry.Offset(point.x,0f),androidx.compose.ui.geometry.Offset(point.x,(point.y-14.dp.toPx()).coerceAtLeast(0f)),1.dp.toPx())
            drawLine(Color.White.copy(.75f),androidx.compose.ui.geometry.Offset((point.x+14.dp.toPx()).coerceAtMost(size.width),point.y),androidx.compose.ui.geometry.Offset(size.width,point.y),1.dp.toPx())
        }
        Canvas(Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription="书脊色相" }
            .pointerInput(Unit) { awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                fun update(x:Float) {
                    hue=(x/size.width).coerceIn(0f,1f)*359.9f
                    onColor(android.graphics.Color.HSVToColor(floatArrayOf(hue,latestHsv[1].coerceAtLeast(.2f),latestHsv[2].coerceAtLeast(.25f))).toLong() and 0xFFFFFFFFL)
                }
                update(down.position.x);down.consume()
                drag(down.id) { change -> update(change.position.x);change.consume() }
            } }) {
            val colors=listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)
            drawRect(Brush.horizontalGradient(colors),topLeft=androidx.compose.ui.geometry.Offset(0f,size.height/2-2.dp.toPx()),size=androidx.compose.ui.geometry.Size(size.width,4.dp.toPx()))
            val point=androidx.compose.ui.geometry.Offset(size.width*hue/360,size.height/2)
            drawCircle(Color(color),6.dp.toPx(),point);drawCircle(Color.White,6.dp.toPx(),point,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}
