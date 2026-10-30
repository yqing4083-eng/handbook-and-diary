package com.qingyu.personalaios.feature.today

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.core.glass.*
import androidx.lifecycle.*
import kotlinx.coroutines.*
import com.qingyu.personalaios.domain.*
import java.time.ZonedDateTime
import java.time.Duration
import kotlin.math.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(now: ZonedDateTime, weather: WeatherState, onWeatherRefresh: ()->Unit, onWeatherRetry: ()->Unit, onPullRefresh:suspend ()->Unit) {
    val context=LocalContext.current
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val asked=remember { context.getSharedPreferences("permissions",0) }
    var permissionPanel by remember { mutableStateOf("") }
    val refresh=rememberUpdatedState(onWeatherRefresh)
    fun hasLocation()=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    fun appSettings() { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }
    val locationLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onWeatherRetry() }
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while(isActive) { if(hasLocation()) refresh.value();delay(15*60_000L) }
    } }
    fun weatherAction() { if(hasLocation()) {
        val location=context.getSystemService(android.location.LocationManager::class.java)
        if(!location.isLocationEnabled) context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) else onWeatherRetry()
    } else permissionPanel="location" }
    val scroll=rememberScrollState()
    val pull=rememberTodayRefresh(scroll) { if(hasLocation()) onPullRefresh() else permissionPanel="location" }
    val data = weather.data
    BoxWithConstraints(Modifier.fillMaxSize().nestedScroll(pull.connection)) {
    val compact = maxHeight < 740.dp
    Column(Modifier.fillMaxSize().graphicsLayer { translationY=pull.displayOffset*.55f }.verticalScroll(scroll)
        .padding(horizontal = AppSpacing.Page, vertical = 24.dp)) {
        Column(Modifier.fillMaxWidth()) {
        MonthHeader(now.monthValue)
        Spacer(Modifier.height(30.dp))
        val dateSize=(if(compact) 132f else 154f)/LocalDensity.current.fontScale
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,horizontalArrangement=Arrangement.Start) {
            Box {
                GlassNumeral(now.format(DateTimeFormatter.ofPattern("dd")),dateSize.sp)
                FestivalDateAdornment(Modifier.align(Alignment.TopEnd).offset(x=8.dp,y=(-30).dp).size(72.dp,54.dp))
            }
            Spacer(Modifier.width(8.dp))
            GlassNumeral(now.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH)).uppercase(),(dateSize/4).sp,liquidDigits=false)
        }
        FestivalSignature(Modifier.padding(top=10.dp))
        Spacer(Modifier.height(if (compact) 18.dp else 28.dp))
        SpaceText(if(weather.hasData) WeatherCopy.select(data,now) else "慢慢开始今天，把生活里的片刻留下。", 15.sp, alpha = .9f)
        Spacer(Modifier.height(if (compact) 22.dp else 32.dp))
        }
        if(weather.hasData) {
            MinimalWeather(weather,now)
        } else {
            GlassButton({ weatherAction() },Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    SpaceText(if(weather.loading) "正在获取当前位置天气…" else "开启定位天气",17.sp)
                    Spacer(Modifier.height(6.dp));SpaceText("根据你所在的位置，同步天气与空间氛围。",12.sp,alpha=.7f)
                }
            }
        }
        weather.message?.let { SpaceText(it,12.sp,Modifier.padding(top=8.dp),alpha=.7f) }
        data.alerts.forEach { alert -> SpaceText("天气预警 · $alert",13.sp,Modifier.padding(top=8.dp)) }
        Spacer(Modifier.height(12.dp))

        Spacer(Modifier.height(LocalPageBottomInset.current))
    }
    TodayRefreshIndicator(pull,Modifier.align(Alignment.TopCenter))
    GlassPanelTransition(permissionPanel) { panel ->
        BackHandler { permissionPanel="" }
        val isLocation=true
        val permission=if(isLocation) Manifest.permission.ACCESS_COARSE_LOCATION else Manifest.permission.READ_CALENDAR
        val blocked=asked.getBoolean(panel,false) && !ActivityCompat.shouldShowRequestPermissionRationale(context as Activity,permission)
        GlassOptionsPopup({ permissionPanel="" },width=296.dp) {
            Column(Modifier.padding(horizontal=20.dp,vertical=16.dp)) {
                SpaceText("天气随你所在",22.sp)
                Spacer(Modifier.height(14.dp))
                SpaceText("允许使用当前位置查询天气。选择精确位置，可更准确识别区县；大致位置也能查询天气。",15.sp,alpha=.8f)
            }
            GlassMenuOption("稍后",{ permissionPanel="" },Modifier.fillMaxWidth())
            GlassMenuOption(if(blocked) "前往设置" else "允许定位",{
                permissionPanel=""
                if(blocked) appSettings() else {
                    asked.edit().putBoolean(panel,true).apply()
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },Modifier.fillMaxWidth())
        }
    }
    }
}

@Composable internal fun HeaderClock(modifier:Modifier=Modifier) {
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    var value by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while(isActive) { value=ZonedDateTime.now();delay(60_000-value.second*1000L-value.nano/1_000_000L) }
    } }
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val density=LocalDensity.current
        val fit=remember(maxWidth,maxHeight,density) {
            val paint=android.graphics.Paint().apply { textSize=100f;typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD) }
            val path=android.graphics.Path();paint.getTextPath("88:88",0,5,0f,0f,path)
            val bounds=android.graphics.RectF();path.computeBounds(bounds,true)
            with(density) { min((maxWidth-8.dp).toPx()/bounds.width(),(maxHeight-8.dp).toPx()/bounds.height())*100f/fontScale/this.density }
        }
        GlassNumeral(value.format(DateTimeFormatter.ofPattern("HH:mm")),fit.sp)
    }
}

@Composable private fun MinimalWeather(weather:WeatherState,now:ZonedDateTime) {
    val data=weather.data
    val foreground=LocalSpaceColors.current.foreground
    AcrylicSection(Modifier.fillMaxWidth().semantics { contentDescription="实时天气信息" },transparent=true) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SpaceText(data.city,14.sp,Modifier.weight(1f),alpha=.7f,maxLines=2)
            if(weather.loading || weather.isStale) {
                SpaceText(if(weather.loading) "同步中" else "缓存天气",11.sp,alpha=.6f)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=24.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            // Keep the decorative numeral proportional; supporting text retains system font scaling.
            GlassNumeral("${data.temperature}°",(72f/LocalDensity.current.fontScale).sp)
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.End) {
                GlassSurface(Modifier.size(44.dp),GlassGeometry.Circle,style=GlassStyle.QUIET) {
                    WeatherMark(data.theme,Modifier.align(Alignment.Center).size(30.dp))
                }
                Spacer(Modifier.width(10.dp))
                SpaceText(data.description,20.sp,Modifier.weight(1f,fill=false),maxLines=2)
            }
        }
        Box(Modifier.fillMaxWidth().height(.5.dp).background(foreground.copy(alpha=.14f)))
        Column(Modifier.padding(top=18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                WeatherDetail("体感","${data.feelsLike}°",Modifier.weight(1f))
                WeatherDetail("湿度","${data.humidity}%",Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                WeatherDetail("风","${data.windDirection} ${data.windLevel}级",Modifier.weight(1f))
                WeatherDetail("降水","${String.format(Locale.ROOT,"%.1f",data.precipitationMm)} mm",Modifier.weight(1f))
            }
        }
        SpaceText("Open-Meteo · ${data.observedAt.atZone(now.zone).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}",
            10.sp,Modifier.padding(top=24.dp),alpha=.55f)
    }
    }
}

@Composable private fun WeatherDetail(label:String,value:String,modifier:Modifier) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        SpaceText(label,13.sp,alpha=.6f)
        Box(Modifier.weight(1f),contentAlignment=Alignment.CenterEnd) {
            SpaceText(value,14.sp)
        }
    }
}

@Composable
private fun WeatherMark(theme: WeatherTheme, modifier: Modifier) {
    val color = LocalSpaceColors.current.foreground
    val environment=checkNotNull(LocalGlassEnvironment.current)
    Canvas(modifier.semantics { contentDescription=when(theme) {
        WeatherTheme.CLEAR -> "晴天天气图标"
        WeatherTheme.SNOW -> "雪天天气图标"
        WeatherTheme.LIGHT_RAIN,WeatherTheme.HEAVY_RAIN -> "雨天天气图标"
        WeatherTheme.THUNDER -> "雷雨天气图标"
        WeatherTheme.FOG -> "雾天天气图标"
        else -> "多云天气图标"
    } }.graphicsLayer {
        val t=environment.seconds.value
        if(theme==WeatherTheme.CLEAR) rotationZ=t*5f
        else { translationY=kotlin.math.sin(t*.8f)*1.5.dp.toPx();scaleX=1f+.035f*kotlin.math.sin(t*.7f);scaleY=scaleX }
    }) {
        val c = center
        val r = size.minDimension * .35f
        val stroke = 1.2.dp.toPx()
        when (theme) {
            WeatherTheme.SNOW -> repeat(6) { index ->
                val angle = index * PI / 3
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val end = c + direction * r
                drawLine(color.copy(alpha = .8f), c, end, stroke, StrokeCap.Round)
                val joint = c + direction * r * .62f
                repeat(2) { branch ->
                    val branchAngle = angle + if (branch == 0) PI / 3 else -PI / 3
                    drawLine(color.copy(alpha = .8f), joint,
                        joint + Offset(cos(branchAngle).toFloat(), sin(branchAngle).toFloat()) * r * .30f, stroke, StrokeCap.Round)
                }
            }
            WeatherTheme.LIGHT_RAIN, WeatherTheme.HEAVY_RAIN, WeatherTheme.THUNDER -> {
                drawArc(color.copy(alpha = .8f), 185f, 180f, false,
                    topLeft = Offset(c.x - r, c.y - r * .8f), size = androidx.compose.ui.geometry.Size(r * 2, r * 1.35f), style = Stroke(stroke, cap = StrokeCap.Round))
                repeat(3) { index ->
                    val x = c.x + (index - 1) * r * .65f
                    drawLine(color.copy(alpha = .8f), Offset(x, c.y + r * .4f), Offset(x - r * .18f, c.y + r), stroke, StrokeCap.Round)
                }
            }
            WeatherTheme.CLEAR -> {
                drawCircle(color.copy(alpha = .8f), r * .38f, c, style = Stroke(stroke))
                repeat(8) { index ->
                    val angle = index * PI / 4
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(color.copy(alpha = .65f), c + direction * r * .72f, c + direction * r, stroke, StrokeCap.Round)
                }
            }
            else -> repeat(3) { index ->
                val y = c.y + (index - 1) * r * .55f
                drawLine(color.copy(alpha = .75f), Offset(c.x - r + index * r * .15f, y), Offset(c.x + r - index * r * .2f, y), stroke, StrokeCap.Round)
            }
        }
    }
}
