package com.qingyu.personalaios.core.glass

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.qingyu.personalaios.core.theme.ThemeTokens
import com.qingyu.personalaios.domain.GlassParameters
import com.qingyu.personalaios.domain.GlassOptics
import com.qingyu.personalaios.domain.GlassGroups
import com.qingyu.personalaios.domain.GraphicsQuality
import com.qingyu.personalaios.domain.resolve

@Stable
class GlassBackdrop internal constructor(internal val upstream: Backdrop)

@Composable
fun rememberGlassBackdrop(captureBottom:Dp=Dp.Unspecified): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    val band=with(LocalDensity.current) { if(captureBottom.isSpecified) captureBottom.toPx() else Float.NaN }
    // Navigation only samples the bottom band. Preserve full-screen coordinates,
    // but avoid rasterizing the rest of the page into a second full-screen texture.
    val recording:ContentDrawScope.()->Unit=remember(band) {
        if(band.isFinite()) { { val content=this;clipRect(top=(size.height-band).coerceAtLeast(0f)) { content.drawContent() } } }
        else { { drawContent() } }
    }
    SideEffect { layer.compositingStrategy = if(band.isFinite()) CompositingStrategy.Auto else CompositingStrategy.Offscreen }
    val upstream = rememberLayerBackdrop(graphicsLayer = layer,onDraw=recording)
    return remember(upstream) { GlassBackdrop(upstream) }
}

/** Record only layers behind the consumer. Never include the glass sampling this recording. */
fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop): Modifier = layerBackdrop(backdrop.upstream as LayerBackdrop)

@Composable fun rememberGlassContentBackdrop(content: GlassBackdrop): GlassBackdrop {
    val environment=checkNotNull(LocalGlassEnvironment.current)
    val combined=rememberCombinedBackdrop(environment.backdrop.upstream,content.upstream)
    return remember(combined) { GlassBackdrop(combined) }
}

data class GlassEnvironment(val backdrop: GlassBackdrop, val theme: ThemeTokens, val seconds: State<Float>,
    val parameters: GlassParameters = GlassParameters.Unified, val dayMode: Boolean = false,
    // Kept for explicit wallpaper previews; glass glyphs sample the current material layer.
    val wallpaperBackdrop: GlassBackdrop = backdrop,
    val groups:GlassGroups=if(parameters==GlassParameters.Unified) GlassGroups() else GlassGroups(parameters,parameters,parameters),
    // Local material treatment, independent of the persisted preference range.
    val surfaceTintOverride:Float?=null,val layeredTintOverride:Float?=null) {
    val optics: GlassOptics get() = sharedGlassOptics(theme.motionEnabled,groups.buttons)
    val surfaceOptics: GlassOptics get() = surfaceTintOverride?.takeIf { it.isFinite() }?.let { optics.copy(tint=it.coerceIn(0f,1f)) } ?: optics
    val layeredOptics:GlassOptics get()=sharedGlassOptics(theme.motionEnabled,groups.layered).let { value ->
        layeredTintOverride?.takeIf { it.isFinite() }?.let { value.copy(tint=it.coerceIn(0f,1f)) } ?: value }
    val navigationOptics:GlassOptics get()=sharedGlassOptics(theme.motionEnabled,groups.bottomBar)
    val numeralOptics:GlassOptics get()=sharedGlassOptics(theme.motionEnabled,groups.numbers)
    val surfaceTint: androidx.compose.ui.graphics.Color get() =
        androidx.compose.ui.graphics.Color(theme.glassTint)
}

/** Transparent, sharp glass in both modes. */
internal fun sharedGlassOptics(motionEnabled: Boolean,parameters:GlassParameters=GlassParameters.Unified): GlassOptics =
    parameters.resolve(1f,motionEnabled,true)

// Weather/theme and content backdrops can change while a route slides. Track only
// consumers instead of invalidating the complete navigation subtree.
val LocalGlassEnvironment = compositionLocalOf<GlassEnvironment?> { null }
internal val LocalGlassDepth=compositionLocalOf { 0 }
internal val LocalGlassLaunchPoint = staticCompositionLocalOf<MutableState<Offset?>?> { null }
internal data class GlassLaunch(val bounds:Rect,val time:Long,val token:Any,val geometry:GlassGeometry,
    val content:@Composable androidx.compose.foundation.layout.BoxScope.()->Unit)
internal val LocalGlassLaunch = staticCompositionLocalOf<MutableState<GlassLaunch?>?> { null }
internal val LocalGlassExpandedAnchor = staticCompositionLocalOf<MutableState<Any?>?> { null }

@Composable
fun LiquidGlassEnvironment(environment: GlassEnvironment, content: @Composable () -> Unit) {
    val inherited=LocalGlassLaunchPoint.current
    val point=remember { mutableStateOf<Offset?>(null) }
    val launch=LocalGlassLaunch.current ?: remember { mutableStateOf<GlassLaunch?>(null) }
    val expanded=LocalGlassExpandedAnchor.current ?: remember { mutableStateOf<Any?>(null) }
    CompositionLocalProvider(LocalGlassEnvironment provides environment,LocalGlassLaunchPoint provides (inherited ?: point),LocalGlassLaunch provides launch,LocalGlassExpandedAnchor provides expanded, content = content)
}
