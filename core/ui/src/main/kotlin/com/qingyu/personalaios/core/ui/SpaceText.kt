package com.qingyu.personalaios.core.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val LocalControlTextSize=staticCompositionLocalOf<TextUnit?> { null }

@Composable
fun SpaceText(
    text: String,
    size: TextUnit = 16.sp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, maxLines=maxLines, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(
        color = LocalSpaceColors.current.foreground.copy(alpha = alpha),
        fontSize = LocalControlTextSize.current ?: size,
        fontWeight = weight,
    ))
}
