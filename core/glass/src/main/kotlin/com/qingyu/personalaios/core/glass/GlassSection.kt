package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/** Record the lower glass only, so floating controls sample it without a feedback loop. */
@Composable
fun GlassSection(
    modifier:Modifier=Modifier,
    geometry:GlassGeometry=GlassGeometry.Corner(com.qingyu.personalaios.core.ui.AppCorners.Large),
    style:GlassStyle=GlassStyle.REGULAR,
    content:@Composable BoxScope.()->Unit,
) {
    val parent=checkNotNull(LocalGlassEnvironment.current)
    val surface=rememberGlassBackdrop()
    val layered=rememberGlassContentBackdrop(surface)
    Box(modifier) {
        GlassSurface(Modifier.matchParentSize().captureGlassBackdrop(surface),geometry,style,layered=true)
        CompositionLocalProvider(LocalGlassEnvironment provides parent.copy(backdrop=layered),LocalGlassDepth provides LocalGlassDepth.current+1) {
            Box(Modifier.fillMaxWidth(),content=content)
        }
    }
}
