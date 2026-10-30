package com.qingyu.personalaios.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.theme.*
import kotlin.math.*

val LocalFestivalTheme=compositionLocalOf<FestivalTheme?> { null }

/** Small vector paths are built once. Animation changes only the GPU transform. */
internal object FestivalPaths {
    val heart=Path().apply { moveTo(50f,85f);cubicTo(5f,58f,4f,20f,29f,20f);cubicTo(40f,20f,47f,27f,50f,34f);cubicTo(60f,10f,90f,17f,91f,38f);cubicTo(91f,58f,69f,74f,50f,85f);close() }
    val hat=Path().apply { moveTo(12f,74f);cubicTo(20f,45f,28f,9f,58f,12f);cubicTo(77f,14f,83f,24f,87f,39f);cubicTo(72f,33f,67f,41f,68f,49f);lineTo(78f,74f);close() }
    val tree=Path().apply { moveTo(50f,10f);lineTo(27f,39f);lineTo(37f,39f);lineTo(18f,63f);lineTo(33f,63f);lineTo(11f,85f);lineTo(89f,85f);lineTo(67f,63f);lineTo(82f,63f);lineTo(63f,39f);lineTo(73f,39f);close() }
    val boat=Path().apply { moveTo(9f,62f);quadraticTo(50f,74f,91f,62f);lineTo(76f,85f);lineTo(25f,85f);close() }
    val sail=Path().apply { moveTo(50f,14f);lineTo(50f,59f);lineTo(19f,54f);close() }
    val book=Path().apply { moveTo(50f,29f);quadraticTo(29f,17f,10f,22f);lineTo(10f,76f);quadraticTo(30f,72f,50f,84f);quadraticTo(70f,72f,90f,76f);lineTo(90f,22f);quadraticTo(71f,17f,50f,29f);close() }
    val star=Path().apply { repeat(10) { i -> val r=if(i%2==0) 39f else 18f;val a=(i*PI/5-PI/2).toFloat();val x=50f+cos(a)*r;val y=50f+sin(a)*r;if(i==0)moveTo(x,y)else lineTo(x,y) };close() }
    val leaf=Path().apply { moveTo(50f,7f);lineTo(60f,30f);lineTo(77f,20f);lineTo(72f,44f);lineTo(94f,43f);lineTo(80f,65f);lineTo(84f,77f);lineTo(53f,72f);lineTo(50f,94f);lineTo(47f,72f);lineTo(16f,77f);lineTo(20f,65f);lineTo(6f,43f);lineTo(28f,44f);lineTo(23f,20f);lineTo(40f,30f);close() }
    val branch=Path().apply { moveTo(15f,8f);cubicTo(58f,20f,76f,53f,82f,94f) }
    val ribbon=Path().apply { moveTo(13f,18f);cubicTo(85f,5f,88f,48f,37f,50f);cubicTo(3f,52f,14f,91f,83f,82f) }
    val flower=(0..5).map { val a=it*PI/3;Offset(50f+cos(a).toFloat()*22f,50f+sin(a).toFloat()*22f) }
}

internal fun DrawScope.motif(event:FestivalEvent,origin:Offset=Offset.Zero,extent:Float=size.minDimension,alpha:Float=1f) {
    val a=Color(event.accent).copy(alpha=alpha);val b=Color(event.companion).copy(alpha=alpha)
    withTransform({ translate(origin.x,origin.y);scale(extent/100f,extent/100f,Offset.Zero) }) {
        when(event.motif) {
            FestivalMotif.CHRISTMAS -> {
                drawPath(FestivalPaths.tree,b);drawRect(a,Offset(44f,83f),Size(12f,13f))
                drawCircle(a,5f,Offset(43f,48f));drawCircle(a,4f,Offset(65f,66f));drawCircle(Color(0xFFFFD283).copy(alpha=alpha),5f,Offset(50f,10f))
            }
            FestivalMotif.LANTERN -> {
                drawLine(b,Offset(50f,0f),Offset(50f,18f),3f)
                drawOval(a,Offset(18f,18f),Size(64f,61f))
                drawOval(b.copy(alpha=alpha*.55f),Offset(34f,18f),Size(32f,61f),style=Stroke(2f))
                drawLine(b,Offset(32f,19f),Offset(68f,19f),5f);drawLine(b,Offset(32f,77f),Offset(68f,77f),5f)
                repeat(3) { drawLine(b,Offset(43f+7f*it,80f),Offset(43f+7f*it,97f),2f) }
            }
            FestivalMotif.MOON -> {
                drawCircle(a,34f,Offset(50f,48f));drawCircle(b.copy(alpha=alpha*.5f),7f,Offset(36f,38f))
                drawCircle(b.copy(alpha=alpha*.4f),5f,Offset(60f,59f));drawLine(b,Offset(13f,91f),Offset(87f,91f),2f)
            }
            FestivalMotif.BOAT -> { drawPath(FestivalPaths.boat,a);drawPath(FestivalPaths.sail,b);drawLine(b,Offset(50f,10f),Offset(50f,65f),3f);drawLine(a,Offset(9f,94f),Offset(91f,94f),2f) }
            FestivalMotif.HEART -> drawPath(FestivalPaths.heart,a)
            FestivalMotif.STAR -> { drawPath(FestivalPaths.star,a);drawCircle(b,4f,Offset(88f,15f));drawCircle(b,3f,Offset(12f,80f)) }
            FestivalMotif.BALLOON -> {
                repeat(3) { i -> val x=23f+27f*i;val y=if(i==1) 4f else 19f
                    drawOval(if(i==1) a else b,Offset(x-14f,y),Size(28f,38f));drawLine(b.copy(alpha=alpha*.7f),Offset(x,y+38f),Offset(50f,95f),1.5f) }
            }
            FestivalMotif.FLOWER -> { FestivalPaths.flower.forEach { drawCircle(a,17f,it) };drawCircle(b,13f,Offset(50f,50f));drawLine(b,Offset(50f,75f),Offset(50f,99f),3f) }
            FestivalMotif.BOOK -> { drawPath(FestivalPaths.book,a);drawLine(b,Offset(50f,29f),Offset(50f,82f),2f);repeat(3) { drawLine(b,Offset(20f,40f+10f*it),Offset(40f,45f+10f*it),2f) } }
            FestivalMotif.PUMPKIN -> {
                drawOval(a,Offset(13f,26f),Size(74f,60f));drawOval(b.copy(alpha=alpha*.5f),Offset(32f,26f),Size(36f,60f),style=Stroke(2f));drawLine(b,Offset(50f,26f),Offset(56f,12f),7f)
                drawLine(b,Offset(29f,48f),Offset(38f,48f),4f);drawLine(b,Offset(62f,48f),Offset(71f,48f),4f);drawArc(b,0f,180f,false,Offset(34f,54f),Size(32f,18f),style=Stroke(3f))
            }
            FestivalMotif.MAPLE -> { drawPath(FestivalPaths.leaf,a);drawLine(b,Offset(50f,45f),Offset(50f,96f),2f) }
            FestivalMotif.WILLOW -> { drawPath(FestivalPaths.branch,b,style=Stroke(2.5f));repeat(5) { i -> drawOval(a,Offset(20f+i*11f,17f+i*13f),Size(25f,9f)) } }
            FestivalMotif.SNOW -> repeat(6) { i -> rotate(i*60f,Offset(50f,50f)) { drawLine(a,Offset(50f,12f),Offset(50f,88f),2.5f);drawLine(b,Offset(50f,24f),Offset(40f,34f),2f) } }
            FestivalMotif.RIBBON -> { drawPath(FestivalPaths.ribbon,a,style=Stroke(5f,cap=StrokeCap.Round));drawCircle(b,6f,Offset(17f,80f));drawCircle(b,4f,Offset(85f,17f)) }
        }
    }
}

@Composable fun FestivalAtmosphere(theme:FestivalTheme?,seconds:State<Float>,dayMode:Boolean,modifier:Modifier=Modifier) {
    FestivalSceneBackdrop(theme,seconds,dayMode,modifier)
}

@Composable fun FestivalEmblem(event:FestivalEvent,modifier:Modifier=Modifier) {
    Canvas(modifier.semantics { contentDescription="${event.name}装饰" }) {
        drawCircle(Brush.radialGradient(listOf(Color(event.companion).copy(.16f),Color.Transparent)))
        motif(event,extent=size.minDimension)
    }
}

/** A small editorial signature, rather than another container on the Today page. */
@Composable fun FestivalSignature(modifier:Modifier=Modifier) {
    val event=LocalFestivalTheme.current?.event ?: return
    SpaceText("${event.name} · ${festivalGreeting(event)}",11.sp,modifier,alpha=.72f)
}

/** A quiet footer mark inside an existing glass surface; never covers its text. */
@Composable fun FestivalSurfaceOrnament(modifier:Modifier=Modifier) {
    val event=LocalFestivalTheme.current?.event ?: return
    Canvas(modifier.clearAndSetSemantics {}) {
        val extent=28.dp.toPx()
        motif(event,Offset(size.width-extent-6.dp.toPx(),size.height-extent-3.dp.toPx()),extent,.14f)
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent,Color(event.accent).copy(.24f),Color.Transparent)),
            Offset(size.width*.15f,size.height-1.dp.toPx()),Offset(size.width*.85f,size.height-1.dp.toPx()),1.dp.toPx())
        repeat(4) { i->
            val p=Offset((10f+i*5)*1.dp.toPx(),size.height-(8f+i%2*3)*1.dp.toPx())
            drawCircle(Color(event.companion).copy(.22f),if(i==1) 1.5.dp.toPx() else .8.dp.toPx(),p)
        }
    }
}

/** Kept on the same line as the page count, so shelf/book geometry never moves. */
@Composable fun FestivalJournalCaption(pageCount:String,modifier:Modifier=Modifier) {
    val event=LocalFestivalTheme.current?.event
    Row(modifier,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(event!=null) FestivalEmblem(event,Modifier.size(14.dp))
        SpaceText(pageCount,12.sp,alpha=.5f)
        if(event!=null) SpaceText("· ${event.name}",10.sp,alpha=.5f)
    }
}

internal fun festivalGreeting(event:FestivalEvent):String=when(event.motif) {
    FestivalMotif.CHRISTMAS -> "把温暖留在这一页"
    FestivalMotif.LANTERN -> if("元宵" in event.name) "灯火可亲，团圆有时" else "新岁有光，心有所向"
    FestivalMotif.MOON -> "月光所至，皆是团圆"
    FestivalMotif.BOAT -> "一叶清香，岁岁安康"
    FestivalMotif.HEART -> "把喜欢，认真写下来"
    FestivalMotif.FLOWER -> if("重阳" in event.name) "把思念写成陪伴" else "愿每一份温柔都被看见"
    FestivalMotif.BOOK -> "把感谢留在字里行间"
    FestivalMotif.PUMPKIN -> "给平常的一天一点奇妙"
    FestivalMotif.WILLOW -> "在时光里，轻轻记得"
    FestivalMotif.MAPLE -> "收集生活里的小小暖意"
    FestivalMotif.SNOW -> "慢慢落笔，静候温暖"
    FestivalMotif.STAR -> "把闪光的日常好好收藏"
    FestivalMotif.BALLOON -> "留一点天真给今天"
    FestivalMotif.RIBBON -> "新的故事，正在展开"
}

@Composable fun FestivalAvatarWreath(modifier:Modifier=Modifier) {
    val event=LocalFestivalTheme.current?.event ?: return
    Canvas(modifier.clearAndSetSemantics {}) {
        val r=size.minDimension*.44f
        drawCircle(Brush.sweepGradient(listOf(Color(event.accent).copy(.65f),Color.Transparent,Color(event.companion).copy(.65f),Color.Transparent,Color(event.accent).copy(.65f))),r,style=Stroke(1.2.dp.toPx()))
        val extent=size.minDimension*.14f
        repeat(12) {i->
            val angle=(i*30f-90)*PI.toFloat()/180f
            drawCircle(Color(if(i%2==0)event.accent else event.companion).copy(.48f),.7.dp.toPx(),center+Offset(cos(angle),sin(angle))*r)
        }
        listOf(-50f,32f,135f).forEach { angle ->
            val a=angle*PI.toFloat()/180f
            val p=center+Offset(cos(a)*r,sin(a)*r)-Offset(extent/2,extent/2)
            motif(event,p,extent,.88f)
        }
    }
}

@Composable fun FestivalDateAdornment(modifier:Modifier=Modifier) {
    val theme=LocalFestivalTheme.current ?: return
    Canvas(modifier.semantics { contentDescription="${theme.event.name}日期装饰" }) {
        if(theme.event.motif==FestivalMotif.CHRISTMAS) {
            withTransform({ scale(size.width/100f,size.height/100f,Offset.Zero) }) {
                drawPath(FestivalPaths.hat,Color(theme.event.accent))
                drawRoundRect(Color(0xFFFFFAEF),Offset(8f,70f),Size(75f,21f),CornerRadius(8f,8f))
                drawCircle(Color(0xFFFFFAEF),11f,Offset(88f,40f))
            }
        } else motif(theme.event,extent=size.minDimension)
    }
}

@Composable fun FestivalIntroduction(events:List<FestivalEvent>) {
    events.forEach { event ->
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            FestivalEmblem(event,Modifier.size(24.dp));Spacer(Modifier.width(10.dp))
            SpaceText("${if(event.solarTerm) "今日节气" else "今日节日"} · ${event.name}",16.sp)
        }
        SpaceText(event.introduction,14.sp,Modifier.padding(top=10.dp),alpha=.78f)
    }
}
