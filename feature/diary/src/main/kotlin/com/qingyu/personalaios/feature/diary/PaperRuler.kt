package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Physical coordinates stay on this paper, independent of dock scrolling. */
@Stable internal class PaperRulerState {
    var center by mutableStateOf(Offset.Unspecified)
    var angle by mutableFloatStateOf(0f)
    var halfWidth by mutableFloatStateOf(0f)
    private var paperSize=Size.Zero
    fun resize(width:Float,height:Float,half:Float) {
        val next=Size(width,height)
        if(next!=paperSize) {
            center=if(!center.isSpecified || paperSize.width<=0f || paperSize.height<=0f) Offset(width*.5f,height*.45f)
                else Offset(center.x*width/paperSize.width,center.y*height/paperSize.height)
            paperSize=next
        }
        halfWidth=half
    }
    fun local(point:Offset):Offset {
        val delta=point-center;val a=-angle*PI.toFloat()/180
        return Offset(delta.x*cos(a)-delta.y*sin(a),delta.x*sin(a)+delta.y*cos(a))
    }
    fun edge(point:Offset,tolerance:Float):Float? {
        if(!center.isSpecified) return null
        val y=local(point).y
        return if(abs(abs(y)-halfWidth)<=tolerance) if(y<0) -halfWidth else halfWidth else null
    }
    fun project(point:Offset,edge:Float):Offset {
        val x=local(point).x;val a=angle*PI.toFloat()/180
        return center+Offset(x*cos(a)-edge*sin(a),x*sin(a)+edge*cos(a))
    }
}

internal fun Modifier.paperRulerGestures(state:PaperRulerState,enabled:Boolean,ignore:()->Rect?={ null })=pointerInput(state,enabled) {
        if(enabled) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                // Edge strokes pass through to the paper. Only the ruler body
                // captures movement, so drawing beside it remains possible.
                if(ignore()?.contains(down.position)==true || !state.center.isSpecified || abs(state.local(down.position).y)>state.halfWidth-8.dp.toPx()) return@awaitEachGesture
                down.consume()
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    state.center=Offset((state.center.x+event.calculatePan().x).coerceIn(0f,size.width.toFloat()),
                        (state.center.y+event.calculatePan().y).coerceIn(0f,size.height.toFloat()))
                    if(event.changes.count { it.pressed }>=2) state.angle=(state.angle+event.calculateRotation())%360f
                    event.changes.forEach { it.consume() }
                } while(event.changes.any { it.pressed })
            }
        }
}

internal fun Modifier.paperRulerDrawing(state:PaperRulerState,enabled:Boolean,halfWidth:Float)=onSizeChanged {
        state.resize(it.width.toFloat(),it.height.toFloat(),halfWidth)
    }.drawWithContent {
        drawContent()
        if(enabled) drawPaperRuler(state)
}

private fun DrawScope.drawPaperRuler(state:PaperRulerState) {
        if(!state.center.isSpecified) return
        val length=hypot(size.width,size.height)*2.2f;val half=state.halfWidth
        withTransform({ translate(state.center.x,state.center.y);rotate(state.angle,Offset.Zero) }) {
            drawRect(Color(0xFFB9C0C5).copy(alpha=.44f),Offset(-length/2,-half),Size(length,half*2))
            drawLine(Color.Black.copy(.45f),Offset(-length/2,-half),Offset(length/2,-half),.7.dp.toPx())
            drawLine(Color.Black.copy(.45f),Offset(-length/2,half),Offset(length/2,half),.7.dp.toPx())
            val step=8.dp.toPx();val count=(length/step/2).toInt()
            for(i in -count..count) {
                val tick=if(i%5==0) 13.dp.toPx() else 7.dp.toPx()
                drawLine(Color.Black.copy(.65f),Offset(i*step,-half),Offset(i*step,-half+tick),.7.dp.toPx())
                drawLine(Color.Black.copy(.65f),Offset(i*step,half),Offset(i*step,half-tick),.7.dp.toPx())
            }
        }
        drawCircle(Color.White.copy(.9f),16.dp.toPx(),state.center)
        drawCircle(Color.Black.copy(.18f),16.dp.toPx(),state.center,style=Stroke(.7.dp.toPx()))
        drawContext.canvas.nativeCanvas.drawText("${state.angle.roundToInt()}°",state.center.x,state.center.y+4.dp.toPx(),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.DKGRAY;textAlign=android.graphics.Paint.Align.CENTER;textSize=11.dp.toPx() })
}

internal object PaperInkPolicy {
    fun canDrawOnPaper(tool:com.qingyu.personalaios.domain.InkTool,erasing:Boolean,cutting:Boolean)=
        tool!=com.qingyu.personalaios.domain.InkTool.GLYPH_ERASER
}
