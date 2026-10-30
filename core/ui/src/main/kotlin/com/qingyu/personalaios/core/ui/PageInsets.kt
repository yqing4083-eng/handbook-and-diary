package com.qingyu.personalaios.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Space at the END of scroll content, never a smaller viewport above the floating glass. */
val LocalPageBottomInset = staticCompositionLocalOf { 0.dp }

object AppNavigationInsets {
    val BarHeight = AppSpacing.NavigationHeight
    val OuterGap = 16.dp
    val ContentClearance = BarHeight + OuterGap * 2
}
