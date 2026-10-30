package com.qingyu.personalaios.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.pm.ActivityInfo
import android.app.Activity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.BuildConfig
import com.qingyu.personalaios.SpaceViewModel
import com.qingyu.personalaios.core.weather.WeatherScene
import com.qingyu.personalaios.core.weather.rememberWeatherClock
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.shape.ShapeGallery
import com.qingyu.personalaios.feature.today.TodayScreen
import com.qingyu.personalaios.feature.today.TodayViewModel
import com.qingyu.personalaios.feature.calendar.CalendarViewModel
import com.qingyu.personalaios.feature.calendar.CalendarScreen
import com.qingyu.personalaios.feature.diary.DiaryScreen
import com.qingyu.personalaios.feature.diary.DiaryViewModel
import com.qingyu.personalaios.feature.diary.DiaryExitGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.qingyu.personalaios.feature.me.MeScreen
import com.qingyu.personalaios.feature.me.MeViewModel
import com.qingyu.personalaios.feature.me.SettingsScreen
import com.qingyu.personalaios.feature.me.GlassSettingsScreen
import com.qingyu.personalaios.feature.me.GlassTuningMenu
import com.qingyu.personalaios.domain.GlassTarget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalSpaceApp(onReady:()->Unit={},onSurfaceReady:()->Unit={},loading:Boolean=false) {
    val viewModel: SpaceViewModel = viewModel()
    // Activity's Hilt factory injects the repository; drafts also survive tab navigation.
    val diaryModel: DiaryViewModel = viewModel()
    val meModel: MeViewModel = viewModel()
    val calendarModel: CalendarViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val diaryExit = remember { DiaryExitGate() }
    val navigationScope = rememberCoroutineScope()
    var diaryNavigationJob by remember { mutableStateOf<Job?>(null) }
    var diaryTarget by remember { mutableStateOf<String?>(null) }
    fun navigateTab(target: String) {
        if (navController.currentDestination?.route == target) return
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState=true }
            launchSingleTop=true
            // Me is the profile entry; settings always opens through its gear.
            restoreState=target != AppDestination.Me.route
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: AppDestination.Today.route
    var diaryImmersive by rememberSaveable { mutableStateOf(false) }
    var diaryCanvas by remember { mutableStateOf(false) }
    var diaryOpaque by remember { mutableStateOf(false) }
    var diaryCover by remember { mutableStateOf(false) }
    val fullScreen = diaryImmersive && route == AppDestination.Diary.route
    val nightMode=state.settings.nightMode
    val wallpaper=rememberWallpaper(state.settings.background,state.settingsReady)
    LaunchedEffect(state.settingsReady,wallpaper.path,state.settings.background) {
        if(state.settingsReady && wallpaper.path==state.settings.background) onSurfaceReady()
    }
    val startupContext=LocalContext.current
    var resourcesReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val diary=diaryModel.state.first { it.loaded }
        // Keep all existing contents private; only neighbouring visible textures
        // are warmed, with a bounded shared cache, independent of library size.
        try { com.qingyu.personalaios.feature.diary.warmDiaryResources(startupContext,diary) }
        catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
        catch(error:Exception) { android.util.Log.w("DokiLoading","Optional book warmup unavailable",error) }
        meModel.initialized.first { it };calendarModel.initialized.first { it }
        try { com.qingyu.personalaios.feature.me.warmSpaceResources(meModel.space.value) }
        catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
        catch(error:Exception) { android.util.Log.w("DokiLoading","Optional space warmup unavailable",error) }
        resourcesReady=true
    }
    LaunchedEffect(resourcesReady,state.settingsReady,wallpaper.path) {
        if(resourcesReady && state.settingsReady && wallpaper.path==state.settings.background) {
            repeat(3) { withFrameNanos {} };onReady()
        }
    }
    val theme = state.theme()
    val festival=remember(state.now.toLocalDate()) { com.qingyu.personalaios.core.theme.FestivalCalendar.theme(state.now.toLocalDate()) }
    val selectedRoute = if (route.endsWith("-preview") || route == "settings" || route.startsWith("glass-settings")) AppDestination.Me.route else route
    val backdrop = rememberGlassBackdrop()
    val navigationBackdrop = rememberGlassBackdrop(captureBottom=320.dp)
    // Warm the real first page once, without animating invisible full-screen
    // weather captures underneath the opaque loading surface.
    val ambientVisible=wallpaper.image?.hasAlpha!=false || festival!=null
    val seconds = rememberWeatherClock(theme.motionEnabled && ambientVisible && !loading && !(diaryOpaque && route==AppDestination.Diary.route))
    val view = LocalView.current
    val activity = view.context as Activity
    LaunchedEffect(fullScreen,diaryCanvas) {
        val orientation=if(fullScreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if(activity.requestedOrientation!=orientation) {
            // Present the fullscreen paper window before asking Android to
            // rotate; otherwise the system falls back to whole-window rotation.
            if(fullScreen) repeat(2) { withFrameNanos { } }
            activity.requestedOrientation=orientation
        }
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    SideEffect {
        AppSystemBars.update(activity,darkIcons=!nightMode || diaryCanvas && route==AppDestination.Diary.route,immersive=fullScreen)
    }
    val foreground=Color(theme.foreground)
    val light = rememberDeviceGlassLight()
    CompositionLocalProvider(LocalGlassLight provides light,LocalFestivalTheme provides festival) {
    SpaceTheme(foreground) {
    LiquidGlassEnvironment(GlassEnvironment(backdrop,theme,seconds,state.settings.glass,dayMode=!nightMode,groups=state.settings.glassGroups)) {
    Box(Modifier.fillMaxSize()) {
        // Capture weather + pages, excluding the bar itself. Page glass samples the separate
        // weather layer, so the recording graph has no glass sampling itself recursively.
        Box(Modifier.fillMaxSize().captureGlassBackdrop(navigationBackdrop)) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            // Transparent artwork still needs weather underneath; opaque photos do not.
            if(wallpaper.image?.hasAlpha!=false) WeatherScene(theme,seconds=seconds)
            CustomBackground(wallpaper,nightMode)
            FestivalAtmosphere(festival,seconds,!nightMode)
        }
        // DiaryScreen owns the animated white scrim. An opaque backdrop here
        // appeared on its very first expansion frame and flashed on entry/exit.
        CompositionLocalProvider(LocalPageBottomInset provides if(fullScreen) 0.dp else AppNavigationInsets.ContentClearance) {
            NavHost(navController, AppDestination.Today.route,
                // Page surfaces reach the bottom edge. Status/cutout safety is
                // independent of transient system navigation and the keyboard.
                Modifier.fillMaxSize().clipToBounds().graphicsLayer().windowInsetsPadding(
                    (if(fullScreen) WindowInsets.captionBar else WindowInsets.statusBars.union(WindowInsets.captionBar))
                        .union(WindowInsets.displayCutout)),
                enterTransition={ spacePageEnter(theme.motionEnabled, layoutDirection) },
                exitTransition={ spacePageExit(theme.motionEnabled, layoutDirection) },
                popEnterTransition={ spacePageEnter(theme.motionEnabled, layoutDirection) },
                popExitTransition={ spacePageExit(theme.motionEnabled, layoutDirection) }) {
                composable(AppDestination.Today.route) { TodayScreen(state.now, state.weather, onWeatherRefresh={ viewModel.refreshWeather() }, onWeatherRetry={ viewModel.refreshWeather(true) }, onPullRefresh={ viewModel.refreshWeatherNow() }) }
                composable(AppDestination.Calendar.route) { CalendarScreen(calendarModel) }
                composable(AppDestination.Diary.route) { DiaryScreen(diaryModel, diaryExit,diaryImmersive,onCanvas={ diaryCanvas=it },onOpaquePaper={ diaryOpaque=it },onCoverEditing={ diaryCover=it }) { diaryImmersive=it } }
                composable(AppDestination.Me.route) {
                    MeScreen(meModel,nightMode,onMode={ viewModel.setNightMode(!nightMode) }) { navController.navigate("settings") { launchSingleTop = true } }
                }
                composable("settings") {
                    SettingsScreen(background=state.settings.background,working=state.backgroundWorking,error=state.backgroundError,
                        onBackground=viewModel::setBackground,onBack = { navController.popBackStack() },
                        onGlass={ navController.navigate("glass-settings") { launchSingleTop=true } })
                }
                composable("glass-settings") {
                    GlassTuningMenu(onBack={ navController.popBackStack() },onOpen={ target ->
                        navController.navigate("glass-settings/${target.name}") { launchSingleTop=true }
                    })
                }
                composable("glass-settings/{target}") { page ->
                    val target=GlassTarget.entries.firstOrNull { it.name==page.arguments?.getString("target") } ?: GlassTarget.BUTTONS
                    GlassSettingsScreen(state.settings.glassGroups[target],target,
                        onChange={ viewModel.setGlass(target,it) },onBack={ navController.popBackStack() },onLeave=viewModel::commitGlass)
                }
                if (BuildConfig.DEBUG) {
                    composable("shape-preview") { SpaceTheme(SpacePalette.Foreground) {
                        ShapeGallery(LocalPageBottomInset.current)
                    } }
                    composable("glass-preview") { GlassGallery() }
                }
            }
        }
        }
        PaperSystemBarBackdrop(diaryCanvas && route==AppDestination.Diary.route,immersive=fullScreen)
        AnimatedVisibility(visible=!fullScreen && !((diaryCanvas || diaryCover) && route==AppDestination.Diary.route),
            modifier=Modifier.align(Alignment.BottomCenter),enter=slideInVertically(tween(350)) { it }+fadeIn(tween(250)),exit=slideOutVertically(tween(350)) { it }+fadeOut(tween(250))) {
        LiquidGlassEnvironment(GlassEnvironment(navigationBackdrop,theme,seconds,state.settings.glass,dayMode=!nightMode,groups=state.settings.glassGroups)) {
            LiquidTabBar(AppDestination.entries.map { it.label },
                AppDestination.entries.indexOfFirst { it.route==selectedRoute }.coerceAtLeast(0),
                iconLabels=AppDestination.entries.map {it.label},onSelect={ index ->
                    val target = AppDestination.entries[index].route
                    val current = navController.currentDestination?.route
                    val owner = if (current?.endsWith("-preview") == true || current == "settings" || current?.startsWith("glass-settings")==true) AppDestination.Me.route else current
                    if (owner == AppDestination.Diary.route && (target != owner || diaryNavigationJob?.isActive == true)) {
                        diaryTarget = target
                        if (diaryNavigationJob?.isActive != true) diaryNavigationJob = navigationScope.launch {
                            try {
                                if (diaryExit.leave()) diaryTarget?.let(::navigateTab)
                            } finally { diaryTarget = null; diaryNavigationJob = null }
                        }
                    } else if (owner == target && current != target) {
                        navController.popBackStack(target, false)
                    } else if (target != owner) navigateTab(target)
                },modifier=Modifier
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal=AppSpacing.NavigationInset,vertical=AppNavigationInsets.OuterGap))
        }
        }
        }
    }
    }
    }
}
