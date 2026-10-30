package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.ui.*

/** Debug-only entry point owned by the app; grid makes refraction falsifiable. */
@Composable
fun GlassGallery() {
    val parent = checkNotNull(LocalGlassEnvironment.current)
    val backdrop = rememberGlassBackdrop()
    var refracting by remember { mutableStateOf(true) }
    var warm by remember { mutableStateOf(false) }
    val style = if (refracting) GlassStyle.REGULAR else GlassStyle.IDENTITY
    val theme = parent.theme.copy(darkBackground=true,foreground=0xFFF0F5F9L,glassTint=0xFFB4D3ECL)
    LiquidGlassEnvironment(parent.copy(backdrop = backdrop, theme = theme)) {
        SpaceTheme(Color(0xFFF0F5F9)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().captureGlassBackdrop(backdrop).clipToBounds()) {
                    drawRect(if (warm) Color(0xFF5C3F38) else Color(0xFF253E54))
                    val cell = 24.dp.toPx()
                    for (x in 0..(size.width/cell).toInt()) {
                        for (y in 0..(size.height/cell).toInt()) {
                            if ((x+y)%2 == 0) drawRect(Color.White.copy(alpha=.06f),Offset(x*cell,y*cell),
                                androidx.compose.ui.geometry.Size(cell,cell))
                        }
                        drawLine(Color(0xFF8FD5DA).copy(alpha=.42f),Offset(x*cell,0f),Offset(x*cell,size.height),1.dp.toPx())
                    }
                    for (y in 0..(size.height/cell).toInt()) {
                        drawLine(Color(0xFFD9D4B5).copy(alpha=.35f),Offset(0f,y*cell),Offset(size.width,y*cell),1.dp.toPx())
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    SpaceText("GLASS LAB",12.sp,alpha=.75f)
                    Spacer(Modifier.height(12.dp))
                    SpaceText("背景采样 · 连续曲率",24.sp)
                    Spacer(Modifier.height(10.dp))
                    SpaceText("网格来自同一背景图层，文字保持清晰。",12.sp,alpha=.8f)
                    Spacer(Modifier.height(24.dp))
                    GlassSurface(Modifier.fillMaxWidth().height(164.dp).semantics { contentDescription="玻璃采样主区域" },GlassGeometry.Corner(32.dp),style) {
                        Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                            SpaceText("Liquid Glass",24.sp)
                            Spacer(Modifier.height(8.dp))
                            SpaceText("${if (refracting) "折射" else "原始"} · 32 dp 连续角",12.sp,alpha=.8f)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    GlassSurface(Modifier.fillMaxWidth().height(68.dp),GlassGeometry.Capsule,style) {
                        SpaceText("Continuous Capsule",16.sp,Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        GlassSurface(Modifier.size(80.dp),GlassGeometry.Capsule,style) {
                            SpaceText("✦",26.sp,Modifier.align(Alignment.Center))
                        }
                        Spacer(Modifier.width(18.dp))
                        SpaceText("局部放大 / 边缘高光\n细微色散 / 缓存采样",13.sp,alpha=.85f)
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        GalleryControl(if (refracting) "原始采样" else "开启折射",Modifier.weight(1f)) { refracting=!refracting }
                        GalleryControl("切换背景",Modifier.weight(1f)) { warm=!warm }
                    }
                    Spacer(Modifier.height(LocalPageBottomInset.current))
                }
            }
        }
    }
}

@Composable
private fun GalleryControl(label: String,modifier: Modifier,onClick: () -> Unit) {
    GlassButton(onClick, modifier.heightIn(min=48.dp),
        GlassGeometry.Corner(AppCorners.Small)) {
        SpaceText(label,13.sp,Modifier.align(Alignment.Center).padding(12.dp))
    }
}
