package com.qingyu.personalaios.feature.calendar

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.qingyu.personalaios.domain.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.core.theme.FestivalCalendar
import com.qingyu.personalaios.core.theme.FestivalEvent
import com.qingyu.personalaios.core.glass.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun CalendarScreen(model:CalendarViewModel) {
    val day by model.day.collectAsStateWithLifecycle()
    val calendar by model.calendar.collectAsStateWithLifecycle()
    val completed by model.completed.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    DisposableEffect(owner,model) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) model.refresh() }
        owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var month by rememberSaveable { mutableStateOf(day.withDayOfMonth(1).toString()) }
    val visible=LocalDate.parse(month)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                SpaceText("日历",24.sp)
                LocalFestivalTheme.current?.let {FestivalEmblem(it.event,Modifier.size(22.dp))}
            }
            Box(Modifier.size(88.dp,44.dp).semantics { contentDescription="回到今天" }
                .clickable(interactionSource=null,indication=null,role=Role.Button) { val now=LocalDate.now();model.select(now);month=now.withDayOfMonth(1).toString() },contentAlignment=Alignment.Center) {
                HelloWord("Today",Modifier.size(72.dp,36.dp),intArrayOf(LocalSpaceColors.current.foreground.toArgb(),LocalSpaceColors.current.foreground.toArgb()))
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            GlassButton({ month=visible.minusMonths(1).toString() },Modifier.size(AppControls.Circle).semantics { contentDescription="上个月" },GlassGeometry.Circle) { GlassChevron() }
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                AnimatedContent(visible,transitionSpec={ (fadeIn(tween(180))+slideInVertically(tween(260)) { it/3 }) togetherWith
                    (fadeOut(tween(120))+slideOutVertically(tween(200)) { -it/3 }) },label="month heading") { shown ->
                    GlassText(shown.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH)),22.sp)
                }
            }
            GlassButton({ month=visible.plusMonths(1).toString() },Modifier.size(AppControls.Circle).semantics { contentDescription="下个月" },GlassGeometry.Circle) { GlassChevron(true) }
        }
        Spacer(Modifier.height(22.dp))
        AcrylicSection(Modifier.fillMaxWidth().semantics { contentDescription="日历玻璃月视图" },transparent=true) {
        FestivalSurfaceOrnament(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) { listOf("MON","TUE","WED","THU","FRI","SAT","SUN").forEach { Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { SpaceText(it,10.sp,alpha=.5f) } } }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(visible,modifier=Modifier.fillMaxWidth().clipToBounds(),transitionSpec={
            val direction=if(targetState>initialState) 1 else -1
            (slideInHorizontally(tween(320,easing=LiquidMotion.Navigation)) { direction*it }+fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(tween(320,easing=LiquidMotion.Navigation)) { -direction*it }+fadeOut(tween(180)))
        },label="calendar month") { shown ->
          MonthGrid(shown,day,model::select)
        }
        }
        }
        Spacer(Modifier.height(20.dp))
        AnimatedContent(day,transitionSpec={ (fadeIn(tween(240))+slideInVertically(tween(260)) { it/12 }) togetherWith fadeOut(tween(120)) },label="selected day fortune") { shownDay ->
          AcrylicSection(Modifier.fillMaxWidth().semantics { contentDescription="节日与日程玻璃区" },style=GlassStyle.QUIET,transparent=true) {
              Box(Modifier.padding(20.dp)) {
                  DayPlanSection(shownDay,calendar,completed,model::toggle,onCalendarPermission={
                      val preferences=context.getSharedPreferences("permissions",0)
                      val activity=context as? android.app.Activity
                      val blocked=preferences.getBoolean("calendar",false) && activity!=null &&
                          !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity,Manifest.permission.READ_CALENDAR)
                      if(blocked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
                      else { preferences.edit().putBoolean("calendar",true).apply();permission.launch(Manifest.permission.READ_CALENDAR) }
                  },onRetry=model::refresh)
              }
          }
        }
        Spacer(Modifier.height(LocalPageBottomInset.current))
    }
}

private data class CalendarDayDecoration(val label:String,val event:FestivalEvent?)
@Composable private fun MonthGrid(visible:LocalDate,day:LocalDate,onSelect:(LocalDate)->Unit) {
    val festivals by produceState<Map<Int,CalendarDayDecoration>>(emptyMap(),visible) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            (1..visible.lengthOfMonth()).associateWith {
                val date=visible.withDayOfMonth(it)
                CalendarDayDecoration(CalendarAlmanac.festival(date),FestivalCalendar.events(date).firstOrNull())
            }
        }
    }
    val density=LocalDensity.current
    val selectedFestival=remember(day) {FestivalCalendar.events(day).firstOrNull()}
    val markerColor by animateColorAsState(selectedFestival?.let {Color(it.accent)} ?: Color(0xFF168BFF),tween(240),label="festival date colour")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val offset=visible.dayOfWeek.value-1
        val inMonth=day.year==visible.year && day.month==visible.month
        val index=offset+day.dayOfMonth-1
        val step=with(density) { (maxWidth/7).toPx() }
        val rowHeight=with(density) { 62.dp.toPx() };val diameter=40.dp
        val target=Offset((index%7)*step+(step-with(density) { diameter.toPx() })/2,(index/7)*rowHeight)
        val position by animateOffsetAsState(target,spring(dampingRatio=.86f,stiffness=480f),label="date circle position")
        val markerAlpha by animateFloatAsState(if(inMonth) 1f else 0f,tween(160),label="date circle visibility")
        GlassSurface(Modifier.offset { IntOffset(position.x.toInt(),position.y.toInt()) }.size(diameter)
            .graphicsLayer { alpha=markerAlpha }.semantics { contentDescription="所选日期圆形标记" },GlassGeometry.Circle,style=GlassStyle.QUIET) {
            val foreground=LocalSpaceColors.current.foreground
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(markerColor.copy(alpha=.22f))
                drawCircle(foreground.copy(alpha=.17f),style=androidx.compose.ui.graphics.drawscope.Stroke(.7.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxWidth()) {
        repeat((offset+visible.lengthOfMonth()+6)/7) { row ->
            Row(Modifier.fillMaxWidth()) { repeat(7) { column ->
                val number=row*7+column-offset+1
                Box(Modifier.weight(1f).height(62.dp),contentAlignment=Alignment.TopCenter) {
                    if(number in 1..visible.lengthOfMonth()) {
                        val date=visible.withDayOfMonth(number)
                        val emphasis by animateFloatAsState(if(date==day || date==LocalDate.now()) 1f else .65f,tween(200),label="date emphasis")
                        Column(Modifier.fillMaxWidth().clickable(interactionSource=null,indication=null) { onSelect(date) }
                            .semantics(mergeDescendants=true) { contentDescription="日期 $date";selected=date==day },horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.size(diameter),contentAlignment=Alignment.Center) {
                                if(date==LocalDate.now() && date!=day) {
                                    val foreground=LocalSpaceColors.current.foreground
                                    Canvas(Modifier.fillMaxSize()) { drawCircle(foreground.copy(alpha=.28f),style=androidx.compose.ui.graphics.drawscope.Stroke(.7.dp.toPx())) }
                                }
                                SpaceText("$number",16.sp,alpha=emphasis)
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal=1.dp).padding(top=4.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                                festivals[number]?.event?.let {
                                    Box(Modifier.size(8.dp).clearAndSetSemantics {}) {FestivalEmblem(it,Modifier.fillMaxSize())}
                                    Spacer(Modifier.width(2.dp))
                                }
                                SpaceText(festivals[number]?.label.orEmpty(),9.sp,Modifier.weight(1f,fill=false),alpha=if(date==day) 1f else .7f,maxLines=1)
                            }
                        }
                    }
                }
            } }
        }
        }
    }
}

@Composable private fun DayPlanSection(day:LocalDate,calendar:CalendarState,completed:Collection<String>,onToggle:(String)->Unit,
    onCalendarPermission:()->Unit,onRetry:()->Unit) {
    val fortune=remember(day) { DailyFortunes.forDate(day) }
    val festivals=remember(day) { com.qingyu.personalaios.core.theme.FestivalCalendar.events(day) }
    val otherFestival=remember(day) { CalendarAlmanac.festival(day) }
    val zone=remember { ZoneId.systemDefault() }
    val events=remember(calendar.events,day,zone) { calendar.events.filter { CalendarRules.overlaps(it,day,zone) }.sortedWith(compareBy<CalendarEvent> { !it.allDay }.thenBy { it.start }) }
    val foreground=LocalSpaceColors.current.foreground
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            SpaceText("节日与日程",22.sp,Modifier.weight(1f),weight=FontWeight.Medium)
            Column(horizontalAlignment=Alignment.End) {
                SpaceText(day.format(DateTimeFormatter.ofPattern("MM / dd")),13.sp,alpha=.75f)
                SpaceText(remember(day) { CalendarAlmanac.lunarDate(day) },11.sp,Modifier.padding(top=4.dp),alpha=.6f)
            }
        }
        festivals.forEach { event ->
            Row(Modifier.padding(top=20.dp),verticalAlignment=Alignment.CenterVertically) {
                FestivalEmblem(event,Modifier.size(36.dp));Spacer(Modifier.width(12.dp))
                Column {
                    SpaceText(event.name,19.sp,weight=FontWeight.Medium)
                    SpaceText(festivalEntryPrompt(event,FestivalPlace.DIARY),10.sp,Modifier.padding(top=4.dp),alpha=.6f,maxLines=2)
                }
            }
            SpaceText(event.introduction,13.sp,Modifier.padding(top=8.dp),alpha=.8f)
        }
        if(festivals.isEmpty() && otherFestival.isNotEmpty()) SpaceText(otherFestival,19.sp,Modifier.padding(top=18.dp),weight=FontWeight.Medium)
        Row(Modifier.fillMaxWidth().padding(top=22.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
            SpaceText(if(day==LocalDate.now()) "今日待办" else "当日安排",15.sp,Modifier.weight(1f),weight=FontWeight.Medium)
            if(calendar.granted) SpaceText("${events.count { it.id !in completed }} 项待完成",11.sp,alpha=.6f)
        }
        when {
            !calendar.granted -> {
                SpaceText("允许读取系统日历，在这里查看当天要做的事。",13.sp,alpha=.75f)
                GlassButton(onCalendarPermission,Modifier.padding(top=10.dp)) { SpaceText("读取系统日程",13.sp,Modifier.padding(horizontal=16.dp,vertical=10.dp)) }
            }
            calendar.message!=null -> SpaceText(checkNotNull(calendar.message),13.sp,Modifier.clickable(interactionSource=null,indication=null,onClick=onRetry).padding(vertical=10.dp),alpha=.75f)
            events.isEmpty() -> SpaceText("这一天暂时没有日程，留一点空白给自己。",14.sp,Modifier.padding(vertical=8.dp),alpha=.75f)
            else -> events.forEach { event ->
                val done=event.id in completed
                Row(Modifier.fillMaxWidth().semantics { contentDescription="日程 ${event.title}";stateDescription=if(done) "已完成" else "未完成" }
                    .clickable(interactionSource=null,indication=null) { onToggle(event.id) }.padding(vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Canvas(Modifier.size(18.dp)) {
                        drawCircle(if(done) Color(0xFF168BFF) else foreground.copy(alpha=.4f),style=if(done) androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        if(done) {
                            val path=Path().apply { moveTo(size.width*.24f,size.height*.51f);lineTo(size.width*.43f,size.height*.69f);lineTo(size.width*.77f,size.height*.31f) }
                            drawPath(path,Color.White,style=androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
                        }
                    }
                    SpaceText(if(event.allDay) "全天" else event.start.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),12.sp,Modifier.width(44.dp),alpha=if(done) .4f else .7f)
                    SpaceText(event.title,16.sp,Modifier.weight(1f),alpha=if(done) .45f else 1f,maxLines=3)
                }
            }
        }
        FestivalDivider(Modifier.padding(top=14.dp,bottom=10.dp),festivals.firstOrNull())
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SpaceText("每日小签 · ${fortune.word}",12.sp,alpha=.6f)
            SpaceText(fortune.title,14.sp,weight=FontWeight.Medium)
        }
        SpaceText(fortune.message,12.sp,Modifier.padding(top=8.dp),alpha=.65f)
        SpaceText("宜 ${fortune.favor} · 放下 ${fortune.avoid}",11.sp,Modifier.padding(top=10.dp),alpha=.6f)
        Row(Modifier.padding(top=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(8.dp)) { drawCircle(Color(fortune.color)) }
            SpaceText("幸运色 · ${fortune.colorName}",11.sp,alpha=.6f)
        }
    }
}
