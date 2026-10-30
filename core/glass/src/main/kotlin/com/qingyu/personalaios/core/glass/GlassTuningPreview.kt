package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.ui.SpaceText
import com.qingyu.personalaios.core.ui.SpaceTheme
import com.qingyu.personalaios.domain.GlassTarget

/** A local patterned backdrop makes optical changes visible even in a quiet weather scene. */
@Composable
fun GlassTuningPreview(modifier: Modifier = Modifier,target:GlassTarget=GlassTarget.BUTTONS) {
    val parent = checkNotNull(LocalGlassEnvironment.current)
    val backdrop = rememberGlassBackdrop()
    val shape = GlassGeometry.Corner(28.dp).shape()
    val theme = parent.theme.copy(darkBackground = true, foreground = 0xFFF1F7FFL,
        glassTint = 0xFFB9DDF2L)
    LiquidGlassEnvironment(parent.copy(backdrop = backdrop, theme = theme)) {
        Box(modifier.height(182.dp).clip(shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                drawRect(Color(0xFF25475F))
                val spacing = 24.dp.toPx()
                for (i in -1..(size.width / spacing).toInt() + 1) {
                    val x = i * spacing
                    drawLine(Color(0xFF9CD4E4), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                for (i in 0..(size.height / spacing).toInt()) {
                    val y = i * spacing
                    drawLine(Color(0xFF9CD4E4), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                drawCircle(Color(0xFFF4D9A9), 38.dp.toPx(), Offset(size.width * .73f, size.height * .4f))
                drawCircle(Color(0xFF6DABC0), 28.dp.toPx(), Offset(size.width * .24f, size.height * .75f))
            }
            when(target) {
            GlassTarget.BOTTOM_BAR -> {
                var selected by remember { mutableIntStateOf(0) }
                SpaceTheme(Color.White) { LiquidTabBar(listOf("Today","日历","日记","Me"),selected,{ selected=it }) }
            }
            GlassTarget.NUMBERS -> GlassNumeral("04",86.sp)
            GlassTarget.BUTTONS -> GlassButton({}, Modifier.width(228.dp).height(120.dp)
                .semantics { contentDescription = "液态材质预览按钮" }, GlassGeometry.Corner(32.dp)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpaceTheme(Color.White) {
                    SpaceText("Liquid Glass", 22.sp)
                    Spacer(Modifier.height(8.dp))
                    SpaceText("轻触或按住，感受液态", 12.sp, alpha = .8f)
                    }
                }
            }
            }
        }
    }
}
