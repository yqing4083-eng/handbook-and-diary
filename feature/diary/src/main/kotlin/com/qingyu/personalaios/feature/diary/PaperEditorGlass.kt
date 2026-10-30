package com.qingyu.personalaios.feature.diary

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.SpaceTheme

internal val LocalPaperToolGlassEnvironment=staticCompositionLocalOf<GlassEnvironment?> { null }

/** Reference-style pale glass is scoped to this paper editor only. */
internal fun paperEditorEnvironment(environment:GlassEnvironment)=environment.copy(
    theme=environment.theme.copy(glassTint=0xFFE2E2E5),surfaceTintOverride=.32f,layeredTintOverride=.32f,
    groups=environment.groups.copy(buttons=environment.groups.buttons.copy(refraction=1f,blur=8f,magnification=1.05f,highlight=.7f,tint=.08f,chromaticAberration=false)))

@Composable internal fun PaperEditorGlass(environment:GlassEnvironment,enabled:Boolean=true,content:@Composable ()->Unit) {
    val stationaryClock=remember { mutableStateOf(0f) }
    if(enabled) LiquidGlassEnvironment(paperEditorEnvironment(environment).copy(seconds=stationaryClock)) {
        CompositionLocalProvider(LocalGlassEdgeColor provides Color.Black.copy(.48f),LocalGlassInteractionColor provides Color(0xFF8E8E93),LocalPaperEditorChrome provides true,LocalPaperToolGlassEnvironment provides environment) { SpaceTheme(PaperInk,content=content) }
    } else LiquidGlassEnvironment(environment,content=content)
}
