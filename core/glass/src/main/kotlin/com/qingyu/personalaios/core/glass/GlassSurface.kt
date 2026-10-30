package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.catalog.components.applyLiquidButtonDeformation
import com.qingyu.personalaios.domain.GraphicsQuality
import com.qingyu.personalaios.core.ui.AppCorners

enum class GlassStyle { REGULAR, QUIET, IDENTITY }

/** App policy only; sampling, refraction, blur, dispersion and highlights run upstream. */
@Composable
fun GlassSurface(modifier: Modifier = Modifier,
    geometry: GlassGeometry = GlassGeometry.Corner(AppCorners.Large),
    style: GlassStyle = GlassStyle.REGULAR, emphasized: Boolean = false,
    interaction: InteractiveHighlight? = null,
    layered:Boolean=false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val light = LocalGlassLight.current
    val edgeColor = LocalGlassEdgeColor.current
    val environment = checkNotNull(LocalGlassEnvironment.current)
    val morph=LocalGlassMorph.current
    val identityToken=remember { Any() }
    if(morph!=null && (morph.owner==null || morph.owner===identityToken)) {
        morph.owner=identityToken
        Box(modifier.onGloballyPositioned { val bounds=it.findRootCoordinates().localBoundingBoxOf(it,clipBounds=false);if(morph.target!=bounds) morph.target=bounds;morph.geometry=geometry }
            .graphicsLayer { alpha=((morph.visibility.value-.55f)/.35f).coerceIn(0f,1f);clip=true;shape=MorphContentClip(morph) },content=content)
        return
    }
    // An expanding panel has exactly one sampled body. Its fields must not draw secondary
    // outlines across the moving contour, or allocate their own backdrop shader passes.
    if(morph!=null) { Box(modifier,content=content);return }
    val shape = remember(geometry) { geometry.shape() }
    val theme = environment.theme
    val optics = if(layered || LocalGlassDepth.current>0) environment.layeredOptics else environment.surfaceOptics
    val quiet = style == GlassStyle.QUIET
    val identity = style == GlassStyle.IDENTITY
    Box(modifier.drawBackdrop(
        backdrop = environment.backdrop.upstream, shape = { shape },
        effects = {
            if (!identity) {
                if(optics.blur>0f) blur(optics.blur.dp.toPx())
                val strength = optics.refraction
                val breath = if (!quiet && optics.interaction > 0f)
                    .25f * optics.interaction * kotlin.math.sin(environment.seconds.value * .55f) else 0f
                liquidLens(minOf(size.minDimension * .45f, 20.dp.toPx()),
                    ((if (quiet) 6f else 12f).dp.toPx() + breath) * strength,
                    depthEffect = !quiet,
                    chromaticAberration = optics.chromatic,dispersionStrength=optics.dispersion)
            }
        },
        highlight = { if (identity) null else glassHighlight(optics.highlight,light.value,edgeColor) },
        shadow = null,
        layerBlock = if (interaction != null && optics.interaction > 0f) {
            { applyLiquidButtonDeformation(interaction, optics.interaction) }
        } else null,
        onDrawBackdrop = { draw ->
            val magnification = if (identity) 1f else optics.magnification
            scale(magnification, magnification, pivot = center) { draw() }
        },
        onDrawSurface = {
            if (!identity) drawRect(environment.surfaceTint.copy(alpha =
                optics.tint))
        },
    ).then(if (interaction != null && optics.interaction > 0f) interaction.modifier else Modifier), content = content)
}
