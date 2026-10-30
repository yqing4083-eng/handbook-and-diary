package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable internal fun PenColorDot(ink:Long,current:Long,diameter:Int,onClick:()->Unit) {
    Canvas(Modifier.size(diameter.dp).semantics { contentDescription="画笔颜色 ${ink.toString(16)}" }
        .clickable(interactionSource=null,indication=null,onClick=onClick)) {
        drawCircle(Color(ink),radius=size.minDimension*.37f)
        if((current and 0xFFFFFFL)==(ink and 0xFFFFFFL)) drawCircle(Color.White,radius=size.minDimension*.47f,style=Stroke(1.dp.toPx()))
    }
}

@Composable internal fun PaperColorField(spectrum:Boolean,onColor:(Long)->Unit) {
    val callback by rememberUpdatedState(onColor)
    fun ink(x:Float,y:Float):Long {
        if(y<.1f) { val gray=(255*(1-x)).toInt();return android.graphics.Color.rgb(gray,gray,gray).toLong() and 0xFFFFFFFFL }
        return android.graphics.Color.HSVToColor(floatArrayOf(x*360f,(1-((y-.1f)/.9f)).coerceIn(.05f,1f),.45f+.55f*((y-.1f)/.9f))).toLong() and 0xFFFFFFFFL
    }
    Canvas(Modifier.fillMaxWidth().height(144.dp).semantics { contentDescription=if(spectrum) "颜色光谱" else "颜色网格" }
        .pointerInput(spectrum) {
            awaitEachGesture {
                val down=awaitFirstDown();down.consume()
                fun pick(p:Offset) {
                    var x=(p.x/size.width).coerceIn(0f,.999f);var y=(p.y/size.height).coerceIn(0f,.999f)
                    if(!spectrum) { x=(kotlin.math.floor(x*12)+.5f)/12;y=(kotlin.math.floor(y*10)+.5f)/10 }
                    callback(ink(x,y))
                }
                pick(down.position)
                do {
                    val event=awaitPointerEvent();val change=event.changes.first { it.id==down.id };pick(change.position);change.consume()
                } while(event.changes.any { it.pressed })
            }
        }.drawWithCache {
            // Color selection changes the cursor, not the grid texture. Upload
            // one cached bitmap instead of thousands of rectangles per move.
            val columns=if(spectrum) 192 else 12;val rows=if(spectrum) 128 else 10
            val bitmap=android.graphics.Bitmap.createBitmap(columns,rows,android.graphics.Bitmap.Config.ARGB_8888)
            val pixels=IntArray(columns*rows) { i -> ink((i%columns+.5f)/columns,(i/columns+.5f)/rows).toInt() }
            bitmap.setPixels(pixels,0,columns,0,0,columns,rows)
            val texture=bitmap.asImageBitmap()
            onDrawBehind {
                drawImage(texture,dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()),
                    filterQuality=if(spectrum) FilterQuality.High else FilterQuality.None)
                drawRect(Color.White.copy(.18f),style=Stroke(1.dp.toPx()))
            }
        }) {
        // The cached field is independent of the changing selected ink.
    }
}
