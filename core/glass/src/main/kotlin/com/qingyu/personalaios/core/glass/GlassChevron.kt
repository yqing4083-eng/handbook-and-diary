package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.ui.*

@Composable fun GlassChevron(forward:Boolean=false) {
    val color=LocalSpaceColors.current.foreground
    Canvas(Modifier.size(AppControls.Icon)) {
        val sign=if(forward) -1f else 1f
        val dx=size.width*.16f;val dy=size.height*.28f
        val a=center+Offset(sign*dx,-dy);val b=center+Offset(-sign*dx,0f);val c=center+Offset(sign*dx,dy)
        drawLine(color,a,b,1.6.dp.toPx(),StrokeCap.Round);drawLine(color,b,c,1.6.dp.toPx(),StrokeCap.Round)
    }
}
