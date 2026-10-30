package com.qingyu.personalaios.core.shape

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

private val GalleryBackground = Color(0xFF111F32)
private val GalleryForeground = Color(0xFFE9F0F6)

/** Device-accessible diagnostic gallery; not an additional top-level destination. */
@Composable
fun ShapeGallery(bottomInset: Dp = 0.dp) {
    Column(Modifier.fillMaxSize().background(GalleryBackground)
        .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GalleryText("CONTINUOUS GEOMETRY", 13)
        GalleryText("连续曲率", 30)
        GalleryText("同一轮廓用于裁切、填充与描边。", 14)
        GalleryText("IOSContinuousCornerShape · 36dp", 14)
        ShapeSample(IOSContinuousCornerShape(36.dp), Modifier.fillMaxWidth().height(160.dp), "Surface")
        GalleryText("ContinuousCapsuleShape · 68dp height", 14)
        ShapeSample(ContinuousCapsuleShape(), Modifier.fillMaxWidth().height(68.dp), "Today     日记     Me")
        GalleryText("SquircleShape · 88dp", 14)
        ShapeSample(SquircleShape(), Modifier.size(88.dp), "Q")
        GalleryText("16 / 22 / 28 / 36dp", 14)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(16, 22, 28, 36).forEach { radius ->
                ShapeSample(IOSContinuousCornerShape(radius.dp), Modifier.weight(1f).height(64.dp), "$radius")
            }
        }
        GalleryText("小尺寸与超大角尺寸自动收敛", 14)
        ShapeSample(IOSContinuousCornerShape(120.dp), Modifier.width(160.dp).height(32.dp), "Clamped")
        Spacer(Modifier.height(12.dp + bottomInset))
    }
}

@Composable
private fun ShapeSample(shape: Shape, modifier: Modifier, label: String) {
    Box(modifier.clip(shape).background(GalleryForeground.copy(alpha = .06f))
        .border(1.dp, GalleryForeground.copy(alpha = .6f), shape), contentAlignment = Alignment.Center) {
        GalleryText(label, 13)
    }
}

@Composable
private fun GalleryText(text: String, size: Int) {
    BasicText(text, style = TextStyle(color = GalleryForeground, fontSize = size.sp))
}

@Preview(name = "Continuous shapes", widthDp = 390, heightDp = 844)
@Preview(name = "Large type", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Preview(name = "RTL", widthDp = 390, heightDp = 844, locale = "ar")
@Composable
private fun ContinuousShapesPreview() = ShapeGallery()
