package com.qingyu.personalaios.feature.me

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.PhotoPageFill
import com.qingyu.personalaios.core.theme.FestivalCalendar
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun MeScreen(model: MeViewModel, nightMode:Boolean=true,onMode:()->Unit={},onSettings: () -> Unit) {
    val space by model.space.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val density = LocalDensity.current
    var composer by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var nameBounds by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Pair<List<String>,Int>?>(null) }
    val contentLayer=rememberGlassBackdrop()
    val combined=rememberGlassContentBackdrop(contentLayer)
    val base=checkNotNull(LocalGlassEnvironment.current)
    val avatarInk=LocalSpaceColors.current.foreground
    val travel=with(density) { SpaceScrollMotion.CollapseDp.dp.toPx() }
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val scrollProgress by remember(list,travel) { derivedStateOf {
        SpaceScrollMotion.progress(list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset.toFloat(),travel)
    } }
    LaunchedEffect(list,travel) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if(!scrolling && list.firstVisibleItemIndex==0) {
                val offset=list.firstVisibleItemScrollOffset.toFloat()
                val target=SpaceScrollMotion.snap(offset,travel)
                if(kotlin.math.abs(target-offset)>1f) scope.launch {
                    list.animateScrollBy(target-offset,tween(260,easing=LiquidMotion.Navigation))
                }
            }
        }
    }
    var titleWidth by remember { mutableIntStateOf(0) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { model.avatar(it.toString()) } }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin=it.positionInRoot() }
        .pointerInput(renaming,nameBounds) {
            if(renaming) awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                if(!nameBounds.contains(rootOrigin+down.position)) { focus.clearFocus();keyboard?.hide() }
            }
        }) {
        LazyColumn(state=list,modifier=Modifier.fillMaxSize().padding(top=72.dp).clipToBounds().captureGlassBackdrop(contentLayer),
            contentPadding=PaddingValues(bottom=LocalPageBottomInset.current+74.dp)) {
            item(key="header") { Spacer(Modifier.fillMaxWidth().height(SpaceScrollMotion.CollapseDp.dp)) }
            item(key="summary") {
                Column(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        SpaceText("${space.posts.size} 条动态 · 仅自己可见",11.sp,Modifier.weight(1f),alpha=.5f)
                        LocalFestivalTheme.current?.let {FestivalEmblem(it.event,Modifier.size(18.dp))}
                    }
                    FestivalSignature(Modifier.padding(top=7.dp))
                }
            }
            if(space.posts.isEmpty()) item(key="empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=28.dp)) {
                    val festival=LocalFestivalTheme.current
                    if(festival!=null) FestivalKeepsake(FestivalPlace.SPACE,Modifier.padding(bottom=10.dp))
                    SpaceText(if(festival!=null) "留住${festival.event.name}的片刻" else "生活，等你落笔。",21.sp)
                    Spacer(Modifier.height(12.dp))
                    SpaceText(festival?.let {festivalEntryPrompt(it.event,FestivalPlace.SPACE)} ?: "点右下角的 +，写一句话，或放一张照片。",14.sp,alpha=.65f)
                }
            }
            items(space.posts,key={ it.id }) { post ->
                val date=Instant.ofEpochMilli(post.createdAt).atZone(ZoneId.systemDefault())
                val postFestival=remember(post.createdAt) {FestivalCalendar.events(date.toLocalDate()).firstOrNull()}
                val line=LocalSpaceColors.current.foreground
                val festival=LocalFestivalTheme.current
                val festivalInk=festival?.event?.accent?.let {Color(it)} ?: line
                // A single continuous timeline replaces the disconnected glass cards.
                Box(Modifier.animateItem().fillMaxWidth().padding(horizontal=24.dp).drawBehind {
                    val x=6.dp.toPx()
                    drawCircle(festivalInk.copy(if(festival!=null) .55f else .38f),2.3.dp.toPx(),Offset(x,30.dp.toPx()))
                    drawLine(festivalInk.copy(if(festival!=null) .18f else .13f),Offset(x,38.dp.toPx()),Offset(x,size.height),.7.dp.toPx())
                }) {
                    Column(Modifier.fillMaxWidth().padding(start=26.dp,top=8.dp,bottom=22.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                SpaceText(date.format(DateTimeFormatter.ofPattern("dd MMM",java.util.Locale.ENGLISH)).uppercase(),14.sp,
                                    weight=androidx.compose.ui.text.font.FontWeight.Medium)
                                Row(Modifier.padding(top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                    SpaceText(date.format(DateTimeFormatter.ofPattern("HH:mm")),10.sp,alpha=.45f)
                                    postFestival?.let {FestivalEmblem(it,Modifier.size(11.dp));SpaceText(it.name,9.sp,alpha=.6f)}
                                }
                            }
                            RoundAction("删除动态 ${post.id}","trash",{ if(!working) deleting=post.id })
                        }
                        if(post.body.isNotEmpty()) { Spacer(Modifier.height(12.dp));SpaceText(post.body,17.sp) }
                        if(post.images.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp));PostPhotos(post.images) { index -> viewing=post.images to index }
                        }
                        Spacer(Modifier.height(22.dp))
                        if(postFestival!=null) FestivalDivider(event=postFestival)
                        else Box(Modifier.fillMaxWidth().height(.6.dp).background(line.copy(.10f)))
                    }
                }
            }
            if(space.posts.size<3) item(key="scroll-room") {
                Spacer(Modifier.height((maxHeight-340.dp).coerceAtLeast(96.dp)))
            }
        }
        LiquidGlassEnvironment(base.copy(backdrop=combined)) {
        // The feed recording excludes this header, preventing recursive glass sampling.
        InlineGlassName("profile",space.name,!working,Modifier.onSizeChanged { titleWidth=it.width }.graphicsLayer {
            val p=scrollProgress
            translationX=(with(density) { maxWidth.toPx() }-titleWidth)/2
            translationY=(162.dp.toPx()*(1-p)+14.dp.toPx()*p)
            scaleX=1f-.08f*p;scaleY=scaleX
        },maxLength=32,label="修改用户名",inputLabel="用户名输入",onEditing={ renaming=it },onBounds={ nameBounds=it },onCommit={ model.rename(it) })
        val centerX = with(density) { ((maxWidth - AppControls.Avatar) / 2).toPx() }
        GlassButton({ photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.size(AppControls.Avatar).graphicsLayer {
                val pose=SpaceScrollMotion.avatar(scrollProgress)
                translationX=centerX+pose.drift*AppControls.Avatar.toPx()
                translationY=32.dp.toPx()-pose.rise*travel
                scaleX=pose.width;scaleY=pose.height;alpha=pose.alpha
                shape=AvatarCollapseShape(pose.neck);clip=true
            }.semantics { contentDescription = "上传头像" }, GlassGeometry.Circle) {
            if (space.avatar != null) LocalMedia(space.avatar!!, Modifier.fillMaxSize().padding(7.dp).graphicsLayer {
                shape=AvatarCollapseShape(SpaceScrollMotion.avatar(scrollProgress).neck);clip=true
            })
            else Canvas(Modifier.fillMaxSize().padding(30.dp)) {
                val c = avatarInk
                drawCircle(c.copy(.75f), size.width * .17f, Offset(center.x, size.height * .30f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                drawArc(c.copy(.7f), 180f, 180f, false, Offset(size.width * .16f, size.height * .57f), androidx.compose.ui.geometry.Size(size.width * .68f, size.height * .48f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
            FestivalAvatarWreath(Modifier.fillMaxSize())
            LocalFestivalTheme.current?.let { FestivalEmblem(it.event,Modifier.align(Alignment.BottomEnd).padding(5.dp).size(26.dp)) }
        }
        RoundAction("设置", "gear", onSettings, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 20.dp))
        RoundAction(if(nightMode) "切换日间模式" else "切换夜间模式",if(nightMode) "sun" else "moon",onMode,
            Modifier.align(Alignment.TopStart).padding(top=16.dp,start=20.dp))
        RoundAction("发布动态", "plus", { composer = true }, Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = LocalPageBottomInset.current + 18.dp))
        if (error != null) SpaceText(error!!, 12.sp, Modifier.align(Alignment.TopCenter).padding(top = 20.dp), alpha = .8f)
        if (working && !composer) SpaceText("正在导入头像…", 12.sp, Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
        }
        LiquidGlassEnvironment(base) {
        GlassPanelTransition(if(composer) "compose" else if(deleting!=null) "delete" else "") { key ->
            if(key=="compose") PostComposer(model) { composer=false }
            else {
                BackHandler { deleting=null }
                GlassOptionsPopup({ deleting=null }) {
                    SpaceText("删除这条动态？",AppTypography.Menu,Modifier.padding(horizontal=20.dp,vertical=14.dp),alpha=.7f)
                    GlassMenuOption("保留",{ deleting=null },Modifier.fillMaxWidth())
                    GlassMenuOption("删除",{ deleting?.let(model::deletePost);deleting=null },Modifier.fillMaxWidth())
                }
            }
        }
        }
        viewing?.let { (images,index) -> SpacePhotoViewer(images,index) { viewing=null } }
    }
}

@Composable internal fun RoundAction(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = AppControls.Circle) {
    val color = LocalSpaceColors.current.foreground
    GlassButton(onClick, modifier.size(size).semantics { contentDescription = label }, GlassGeometry.Circle) {
        Canvas(Modifier.fillMaxSize().padding((size-AppControls.Icon)/2)) {
            val w = 1.6.dp.toPx()
            val area=this.size
            if(icon=="trash") {
                drawLine(color,Offset(this.size.width*.15f,this.size.height*.23f),Offset(this.size.width*.85f,this.size.height*.23f),w,StrokeCap.Round)
                drawLine(color,Offset(this.size.width*.38f,this.size.height*.08f),Offset(this.size.width*.62f,this.size.height*.08f),w,StrokeCap.Round)
                drawLine(color,Offset(this.size.width*.25f,this.size.height*.3f),Offset(this.size.width*.3f,this.size.height*.88f),w,StrokeCap.Round)
                drawLine(color,Offset(this.size.width*.75f,this.size.height*.3f),Offset(this.size.width*.7f,this.size.height*.88f),w,StrokeCap.Round)
                drawLine(color,Offset(this.size.width*.3f,this.size.height*.88f),Offset(this.size.width*.7f,this.size.height*.88f),w,StrokeCap.Round)
            } else if(icon=="sun") {
                drawCircle(color,area.minDimension*.23f,style=androidx.compose.ui.graphics.drawscope.Stroke(w))
                repeat(8) { i -> val a=i*kotlin.math.PI.toFloat()/4;val v=Offset(kotlin.math.cos(a),kotlin.math.sin(a));drawLine(color,center+v*area.minDimension*.35f,center+v*area.minDimension*.47f,w,StrokeCap.Round) }
            } else if(icon=="moon") {
                val path=Path().apply { moveTo(area.width*.71f,area.height*.12f);cubicTo(area.width*.05f,-area.height*.02f,-area.width*.02f,area.height*.89f,area.width*.55f,area.height*.91f);cubicTo(area.width*.76f,area.height*.92f,area.width*.93f,area.height*.76f,area.width*.93f,area.height*.60f);cubicTo(area.width*.43f,area.height*.76f,area.width*.39f,area.height*.25f,area.width*.71f,area.height*.12f) }
                drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(w))
            } else if (icon == "plus") { drawLine(color, Offset(center.x, 0f), Offset(center.x, this.size.height), w, StrokeCap.Round); drawLine(color, Offset(0f, center.y), Offset(this.size.width, center.y), w, StrokeCap.Round) }
            else {
                drawCircle(color, this.size.minDimension * .31f, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                drawCircle(color, this.size.minDimension * .1f, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                repeat(8) { i -> val a = i * kotlin.math.PI.toFloat() / 4f; val v = Offset(kotlin.math.cos(a), kotlin.math.sin(a)); drawLine(color, center + v * this.size.minDimension * .31f, center + v * this.size.minDimension * .46f, w * 1.6f, StrokeCap.Round) }
            }
        }
    }
}

@Composable internal fun LocalMedia(path: String, modifier: Modifier) {
    val bitmap by produceState(SpacePhotoCache.peek(path,false), path) {
        value = withContext(Dispatchers.IO) { runCatching { SpacePhotoCache.load(path,false) }.getOrNull() }
    }
    bitmap?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) } ?: Box(modifier)
}

@Composable private fun PostComposer(model: MeViewModel, onClose: () -> Unit) {
    val festival=LocalFestivalTheme.current
    val panelColor=if(LocalSpaceColors.current.foreground==Color.Black) Color.White.copy(.97f) else Color(0xFF172331).copy(.97f)
    var body by rememberSaveable { mutableStateOf("") }
    var sources by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val working by model.working.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoPageFill.MaxImages)) { uris -> sources = ArrayList(uris.take(PhotoPageFill.MaxImages).map { it.toString() }) }
    BackHandler { if(!working) onClose() }
    Box(Modifier.fillMaxSize().imePadding().background(Color.Black.copy(.14f)).clickable { if(!working) onClose() },contentAlignment=Alignment.Center) {
        GlassSurface(Modifier.widthIn(max=320.dp).fillMaxWidth().padding(16.dp).clickable { }, GlassGeometry.Corner(36.dp)) {
            Column(Modifier.background(panelColor,GlassGeometry.Corner(36.dp).shape()).padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SpaceText("新的片刻", 24.sp)
                    festival?.let {FestivalEmblem(it.event,Modifier.size(25.dp))}
                }
                Spacer(Modifier.height(24.dp))
                BasicTextField(body, { if (it.length <= 10000) body = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp).semantics { contentDescription = "动态正文" },
                    textStyle = TextStyle(color = LocalSpaceColors.current.foreground, fontSize = 17.sp, lineHeight = 27.sp), cursorBrush = SolidColor(LocalSpaceColors.current.foreground),
                    decorationBox = { field -> if (body.isEmpty()) SpaceText(festival?.let {festivalEntryPrompt(it.event,FestivalPlace.SPACE)} ?: "今天，有什么想留下？", 17.sp, alpha = .5f); field() })
                if (sources.isNotEmpty()) SpaceText("已选择 ${sources.size} 张照片", 13.sp, alpha = .7f)
                Spacer(Modifier.height(16.dp)); SpaceText(error ?: "仅自己可见 · 保存在手机", 12.sp, alpha = .6f)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton({ if (!working) photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) { SpaceText("添加照片", 14.sp, Modifier.align(Alignment.Center).padding(16.dp)) }
                    GlassButton({ model.publish(body, sources, onClose) }, Modifier.weight(1f)) { SpaceText(if (working) "保存中…" else "发布", 14.sp, Modifier.align(Alignment.Center).padding(16.dp)) }
                }
            }
        }
    }
}


