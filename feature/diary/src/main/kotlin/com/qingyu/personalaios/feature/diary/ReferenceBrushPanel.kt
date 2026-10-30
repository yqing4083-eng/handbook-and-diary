package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Pens use discrete stroke presets; erasure uses a neutral continuous size wheel. */
@Composable internal fun ReferenceBrushPanel(tool:InkTool,erasing:Boolean,color:Long,width:Float,eraser:Float,opacity:Float,
    onWidth:(Float)->Unit,onEraser:(Float)->Unit,onOpacity:(Float)->Unit) {
    if(erasing) { ReferenceEraserSize(eraser,onEraser);return }
    val values=PaperBrushPresets.widths
    val value=width
    val selected=values.indices.minBy { abs(values[it]-value) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().semantics {
            contentDescription="笔触宽度"
            progressBarRangeInfo=ProgressBarRangeInfo(value,.001f.. .04f)
            setProgress { onWidth(it.coerceIn(.001f,.04f));true }
        },horizontalArrangement=Arrangement.SpaceBetween) {
            values.forEachIndexed { index,v ->
                Canvas(Modifier.size(34.dp).semantics { contentDescription="笔触 ${index+1}";this.selected=index==selected }
                    .clickable(interactionSource=null,indication=null) { onWidth(v) }) {
                    if(selected==index) drawPath(AppShapes.Small.createOutline(size,layoutDirection,this).let { (it as androidx.compose.ui.graphics.Outline.Generic).path },Color(0xFF171719))
                    val ink=if(selected==index) Color.White else Color(color).copy(alpha=1f)
                    val sample=Path().apply {
                        moveTo(size.width*.38f,size.height*.2f)
                        cubicTo(size.width*.13f,size.height*.73f,size.width*.30f,size.height*.71f,size.width*.50f,size.height*.48f)
                        cubicTo(size.width*.68f,size.height*.30f,size.width*.52f,size.height*.68f,size.width*.83f,size.height*.82f)
                    }
                    drawPath(sample,ink,style=Stroke((1.1f+index*1.15f+if(index==2) .3f else 0f).dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
            }
        }
        ReferenceOpacity(opacity,color,onOpacity)
    }
}

@Composable private fun ReferenceEraserSize(value:Float,onValue:(Float)->Unit) {
    val callback by rememberUpdatedState(onValue)
    val fraction=((value-.008f)/.092f).coerceIn(0f,1f)
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription="橡皮大小";progressBarRangeInfo=ProgressBarRangeInfo(value,.008f.. .1f)
        setProgress { callback(it.coerceIn(.008f,.1f));true }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown();down.consume()
            fun update(x:Float) { callback(.008f+.092f*((x-16.dp.toPx())/(size.width-32.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f,1f)) }
            update(down.position.x)
            do { val event=awaitPointerEvent();val p=event.changes.first { it.id==down.id };update(p.position.x);p.consume() } while(event.changes.any { it.pressed })
        }
    }) {
        val margin=16.dp.toPx();val y=size.height/2;val length=size.width-2*margin
        drawLine(Color(0xFF9A9A9E),Offset(margin,y),Offset(size.width-margin,y),3.dp.toPx(),StrokeCap.Round)
        for(i in 0..20) {
            val x=margin+length*i/20;val half=(if(i%5==0) 8 else 4).dp.toPx()
            drawLine(Color(0xFF4A4A4E).copy(alpha=.6f),Offset(x,y-half),Offset(x,y+half),1.dp.toPx())
        }
        val centre=Offset(margin+length*fraction,y)
        drawCircle(Color.Black.copy(alpha=.12f),13.dp.toPx(),centre+Offset(0f,1.dp.toPx()))
        drawCircle(Color.White,12.dp.toPx(),centre)
        drawCircle(Color(0xFF444448),12.dp.toPx(),centre,style=Stroke(1.dp.toPx()))
        drawCircle(Color(0xFF444448),((3f+fraction*5).dp.toPx()),centre,style=Stroke(1.dp.toPx()))
    }
}

@Composable private fun ReferenceOpacity(value:Float,color:Long,onValue:(Float)->Unit) {
    val callback by rememberUpdatedState(onValue)
    Canvas(Modifier.fillMaxWidth().height(30.dp).semantics { contentDescription="不透明度";progressBarRangeInfo=ProgressBarRangeInfo(value,.05f..1f)
        setProgress { callback(it.coerceIn(.05f,1f));true }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown();down.consume()
            fun update(x:Float) { callback(((x-15.dp.toPx())/(size.width-30.dp.toPx())).coerceIn(.05f,1f)) }
            update(down.position.x)
            do { val event=awaitPointerEvent();val p=event.changes.first { it.id==down.id };update(p.position.x);p.consume() } while(event.changes.any { it.pressed })
        }
    }) {
        val radius=size.height/2;val clip=Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero,size),radius,radius)) }
        drawContext.canvas.save();drawContext.canvas.clipPath(clip)
        val unit=size.height/3
        for(y in 0..2) for(x in 0..ceil(size.width/unit).toInt()) drawRect(if((x+y)%2==0) Color.White else Color(0xFFD1D1D3),Offset(x*unit,y*unit),Size(unit,unit))
        drawRect(Brush.horizontalGradient(listOf(Color(color).copy(alpha=0f),Color(color).copy(alpha=1f))))
        drawContext.canvas.restore()
        val centre=Offset(radius+(size.width-2*radius)*value,radius)
        drawCircle(Color(color).copy(alpha=value),radius-3.dp.toPx(),centre)
        drawCircle(Color.White,radius-2.dp.toPx(),centre,style=Stroke(2.dp.toPx()))
        drawCircle(Color.Black.copy(.15f),radius-1.dp.toPx(),centre,style=Stroke(.5.dp.toPx()))
    }
}
