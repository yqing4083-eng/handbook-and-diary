package com.qingyu.personalaios.feature.diary

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/** A cached texture follows all four real corners, including the shallow V. */
@Composable internal fun PaperLiftSnapshot(image:ImageBitmap,quad:BookQuad,modifier:Modifier) {
    val matrix=remember { Matrix() };val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val density=LocalDensity.current.density
    val outline=remember { android.graphics.Path() }
    val shadow=remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;setShadowLayer(8*density,0f,4*density,0x26000000) } }
    val source=remember(image) { floatArrayOf(0f,0f,image.width.toFloat(),0f,image.width.toFloat(),image.height.toFloat(),0f,image.height.toFloat()) }
    Canvas(modifier) { drawIntoCanvas {
        val target=FloatArray(8) { i->quad.points[i]*density }
        outline.rewind();outline.moveTo(target[0],target[1]);for(i in 1..3) outline.lineTo(target[i*2],target[i*2+1]);outline.close()
        it.nativeCanvas.drawPath(outline,shadow)
        matrix.setPolyToPoly(source,0,target,0,4)
        it.nativeCanvas.drawBitmap(image.asAndroidBitmap(),matrix,paint)
    } }
}
