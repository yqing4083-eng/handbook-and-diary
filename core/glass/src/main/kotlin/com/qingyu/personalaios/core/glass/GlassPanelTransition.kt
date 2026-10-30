package com.qingyu.personalaios.core.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.ui.AppControls
import com.qingyu.personalaios.core.ui.AppCorners
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.*

@Stable internal class GlassMorphFrame(
    val progress: State<Float>, val visibility: State<Float>, val anchor: Rect,
    val startGeometry: GlassGeometry,
    val returnAnchor: Rect, val returnSource: GlassLaunch?, val returning: Boolean = false,
    val lift: Float = 12f,
) {
    var owner: Any? = null
    var target by mutableStateOf<Rect?>(null)
    var geometry by mutableStateOf<GlassGeometry>(GlassGeometry.Corner(AppCorners.Large))
    fun bounds(): Rect = LiquidMenuMotion.bounds(anchor,
        if (returning) returnAnchor else target ?: anchor, progress.value, lift)

    fun roundness(): Float {
        fun value(shape: GlassGeometry?) = when(shape) {
            GlassGeometry.Circle -> 1f
            is GlassGeometry.Corner -> shape.roundness
            else -> 0f
        }
        val p=progress.value.coerceIn(0f,1f)
        val start=value(startGeometry)
        val end=value(if(returning) returnSource?.geometry ?: GlassGeometry.Circle else geometry)
        return start+(end-start)*(p*p*(3f-2f*p))
    }

    fun corner(density: Density): Dp {
        val p = progress.value.coerceIn(0f, 1f)
        val start = (startGeometry as? GlassGeometry.Corner)?.extent
            ?: with(density) { (min(anchor.width, anchor.height) / 2).toDp() }
        val end = if (returning) (returnSource?.geometry as? GlassGeometry.Corner)?.extent
            ?: with(density) { (min(returnAnchor.width, returnAnchor.height) / 2).toDp() }
            else (geometry as? GlassGeometry.Corner)?.extent ?: start
        val extent = with(density) { (min(bounds().width, bounds().height) / 2).toDp() }
        val smooth = p * p * (3f - 2f * p)
        val settled = start + (end - start) * smooth
        val fluid = .18f * sin(PI.toFloat() * p).pow(2)
        return (settled + (extent - settled).coerceAtLeast(0.dp) * fluid).coerceIn(0.dp, extent)
    }
    fun contour(density:Density):GlassGeometry.Corner {
        val tip=(geometry as? GlassGeometry.Corner)?.pointer
        val q=((visibility.value-.78f)/.22f).coerceIn(0f,1f)
        return GlassGeometry.Corner(corner(density),roundness(),tip?.copy(height=tip.height*q*q*(3f-2f*q)))
    }
}
internal val LocalGlassMorph = staticCompositionLocalOf<GlassMorphFrame?> { null }

/** One sampled glass body. Rebase from the currently rendered contour on every interruption. */
@Composable fun GlassPanelTransition(key: String, modifier: Modifier = Modifier, vanishOnClose:Boolean=false, content: @Composable (String) -> Unit) {
    val environment = checkNotNull(LocalGlassEnvironment.current)
    val launch = LocalGlassLaunch.current
    val active = LocalGlassExpandedAnchor.current
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val visibility = remember { Animatable(0f) }
    var visible by remember { mutableStateOf("") }
    var frame by remember { mutableStateOf<GlassMorphFrame?>(null) }
    var root by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(key) {
        if (key.isEmpty() && visible.isEmpty()) return@LaunchedEffect
        val old = frame
        val fresh=launch?.value?.takeIf { android.os.SystemClock.elapsedRealtime()-it.time<1200 }
        val recent = if(vanishOnClose && key.isNotEmpty() && fresh?.token!==old?.returnSource?.token) fresh
            else if(old==null) fresh else old.returnSource
        val side = with(density) { AppControls.Circle.toPx() }
        val button = (if(vanishOnClose) recent?.bounds else null) ?: old?.returnSource?.bounds ?: old?.returnAnchor ?: recent?.bounds ?: Rect(root.center - Offset(side / 2, side / 2), Size(side, side))
        val start = old?.bounds() ?: button
        val shape = old?.let { GlassGeometry.Corner(it.corner(density),it.roundness()) } ?: recent?.geometry ?: GlassGeometry.Circle
        // Sample before snapTo: both bounds and contour match the last rendered frame exactly.
        val next = GlassMorphFrame(progress.asState(), visibility.asState(), start, shape,
            if(key.isEmpty() && vanishOnClose) Rect(button.center,Size.Zero) else button, recent, returning = key.isEmpty(), lift = with(density) { 12.dp.toPx() }).apply {
            target = old?.target
            geometry = old?.geometry ?: geometry
            if (key.isEmpty()) owner = old?.owner
        }
        progress.snapTo(0f)
        frame = next
        active?.value = recent?.token
        if (key.isNotEmpty()) {
            visible = key
            // Measure the new content once; wait at most three frames, rather than guess its size.
            repeat(3) { withFrameNanos { } }
        }
        val spec = spring<Float>(dampingRatio = if (key.isEmpty()) .86f else .76f,
            stiffness = if (key.isEmpty()) 460f else 340f, visibilityThreshold = .001f)
        if (environment.theme.motionEnabled) coroutineScope {
            launch { progress.animateTo(1f, spec) }
            launch { visibility.animateTo(if (key.isEmpty()) 0f else 1f, spec) }
        } else {
            progress.snapTo(1f)
            visibility.snapTo(if (key.isEmpty()) 0f else 1f)
        }
        if (key.isEmpty()) { visible = ""; frame = null; active?.value = null }
    }
    DisposableEffect(Unit) { onDispose { active?.value = null } }
    Box(modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }) {
        if (visible.isNotEmpty()) frame?.let { current ->
            val rect = current.bounds()
            if(rect.width>1f && rect.height>1f) CompositionLocalProvider(LocalGlassMorph provides null) {
                GlassSurface(Modifier.offset { IntOffset((rect.left - root.left).roundToInt(), (rect.top - root.top).roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
                    current.contour(density),layered=true) {
                    current.returnSource?.takeUnless { current.returning && vanishOnClose }?.let { source ->
                        Box(Modifier.align(Alignment.Center)
                            .size(with(density) { current.returnAnchor.width.toDp() }, with(density) { current.returnAnchor.height.toDp() })
                            .graphicsLayer { alpha = (1f - visibility.value / .22f).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.Center, content = source.content)
                    }
                }
            }
            CompositionLocalProvider(LocalGlassMorph provides current) { content(visible) }
        }
    }
}

/** The options are clipped to the growing glass, rather than drawn in another dialog. */
internal class MorphContentClip(private val frame: GlassMorphFrame): Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val natural = frame.target ?: return Outline.Generic(Path())
        val rect = frame.bounds()
        val outline = frame.contour(density).shape().createOutline(Size(rect.width, rect.height), layoutDirection, density)
        val path = Path()
        val offset = rect.topLeft - natural.topLeft
        when (outline) {
            is Outline.Generic -> path.addPath(outline.path, offset)
            is Outline.Rounded -> path.addPath(Path().apply { addRoundRect(outline.roundRect) }, offset)
            is Outline.Rectangle -> path.addRect(outline.rect.translate(offset))
        }
        return Outline.Generic(path)
    }
}
