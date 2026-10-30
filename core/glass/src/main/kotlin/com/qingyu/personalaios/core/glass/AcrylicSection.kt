package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** One sampled material pass per section, optionally without blur or tint.
 * Child buttons and numerals retain their own independent optics. */
@Composable fun AcrylicSection(modifier:Modifier=Modifier,style:GlassStyle=GlassStyle.REGULAR,
    transparent:Boolean=false,
    content:@Composable BoxScope.()->Unit) {
    val parent=checkNotNull(LocalGlassEnvironment.current)
    val acrylic=parent.copy(groups=parent.groups.copy(buttons=parent.groups.buttons.copy(blur=if(transparent) 0f else 16f)),
        layeredTintOverride=if(transparent) 0f else if(parent.dayMode) .28f else .18f)
    LiquidGlassEnvironment(acrylic) {
        GlassSection(modifier,style=style) {
            val sampled=checkNotNull(LocalGlassEnvironment.current).backdrop
            CompositionLocalProvider(LocalGlassEnvironment provides parent.copy(backdrop=sampled)) { content() }
        }
    }
}

