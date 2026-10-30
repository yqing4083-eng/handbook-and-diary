/*
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Source: Kyant0/AndroidLiquidGlass, tag 1.0.6, catalog.
 * Modified: shared intensity, public gesture bridge and minimum feedback for quick taps.
 */
package com.kyant.backdrop.catalog.utils

import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.android.awaitFrame

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    val intensity: () -> Float = { 1f },
    val highlightColor: () -> Color = { Color.White },
) {

    private val pressProgressAnimationSpec =
        spring(0.72f, 520f, 0.001f)
    private val positionAnimationSpec =
        spring(0.72f, 520f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    private var pressStarted = 0L
    private var releaseJob: Job? = null
    private var pressJob: Job? = null

    fun press(at: Offset) {
        releaseJob?.cancel()
        pressStarted = SystemClock.uptimeMillis()
        startPosition = at
        pressJob?.cancel()
        pressJob=animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { positionAnimation.snapTo(at) }
        }
    }

    fun move(at: Offset) { animationScope.launch { positionAnimation.snapTo(at) } }

    fun release() {
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            delay((95L - (SystemClock.uptimeMillis() - pressStarted)).coerceAtLeast(0L))
            // Complete a visible press before rebounding, including a tap shorter than
            // the first rendered frame. A later press cancels this release job.
            if (pressProgressAnimation.value < .75f) {
                snapshotFlow { pressProgressAnimation.value }.filter { it >= .75f }.first()
            }
            awaitFrame()
            pressJob?.cancel()
            launch { pressProgressAnimation.animateTo(0f, spring(0.58f,420f,.001f)) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = (pressProgressAnimation.value * intensity()).coerceIn(0f, 1.5f)
            val color=highlightColor()
            val blend=if(color==Color.White) BlendMode.Plus else BlendMode.SrcOver
            if (progress > 0f) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    drawRect(
                        color.copy(0.08f * progress),
                        blendMode = blend
                    )
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", color.copy(0.15f * progress).toArgb())
                        setFloatUniform("radius", size.minDimension * 1.5f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader),
                        blendMode = blend
                    )
                } else {
                    drawRect(
                        color.copy(0.25f * progress),
                        blendMode = blend
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    press(down.position)
                },
                onDragEnd = {
                    release()
                },
                onDragCancel = {
                    release()
                }
            ) { change, _ ->
                move(change.position)
            }
        }
}
