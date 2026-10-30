/*
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Source: Kyant0/AndroidLiquidGlass, tag 1.0.6, catalog.
 * Modified by PersonalAIOS: weather tint, shared optical/interaction settings,
 * reduced motion and velocity updates. Original Capsule and 64/56dp sizes restored.
 * Stable parent gesture plane; moving lens is visual-only, navigation feedback removed.
 */
package com.kyant.backdrop.catalog.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.qingyu.personalaios.core.glass.LocalGlassLight
import com.qingyu.personalaios.core.glass.glassHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.qingyu.personalaios.core.glass.liquidLens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    darkBackground: Boolean = true,
    glassTint: Color = Color.White,
    opticalStrength: Float = 1f,
    blurRadius: Float = 4f,
    highlightAlpha: Float = .85f,
    magnification: Float = 1.12f,
    tintAlpha: Float = .07f,
    chromaticAberration: Boolean = true,
    interactionStrength: Float = 1f,
    dispersionStrength:Float=1f,
    content: @Composable RowScope.() -> Unit
) {
    val light = LocalGlassLight.current
    val isLightTheme = !darkBackground
    val accentColor =
        if (isLightTheme) Color.Black
        else Color.White
    val containerColor =
        if (isLightTheme) glassTint.copy(tintAlpha)
        else glassTint.copy(tintAlpha)

    val tabsBackdrop = rememberLayerBackdrop()
    val currentInteraction = rememberUpdatedState(interactionStrength)
    val currentHighlight = rememberUpdatedState(highlightAlpha)

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val animationScope = rememberCoroutineScope()
        val currentSelection = rememberUpdatedState(onTabSelected)
        val dampedDragAnimation = remember(animationScope, motionEnabled, tabWidth, isLtr, touchSlop) {
            var dragTravel = 0f
            DampedDragAnimation(
                animationScope = animationScope,
                motionEnabled = motionEnabled,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                interactionStrength = { currentInteraction.value },
                onDragStarted = { dragTravel = 0f },
                onDragStopped = {
                    // A tap belongs to the stable tab underneath. Do not overwrite its
                    // route with an old animated-lens target when the pointer is released.
                    val targetIndex = if (abs(dragTravel) >= touchSlop)
                        targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    else selectedTabIndex()
                    if (abs(dragTravel) >= touchSlop) currentSelection.value(targetIndex)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            if (motionEnabled) spring(1f, 300f, 0.5f) else androidx.compose.animation.core.snap()
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount == Offset.Zero) return@DampedDragAnimation
                    dragTravel += dragAmount.x
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex, dampedDragAnimation) {
            snapshotFlow { selectedTabIndex() }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        val interactiveHighlight = remember(animationScope, motionEnabled) {
            InteractiveHighlight(
                animationScope = animationScope,
                intensity = { currentHighlight.value * currentInteraction.value * .28f },
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // The liquid lens can stretch across adjacent tabs. Its hit box must never move
        // over their click targets; observe drags on this stable parent instead.
        Box(Modifier.fillMaxWidth().height(56f.dp)
            .then(if (motionEnabled) interactiveHighlight.gestureModifier else Modifier)
            .then(dampedDragAnimation.modifier), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        if(blurRadius>0f) blur(blurRadius.dp.toPx())
                        liquidLens(24f.dp.toPx(), 24f.dp.toPx() * opticalStrength,
                            chromaticAberration = chromaticAberration,dispersionStrength=dispersionStrength)
                    },
                    highlight = { glassHighlight(highlightAlpha,light.value) },
                    onDrawBackdrop = { draw -> scale(magnification, magnification, pivot = center) { draw() } },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width * currentInteraction.value, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(if (motionEnabled) interactiveHighlight.modifier else Modifier)
                .height(56f.dp)
                .fillMaxWidth()
                .padding(3.5f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1f + .2f * currentInteraction.value, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            if(blurRadius>0f) blur(blurRadius.dp.toPx())
                            liquidLens(
                                24f.dp.toPx() * progress * opticalStrength,
                                24f.dp.toPx() * progress * opticalStrength,
                                chromaticAberration = chromaticAberration,dispersionStrength=dispersionStrength
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            glassHighlight(highlightAlpha * (.45f + .10f * progress),light.value)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(if (motionEnabled) interactiveHighlight.modifier else Modifier)
                    .height(49f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        liquidLens(
                            10f.dp.toPx() * (0.35f + 0.65f * progress),
                            6f.dp.toPx() * (0.35f + 0.65f * progress) * opticalStrength,
                            chromaticAberration = chromaticAberration && opticalStrength > 0f,dispersionStrength=dispersionStrength
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        glassHighlight(highlightAlpha * (.45f + .10f * progress),light.value)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress * currentInteraction.value.coerceAtMost(1f))
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = .28f * progress * currentInteraction.value.coerceAtMost(1f)
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f * currentInteraction.value
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            // The lens already samples the light bar plus black labels.
                            // A second white layer would wash out the selected content.
                            if (isLightTheme) Color.Transparent
                            else glassTint.copy(tintAlpha),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = .12f+.04f*progress))
                    }
                )
                .height(49f.dp)
                .fillMaxWidth(1f / tabsCount)
        )
        }
    }
}
