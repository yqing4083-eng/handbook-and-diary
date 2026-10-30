package com.qingyu.personalaios.core.weather

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.qingyu.personalaios.core.theme.ThemeTokens
import com.qingyu.personalaios.domain.ParticleType
import kotlin.math.*

/** One weather canvas behind every route. Cached soft sprites avoid screen-sized blur layers. */
@Composable
fun WeatherScene(theme: ThemeTokens, modifier: Modifier = Modifier, seconds: State<Float> = rememberWeatherClock(theme.motionEnabled)) {
    val duration = if (theme.motionEnabled) 1200 else 0
    val top = animateColorAsState(Color(theme.backgroundTop), tween(duration), label = "weather-top")
    val bottom = animateColorAsState(Color(theme.backgroundBottom), tween(duration), label = "weather-bottom")
    val engine = remember(theme.particles) { ParticleEngine(theme.particles) }
    val sprites = remember { List(3) { createSoftSprite(it) } }
    val visibility = remember { Animatable(1f) }
    LaunchedEffect(theme.particles.type, theme.motionEnabled) {
        visibility.snapTo(if (theme.motionEnabled) 0f else 1f)
        if (theme.motionEnabled) visibility.animateTo(1f, tween(900))
    }
    Box(modifier.fillMaxSize().drawWithCache {
        val background = Brush.verticalGradient(listOf(top.value, bottom.value))
        val glow = Brush.radialGradient(listOf(Color(theme.atmosphere).copy(alpha = theme.glowIntensity * .28f), Color.Transparent),
            center = Offset.Zero, radius = size.width * 1.15f)
        val fog = Brush.radialGradient(listOf(Color(theme.atmosphere).copy(alpha = .10f), Color.Transparent),
            center = Offset.Zero, radius = size.width * .92f)
        val white = if(theme.darkBackground) Color(0xFFE8F4FF) else Color(0xFF6D8BA8)
        val accents=when(engine.config.type) {
            ParticleType.LIGHT -> listOf(Color(0xFFEBAE60),Color(0xFF80CDBE),Color(0xFFA6BEDF))
            ParticleType.SNOW -> listOf(Color(0xFF8BCAEB),Color(0xFFB8A9DF),Color(0xFFA3DDD5))
            ParticleType.RAIN -> listOf(Color(0xFF6CAABF),Color(0xFF8797C3),Color(0xFF8CB9AA))
            ParticleType.MIST -> listOf(Color(0xFF9BADC7),Color(0xFFB9ACBF),Color(0xFF85BBAE))
        }
        val decorationRadius=size.width*.58f
        val decorations=accents.map { tint -> Brush.radialGradient(listOf(tint.copy(alpha=if(theme.darkBackground) .13f else .16f),Color.Transparent),Offset.Zero,decorationRadius) }

        onDrawBehind {
            // Reading animation state here invalidates drawing, not page composition/layout.
            val t = seconds.value
            drawRect(background)
            decorations.forEachIndexed { i,brush ->
                translate(size.width*(.1f+i*.42f)+sin(t*.025f+i)*size.width*.05f,size.height*(.43f+i*.23f)) { drawCircle(brush,decorationRadius,Offset.Zero) }
            }
            drawRect(if(theme.darkBackground) Color.Black.copy(.09f) else Color.White.copy(.12f))
            translate(size.width * .8f, size.height * .10f) { drawCircle(glow, size.width * 1.15f, Offset.Zero) }
            val alpha = visibility.value
            val scale = density
            when (engine.config.type) {
                ParticleType.SNOW -> engine.particles.forEach { p ->
                    val x = engine.x(p, t) * size.width
                    val y = engine.y(p, t) * size.height
                    val r = p.size * scale
                    val opacity = (.18f + p.depth * .12f) * alpha
                    if (p.depth == 2 && engine.config.blurAmount > 0f) {
                        val edge = (r * 5f).roundToInt().coerceAtLeast(1)
                        drawImage(sprites[p.depth], dstOffset = IntOffset((x - edge / 2).roundToInt(), (y - edge / 2).roundToInt()),
                            dstSize = IntSize(edge, edge), alpha = opacity * 1.3f)
                    } else drawCircle(white, r, Offset(x, y), alpha = opacity)
                }
                ParticleType.RAIN -> {
                    engine.particles.forEach { p ->
                        val x = engine.x(p, t) * size.width
                        val y = engine.y(p, t) * size.height
                        val length = (8f + p.depth * 8f) * scale
                        drawLine(white.copy(alpha = (.055f + p.depth * .045f) * alpha),
                            Offset(x, y), Offset(x - length * .10f, y + length),
                            strokeWidth = (.45f + p.depth * .22f) * scale, cap = StrokeCap.Round)
                    }
                    repeat(3) { index ->
                        val phase = ((t * .16f + index * .33f) % 1f)
                        val radius = (8f + phase * 40f) * scale
                        val x = size.width * (.20f + index * .31f)
                        val y = size.height * (.72f + index * .075f)
                        drawOval(white.copy(alpha = (1f - phase) * .075f * alpha),
                            topLeft = Offset(x - radius, y - radius * .23f),
                            size = Size(radius * 2f, radius * .46f), style = Stroke(.5f * scale))
                    }
                }
                ParticleType.MIST -> engine.particles.forEach { p ->
                    translate((p.x + sin(t * .022f + p.phase) * .14f) * size.width, p.y * size.height) {
                        drawCircle(fog, size.width * .92f, Offset.Zero, alpha = alpha)
                    }
                }
                ParticleType.LIGHT -> engine.particles.forEach { p ->
                    val radius = (p.size + .7f) * scale * 2f
                    val x = engine.x(p, t * .2f) * size.width
                    val y = engine.y(p, -t * .2f) * size.height
                    val edge = (radius * 4f).roundToInt().coerceAtLeast(1)
                    drawImage(sprites[1], dstOffset = IntOffset((x - edge / 2).roundToInt(), (y - edge / 2).roundToInt()),
                        dstSize = IntSize(edge, edge), alpha = (.12f + sin(t * .5f + p.phase) * .05f) * alpha)
                    drawCircle(accents[(p.phase.toInt().absoluteValue)%accents.size],radius*.5f,Offset(x,y),alpha=.26f*alpha)
                }
            }
        }
    })
}

private fun createSoftSprite(depth: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(32f, 32f, 30f,
            intArrayOf(0xE8F0F8FF.toInt(), 0x70F0F8FF, 0x00F0F8FF),
            floatArrayOf(0f, if (depth == 2) .25f else .40f, 1f), Shader.TileMode.CLAMP)
    }
    android.graphics.Canvas(bitmap).drawCircle(32f, 32f, 30f, paint)
    return bitmap.asImageBitmap()
}
