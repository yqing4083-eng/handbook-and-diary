package com.qingyu.personalaios.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

/** No pointer handler or inset consumption: siblings still receive all insets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PaperSystemBarBackdrop(active:Boolean,immersive:Boolean=false,modifier:Modifier=Modifier) {
    if(!active) return
    val density=LocalDensity.current;val direction=LocalLayoutDirection.current
    // The paper already reaches the bottom edge. Never paint the hidden
    // navigation bar's stable height over wallpaper or editor controls.
    val bars=(if(immersive) WindowInsets.captionBar else WindowInsets.statusBars)
        .union(WindowInsets.displayCutout)
    val top=bars.getTop(density).toFloat();val bottom=bars.getBottom(density).toFloat()
    val left=bars.getLeft(density,direction).toFloat();val right=bars.getRight(density,direction).toFloat()
    Canvas(modifier.fillMaxSize()) {
        if(top>0) drawRect(Color.White,Offset.Zero,Size(size.width,top))
        if(bottom>0) drawRect(Color.White,Offset(0f,size.height-bottom),Size(size.width,bottom))
        if(left>0) drawRect(Color.White,Offset.Zero,Size(left,size.height))
        if(right>0) drawRect(Color.White,Offset(size.width-right,0f),Size(right,size.height))
    }
}
