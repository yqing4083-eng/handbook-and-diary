package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** One 24-unit outline grid for weather, calendar, notebook and personal space. */
@Composable internal fun TabGlyph(label:String,color:Color) {
    val path=remember(label) { PathParser().parsePathString(when(label) {
        "Today" -> "M3 18H21 M5 14A7 7 0 0 1 19 14 M12 2V4 M3 5L5 7 M21 5L19 7 M1 12H3 M21 12H23"
        "日历" -> "M5 4H19Q21 4 21 6V20Q21 22 19 22H5Q3 22 3 20V6Q3 4 5 4Z M3 9H21 M8 2V6 M16 2V6 M8 13H9 M15 13H16 M8 17H9 M15 17H16"
        "日记" -> "M3 3H8Q12 3 12 7V22Q10 19 7 19H3Z M21 3H16Q12 3 12 7 M21 3V19H17Q14 19 12 22"
        else -> "M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7 M4 22V20Q4 14 12 14Q20 14 20 20V22"
    }).toPath() }
    Canvas(Modifier.size(20.dp)) {
        scale(size.width/24f,size.height/24f,pivot=Offset.Zero) {
            drawPath(path,color,style=Stroke(1.65f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}
