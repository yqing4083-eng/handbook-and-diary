package com.qingyu.personalaios.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.theme.*

enum class FestivalPlace(val label:String) { DIARY("日记"), SPACE("空间"), PATCH("对话") }

/** UI copy only: never added to a saved entry or sent as an AI instruction. */
fun festivalEntryPrompt(event:FestivalEvent,place:FestivalPlace):String {
    val subject=when(event.motif) {
        FestivalMotif.CHRISTMAS -> "今天收到的小小温暖"
        FestivalMotif.LANTERN -> if("元宵" in event.name) "灯火里的团圆" else "新一年的心愿"
        FestivalMotif.MOON -> "月光下想念的人"
        FestivalMotif.BOAT -> "一缕粽香里的记忆"
        FestivalMotif.HEART -> "想认真说出的喜欢"
        FestivalMotif.FLOWER -> if("重阳" in event.name) "想陪伴的人" else "值得珍藏的温柔"
        FestivalMotif.STAR -> "今天值得记住的瞬间"
        FestivalMotif.BALLOON -> "今天的小小快乐"
        FestivalMotif.BOOK -> "想说的一句谢谢"
        FestivalMotif.PUMPKIN -> "一个奇妙的小故事"
        FestivalMotif.MAPLE -> "值得感谢的人与事"
        FestivalMotif.WILLOW -> "春风里的一段念想"
        FestivalMotif.SNOW -> "冬日里的一点暖意"
        FestivalMotif.RIBBON -> "新的期待和小愿望"
    }
    return when(place) {
        FestivalPlace.DIARY -> "把$subject，留在这一页。"
        FestivalPlace.SPACE -> "分享$subject。"
        FestivalPlace.PATCH -> "聊聊$subject。"
    }
}

private object KeepsakePaths {
    val book=Path().apply {moveTo(50f,30f);cubicTo(36f,21f,20f,21f,8f,25f);lineTo(8f,78f);cubicTo(24f,73f,37f,76f,50f,84f);cubicTo(63f,76f,76f,73f,92f,78f);lineTo(92f,25f);cubicTo(80f,21f,64f,21f,50f,30f);close()}
    val letter=Path().apply {moveTo(8f,26f);quadraticTo(8f,20f,14f,20f);lineTo(86f,20f);quadraticTo(92f,20f,92f,26f);lineTo(92f,78f);quadraticTo(92f,84f,86f,84f);lineTo(14f,84f);quadraticTo(8f,84f,8f,78f);close();moveTo(8f,27f);lineTo(50f,59f);lineTo(92f,27f)}
    val message=Path().apply {moveTo(22f,17f);lineTo(78f,17f);cubicTo(97f,17f,97f,77f,78f,77f);lineTo(43f,77f);quadraticTo(30f,91f,25f,88f);lineTo(25f,76f);cubicTo(3f,76f,3f,17f,22f,17f);close()}
}

/** One non-interactive, cached vector illustration for each empty-state context. */
@Composable fun FestivalKeepsake(place:FestivalPlace,modifier:Modifier=Modifier) {
    val event=LocalFestivalTheme.current?.event ?: return
    val ink=LocalSpaceColors.current.foreground
    Canvas(modifier.size(140.dp,96.dp).semantics {contentDescription="${event.name}${place.label}节日插画"}) {
        val unit=size.height/100f;val origin=Offset((size.width-size.height)/2,0f)
        withTransform({translate(origin.x,origin.y);scale(unit,unit,Offset.Zero)}) {
            val a=Color(event.accent);val b=Color(event.companion)
            drawOval(a.copy(.12f),Offset(2f,34f),Size(96f,51f))
            drawCircle(b.copy(.15f),24f,Offset(76f,26f))
            val path=when(place) {FestivalPlace.DIARY->KeepsakePaths.book;FestivalPlace.SPACE->KeepsakePaths.letter;FestivalPlace.PATCH->KeepsakePaths.message}
            drawPath(path,ink.copy(.045f));drawPath(path,ink.copy(.5f),style=Stroke(.9f,join=StrokeJoin.Round))
            when(place) {
                FestivalPlace.DIARY -> {
                    drawLine(b.copy(.8f),Offset(50f,31f),Offset(50f,83f),1f)
                    repeat(4) {i->drawLine(ink.copy(.17f),Offset(18f,39f+i*8),Offset(41f,43f+i*8),.6f)}
                    motif(event,Offset(59f,37f),26f,.85f)
                }
                FestivalPlace.SPACE -> {
                    drawCircle(b.copy(.6f),13f,Offset(50f,59f));motif(event,Offset(39f,46f),23f,.9f)
                    drawLine(b.copy(.4f),Offset(10f,78f),Offset(36f,57f),.7f)
                    drawLine(b.copy(.4f),Offset(90f,78f),Offset(64f,57f),.7f)
                }
                FestivalPlace.PATCH -> {
                    motif(event,Offset(33f,28f),36f,.92f)
                    repeat(3) {i->drawCircle(b.copy(.6f),1.2f,Offset(43f+i*7,69f))}
                }
            }
            drawLine(a.copy(.5f),Offset(1f,12f),Offset(1f,20f),.8f)
            drawLine(a.copy(.5f),Offset(-3f,16f),Offset(5f,16f),.8f)
            drawCircle(b.copy(.7f),1.5f,Offset(99f,69f));drawCircle(a.copy(.45f),1f,Offset(93f,91f))
        }
    }
}

/** Decorative only: leaves the name field's bounds, cursor and hit target intact. */
@Composable fun FestivalNameAccent(modifier:Modifier=Modifier) {
    val event=LocalFestivalTheme.current?.event ?: return
    Canvas(modifier.clearAndSetSemantics {}) {
        val a=Color(event.accent);val b=Color(event.companion);val u=1.dp.toPx()
        val extent=9*u
        motif(event,Offset(3*u,size.height-11*u),extent,.55f)
        motif(event,Offset(size.width-12*u,2*u),extent,.65f)
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent,b.copy(.48f),a.copy(.35f),Color.Transparent)),
            Offset(size.width*.2f,size.height-1.5f*u),Offset(size.width*.8f,size.height-1.5f*u),.65f*u)
    }
}

@Composable fun FestivalDivider(modifier:Modifier=Modifier,event:FestivalEvent?=LocalFestivalTheme.current?.event) {
    val ink=LocalSpaceColors.current.foreground
    if(event==null) {
        Canvas(modifier.fillMaxWidth().height(.5.dp)) {drawLine(ink.copy(.14f),Offset.Zero,Offset(size.width,0f),size.height)}
        return
    }
    Canvas(modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics {}) {
        val centre=size.width/2;val e=size.height
        val colors=listOf(Color.Transparent,Color(event.companion).copy(.5f))
        drawLine(Brush.horizontalGradient(colors),Offset(size.width*.05f,e/2),Offset(centre-e,e/2),.6.dp.toPx())
        drawLine(Brush.horizontalGradient(colors.reversed()),Offset(centre+e,e/2),Offset(size.width*.95f,e/2),.6.dp.toPx())
        motif(event,Offset(centre-e/2,0f),e,.72f)
    }
}
