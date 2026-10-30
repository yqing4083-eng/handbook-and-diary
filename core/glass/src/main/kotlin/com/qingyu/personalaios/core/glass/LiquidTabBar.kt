package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import com.qingyu.personalaios.core.ui.LocalSpaceColors
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.qingyu.personalaios.core.ui.AppTypography
import com.qingyu.personalaios.core.ui.SpaceText

@Composable
fun LiquidTabBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,iconLabels:List<String> = labels) {
    require(labels.isNotEmpty() && selectedIndex in labels.indices)
    val environment = checkNotNull(LocalGlassEnvironment.current)
    val optics = environment.navigationOptics
    val foreground=LocalSpaceColors.current.foreground
    val density=LocalDensity.current
    val currentIndex = rememberUpdatedState(selectedIndex)
    val currentSelect = rememberUpdatedState(onSelect)
    val selected = remember { { currentIndex.value } }
    // The caller checks its synchronous navigation destination. Composition may still
    // expose an older selected index when several taps arrive during one transition.
    val select = remember { { index: Int -> currentSelect.value(index) } }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LiquidBottomTabs(selected, select, environment.backdrop.upstream, labels.size,
            modifier = Modifier.widthIn(max = 455.dp).fillMaxWidth(if(labels.size>4) .94f else .875f).selectableGroup(),
            motionEnabled = environment.theme.motionEnabled && optics.interaction > 0f,
            darkBackground = !environment.dayMode,
            glassTint = environment.surfaceTint,
            opticalStrength = optics.refraction,
            blurRadius = optics.blur,
            highlightAlpha = optics.highlight,
            magnification = optics.magnification,
            tintAlpha = optics.tint,
            chromaticAberration = optics.chromatic,
            interactionStrength = optics.interaction,
            dispersionStrength=optics.dispersion,
        ) {
            labels.forEachIndexed { index, label ->
                val active = index == selectedIndex
                LiquidBottomTab(onClick = { select(index) }, modifier = Modifier.semantics { this.selected = active }) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    TabGlyph(iconLabels.getOrElse(index) {label},foreground.copy(alpha=if(active) 1f else .7f))
                    BasicText(label,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=TextStyle(color=foreground.copy(alpha=if(active) 1f else .8f),fontSize=if(labels.size>4) 11.sp else AppTypography.Tab,
                        fontWeight=if(active) FontWeight.Medium else FontWeight.Normal,
                        shadow=Shadow(if(environment.dayMode) Color.White.copy(.35f) else Color.Black.copy(.8f),blurRadius=with(density) { 1.dp.toPx() })))
                    }
                }
            }
        }
    }
}
