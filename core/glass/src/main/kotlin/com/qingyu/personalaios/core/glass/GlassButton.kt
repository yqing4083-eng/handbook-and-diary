package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.qingyu.personalaios.core.ui.AppCorners
import androidx.compose.ui.unit.dp

/** A plain label can launch the same anchored menu without becoming a glass button. */
@Composable fun GlassMenuAnchor(onClick:()->Unit,modifier:Modifier=Modifier,
    launchBounds:((Rect)->Rect)?=null,geometry:GlassGeometry=GlassGeometry.Corner(12.dp),
    enabled:Boolean=true,onLongClick:(()->Unit)?=null,onLongClickLabel:String?=null,content:@Composable BoxScope.()->Unit) {
    val launch=LocalGlassLaunch.current
    val point=LocalGlassLaunchPoint.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val token=remember { Any() }
    fun prepareLaunch() {
        val origin=launchBounds?.invoke(bounds) ?: bounds
        point?.value=origin.center
        launch?.value=GlassLaunch(origin,android.os.SystemClock.elapsedRealtime(),token,geometry,content)
    }
    val gesture=if(onLongClick==null) Modifier.clickable(enabled=enabled,interactionSource=null,indication=null,role=Role.Button) {
        prepareLaunch();onClick()
    } else Modifier.combinedClickable(enabled=enabled,interactionSource=null,indication=null,role=Role.Button,
        onLongClickLabel=onLongClickLabel,onLongClick={prepareLaunch();onLongClick()},onClick={prepareLaunch();onClick()})
    Box(modifier.onGloballyPositioned { bounds=it.boundsInRoot() }.then(gesture),contentAlignment=Alignment.Center,content=content)
}

/** Every actionable glass control delegates to the upstream LiquidButton. */
@Composable
fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    geometry: GlassGeometry = GlassGeometry.Corner(AppCorners.Small),
    role: Role = Role.Button, content: @Composable BoxScope.() -> Unit) {
    val environment = checkNotNull(LocalGlassEnvironment.current)
    if(LocalGlassMorph.current!=null && geometry!=GlassGeometry.Circle) {
        Box(modifier.clickable(interactionSource=null,indication=null,role=role,onClick=onClick),contentAlignment=Alignment.Center,content=content)
        return
    }
    val buttonGeometry=if(geometry==GlassGeometry.Circle) geometry else GlassGeometry.Capsule
    val shape = remember(buttonGeometry) { buttonGeometry.shape() }
    val launch=LocalGlassLaunchPoint.current
    var centre by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val launchBounds=LocalGlassLaunch.current
    val expanded=LocalGlassExpandedAnchor.current
    val token=remember { Any() }
    LiquidButton({ launch?.value=centre;launchBounds?.value=GlassLaunch(bounds,android.os.SystemClock.elapsedRealtime(),token,buttonGeometry,content);onClick() }, environment.backdrop.upstream, environment.surfaceOptics,
        modifier.onGloballyPositioned { bounds=it.boundsInRoot();centre=bounds.center }.graphicsLayer { alpha=if(expanded?.value===token) 0f else 1f },
        shape, environment.surfaceTint, role) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center, content = content)
    }
}

/** Bridges existing accessible slider gestures to the same upstream press animation. */
@Composable
fun rememberGlassInteraction(): InteractiveHighlight {
    val environment = checkNotNull(LocalGlassEnvironment.current)
    val current = rememberUpdatedState(environment.optics)
    val color=rememberUpdatedState(LocalGlassInteractionColor.current)
    val scope = rememberCoroutineScope()
    return remember(scope) { InteractiveHighlight(scope, intensity = {
        current.value.highlight * current.value.interaction
    },highlightColor={ color.value }) }
}
