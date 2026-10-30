package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.AppShapes
import com.kyant.backdrop.catalog.components.applyLiquidButtonDeformation

internal val LocalPaperEditorChrome=staticCompositionLocalOf { false }
private val EditorBlue=Color(0xFF0095FF)

/** The editor's icon controls share a pale rim and a small lifted shadow. The
 * confirmation lens is opaque blue, so paper imagery cannot wash out its tick. */
@Composable internal fun PaperChromeIcon(label:String,icon:Int,modifier:Modifier,size:Dp,onClick:()->Unit) {
    val blue=icon==R.drawable.paper_check && LocalPaperEditorSession.current.changed
    val environment=checkNotNull(LocalGlassEnvironment.current)
    val material=environment.copy(theme=if(blue) environment.theme.copy(glassTint=0xFF0095FF) else environment.theme,
        surfaceTintOverride=if(blue) 1f else environment.surfaceTintOverride,
        groups=environment.groups.copy(buttons=environment.groups.buttons.copy(interaction=0f)))
    val bluePress=rememberGlassInteraction()
    LiquidGlassEnvironment(material) {
        // Deform the whole blue body, including surface tint, icon and black rim.
        // A second white press overlay would bleach the confirmation to cyan.
        Box(modifier.size(size).semantics(mergeDescendants=true) {
            contentDescription=label
            if(icon==R.drawable.paper_check) stateDescription=if(blue) "已修改" else "未修改"
        }.graphicsLayer {
            applyLiquidButtonDeformation(bluePress,1f)
        }.then(bluePress.gestureModifier)) {
        GlassButton(onClick,Modifier.fillMaxSize()
            .shadow(7.dp,GlassGeometry.Circle.shape(),ambientColor=Color.Black.copy(.10f),spotColor=Color.Black.copy(.18f))
            .border(.85.dp,Color.White,GlassGeometry.Circle.shape()),geometry=GlassGeometry.Circle) {
            // Content is painted after sampling, with one outer deformation.
            // Keep it in the launch snapshot so menu morphs retain the icon.
            Box(Modifier.size(size).background((if(blue) EditorBlue else Color(0xFF77777C)).copy(alpha=.12f*bluePress.pressProgress),GlassGeometry.Circle.shape()))
            Image(painterResource(icon),null,Modifier.align(Alignment.Center).size(size*.5f),
                colorFilter=ColorFilter.tint(if(blue) Color.White else Color(0xFF1C1C1E)))
        }
        }
    }
}

/** A single liquid body, with two independent hit targets and no divider. */
@Composable internal fun PaperUndoRedo(onUndo:()->Unit,onRedo:()->Unit,height:Dp=44.dp) {
    val interaction=rememberGlassInteraction()
    Box(Modifier.size(height*2,height).graphicsLayer { applyLiquidButtonDeformation(interaction,1f) }.then(interaction.gestureModifier)) {
    GlassSurface(Modifier.fillMaxSize()
        .shadow(7.dp,GlassGeometry.Capsule.shape(),ambientColor=Color.Black.copy(.10f),spotColor=Color.Black.copy(.18f))
        .border(.85.dp,Color.White,GlassGeometry.Capsule.shape()).semantics { contentDescription="撤销与重做" },
        geometry=GlassGeometry.Capsule) {}
        Box(Modifier.fillMaxSize().background(Color(0xFF77777C).copy(alpha=.12f*interaction.pressProgress),GlassGeometry.Capsule.shape()))
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            listOf(Triple("撤销",R.drawable.paper_corner_up_left,onUndo),Triple("重做",R.drawable.paper_corner_up_right,onRedo)).forEach { (label,icon,action) ->
                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription=label }
                    .clickable(interactionSource=null,indication=null,onClick=action),contentAlignment=Alignment.Center) {
                    Image(painterResource(icon),null,Modifier.size(height*.52f),colorFilter=ColorFilter.tint(Color(0xFF1C1C1E)))
                }
            }
        }
    }
}

/** Pale capsule and broad, low-opacity elevation like the supplied Markup
 * tray. Clipping applies to the 0.19.42 barrel tails, not to the paper canvas. */
@Composable internal fun PaperPenDock(modifier:Modifier,content:@Composable BoxScope.()->Unit) {
    Box(modifier.shadow(14.dp,AppShapes.Capsule,ambientColor=Color.Black.copy(.11f),spotColor=Color.Black.copy(.18f))
        .background(Brush.verticalGradient(listOf(Color(0xFFFCFCFD).copy(.97f),Color(0xFFF4F4F6).copy(.94f))),AppShapes.Capsule)
        .border(1.dp,Color.White.copy(.94f),AppShapes.Capsule).clip(AppShapes.Capsule)
        .semantics { contentDescription="笔套" }
        .clickable(interactionSource=null,indication=null) {},content=content)
}
