/*
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Source: Kyant0/AndroidLiquidGlass, tag 1.0.6, catalog/components/LiquidButton.kt.
 * Modified by PersonalAIOS: shape, shared optics, adaptive height, role and reduced motion.
 * Original elastic transform extracted for reuse by slider thumbs. See third_party/android-liquid-glass.
 */
package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.qingyu.personalaios.core.glass.LocalGlassLight
import com.qingyu.personalaios.core.glass.LocalGlassEdgeColor
import com.qingyu.personalaios.core.glass.LocalGlassInteractionColor
import com.qingyu.personalaios.core.glass.glassHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.qingyu.personalaios.core.glass.liquidLens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.qingyu.personalaios.domain.GlassOptics
import kotlin.math.*

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    optics: GlassOptics,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    tint: Color = Color.White,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val light = LocalGlassLight.current
    val edgeColor = LocalGlassEdgeColor.current
    val animationScope = rememberCoroutineScope()
    val currentOptics = rememberUpdatedState(optics)
    val pressColor=rememberUpdatedState(LocalGlassInteractionColor.current)
    val isInteractive = optics.interaction > 0f
    val interactiveHighlight = remember(animationScope, isInteractive) {
        InteractiveHighlight(animationScope, intensity = {
            currentOptics.value.highlight * currentOptics.value.interaction
        },highlightColor={ pressColor.value })
    }
    Row(modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val press = if (isInteractive) interactiveHighlight.pressProgress else 0f
            vibrancy()
            if(optics.blur>0f) blur(optics.blur.dp.toPx())
            liquidLens(minOf(size.minDimension * .45f, 12.dp.toPx()),
                24.dp.toPx() * optics.refraction * (1f + .35f * press * optics.interaction),
                depthEffect = true, chromaticAberration = optics.chromatic,dispersionStrength=optics.dispersion)
        },
        highlight = { glassHighlight(optics.highlight,light.value,edgeColor) },
        shadow = null,
        layerBlock = if (isInteractive) {
            { applyLiquidButtonDeformation(interactiveHighlight, currentOptics.value.interaction) }
        } else null,
        onDrawBackdrop = { draw ->
            val press = if (isInteractive) interactiveHighlight.pressProgress else 0f
            val zoom = 1f + (optics.magnification - 1f) * (1f + .35f * press * optics.interaction)
            scale(zoom, zoom, pivot = center) { draw() }
        },
        onDrawSurface = { drawRect(tint.copy(alpha = optics.tint)) },
    ).clickable(interactionSource = null, indication = null, role = role, onClick = onClick)
        .then(if (isInteractive) interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier) else Modifier)
        .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

/** Elastic scale and tanh drag follow the upstream LiquidButton implementation. */
fun GraphicsLayerScope.applyLiquidButtonDeformation(highlight: InteractiveHighlight, strength: Float) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val progress = highlight.pressProgress
    val scale = lerp(1f, 1f + 4.dp.toPx() / height * strength, progress)
    val maxOffset = size.minDimension
    val initialDerivative = .05f
    val offset = highlight.offset
    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset) * strength
    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset) * strength
    val maxDragScale = 4.dp.toPx() / height * strength
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
}
