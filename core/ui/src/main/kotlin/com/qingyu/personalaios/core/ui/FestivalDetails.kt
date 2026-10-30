package com.qingyu.personalaios.core.ui

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.theme.*
import kotlin.math.*

/** Small foreground details at the margins of the existing background canvas.
 * Shared paths are built once; no additional layer, blur or animation clock. */
private object DetailPaths {
    val cane=Path().apply {moveTo(35f,94f);lineTo(35f,28f);cubicTo(35f,1f,77f,1f,77f,28f);lineTo(77f,37f)}
    val knot=Path().apply {moveTo(50f,15f);lineTo(78f,43f);lineTo(50f,71f);lineTo(22f,43f);close();moveTo(50f,28f);lineTo(65f,43f);lineTo(50f,58f);lineTo(35f,43f);close()}
    val parcel=Path().apply {moveTo(8f,73f);lineTo(57f,9f);quadraticTo(63f,8f,68f,18f);lineTo(94f,82f);quadraticTo(53f,103f,8f,73f);close()}
    val envelope=Path().apply {moveTo(8f,26f);lineTo(92f,26f);lineTo(92f,83f);lineTo(8f,83f);close();moveTo(8f,26f);lineTo(50f,60f);lineTo(92f,26f)}
    val kite=Path().apply {moveTo(48f,2f);lineTo(82f,33f);lineTo(51f,72f);lineTo(16f,37f);close();moveTo(51f,72f);cubicTo(25f,83f,75f,85f,45f,100f)}
    val wing=Path().apply {moveTo(50f,53f);cubicTo(15f,-12f,-10f,30f,20f,54f);cubicTo(-4f,91f,42f,95f,50f,53f);cubicTo(58f,95f,104f,91f,80f,54f);cubicTo(110f,30f,85f,-12f,50f,53f);close()}
    val steam=Path().apply {moveTo(22f,36f);cubicTo(6f,19f,37f,19f,22f,1f);moveTo(49f,36f);cubicTo(33f,19f,64f,19f,49f,1f);moveTo(76f,36f);cubicTo(60f,19f,91f,19f,76f,1f)}
}

private fun DrawScope.detail(at:Offset,extent:Float,draw:DrawScope.()->Unit) {
    withTransform({translate(at.x,at.y);scale(extent/100f,extent/100f,Offset.Zero)},draw)
}
private fun DrawScope.gift(at:Offset,s:Float,a:Color,b:Color) = detail(at,s) {
    drawRoundRect(a,Offset(10f,40f),Size(80f,54f),CornerRadius(6f))
    drawRoundRect(b,Offset(6f,31f),Size(88f,16f),CornerRadius(4f))
    drawLine(b,Offset(50f,38f),Offset(50f,94f),9f)
    drawOval(b,Offset(23f,7f),Size(28f,25f),style=Stroke(5f))
    drawOval(b,Offset(49f,7f),Size(28f,25f),style=Stroke(5f))
    drawLine(Color.White.copy(a.alpha*.7f),Offset(17f,49f),Offset(17f,84f),2f)
}
private fun DrawScope.rabbit(at:Offset,s:Float,c:Color) = detail(at,s) {
    rotate(-13f,Offset(50f,48f)) {drawOval(c,Offset(35f,1f),Size(13f,44f))}
    rotate(9f,Offset(50f,48f)) {drawOval(c,Offset(52f,1f),Size(13f,44f))}
    drawOval(c,Offset(16f,43f),Size(68f,50f));drawCircle(c,22f,Offset(52f,47f))
    drawCircle(c,11f,Offset(12f,73f));drawCircle(Color(0xFF7D6578).copy(c.alpha),2.5f,Offset(63f,44f))
    drawOval(Color(0xFFE5A8A2).copy(c.alpha*.6f),Offset(59f,53f),Size(13f,6f))
}

internal fun DrawScope.drawFestivalDetails(event:FestivalEvent,t:Float,opacity:Float) {
    val u=1.dp.toPx();val w=size.width;val h=size.height
    val a=Color(event.accent).copy(opacity*.68f);val b=Color(event.companion).copy(opacity*.66f)
    val cream=Color(0xFFFFE8BB).copy(opacity*.75f)
    when(event.motif) {
        FestivalMotif.CHRISTMAS -> {
            gift(Offset(w*.055f,h*.92f),27*u,a,cream);gift(Offset(w*.13f,h*.935f),19*u,b,a)
            detail(Offset(w-34*u,h*.37f),30*u) {
                rotate(-13f,Offset(50f,50f)) {
                    drawPath(DetailPaths.cane,cream,style=Stroke(13f,cap=StrokeCap.Round))
                    drawLine(a,Offset(29f,43f),Offset(41f,35f),7f);drawLine(a,Offset(29f,66f),Offset(41f,58f),7f)
                    drawLine(a,Offset(29f,88f),Offset(41f,80f),7f);drawLine(a,Offset(61f,11f),Offset(62f,24f),7f)
                }
            }
            repeat(5) {i->val x=w*(.035f+i*.008f);val y=h*(.48f+i*.045f)+sin(t*.2f+i)*3*u
                drawCircle(cream.copy(alpha=.17f),1.1f*u,Offset(x,y))}
        }
        FestivalMotif.LANTERN -> {
            detail(Offset(-8*u,h*.60f),30*u) {
                drawLine(b,Offset(50f,0f),Offset(50f,15f),2f)
                drawPath(DetailPaths.knot,a,style=Stroke(7f,join=StrokeJoin.Round))
                repeat(3) {i->drawLine(b,Offset(39f+i*11,70f),Offset(39f+i*11,96f),3f)}
            }
            detail(Offset(w-35*u,h*.91f),28*u) {
                rotate(13f,Offset(50f,50f)) {
                    drawRoundRect(a,Offset(12f,5f),Size(76f,90f),CornerRadius(7f))
                    drawArc(b,0f,180f,false,Offset(12f,-15f),Size(76f,54f),style=Stroke(3f))
                    drawCircle(b,15f,Offset(50f,60f),style=Stroke(3f));drawCircle(b,3f,Offset(50f,60f))
                }
            }
            repeat(3) {i-> val at=Offset(w*.035f,h*(.88f+i*.012f));drawLine(b,at,at+Offset(7*u,0f),.6f*u)}
        }
        FestivalMotif.MOON -> {
            rabbit(Offset(-8*u,h*.89f),46*u,cream)
            detail(Offset(w-22*u,h*.73f),35*u) {
                drawLine(b,Offset(90f,0f),Offset(10f,100f),2f)
                repeat(5) {i-> val p=Offset(70f-i*13,20f+i*16)
                    repeat(4) {j->rotate(j*90f,p) {drawOval(cream,p-Offset(2f,8f),Size(4f,8f))}}}
            }
        }
        FestivalMotif.BOAT -> {
            detail(Offset(-9*u,h*.58f),33*u) {
                drawPath(DetailPaths.parcel,a);drawLine(cream,Offset(57f,12f),Offset(38f,86f),3f)
                drawLine(cream,Offset(14f,68f),Offset(83f,59f),2f)
                drawLine(b,Offset(62f,22f),Offset(84f,76f),1.5f)
            }
            detail(Offset(w-20*u,h*.34f),28*u) {
                drawOval(b,Offset(24f,20f),Size(54f,64f));drawLine(a,Offset(25f,26f),Offset(77f,26f),6f)
                drawLine(cream,Offset(51f,0f),Offset(51f,21f),2f);drawLine(a,Offset(50f,81f),Offset(50f,100f),4f)
            }
        }
        FestivalMotif.HEART -> {
            detail(Offset(w-37*u,h*.88f),33*u) {
                rotate(-12f,Offset(50f,50f)) {drawPath(DetailPaths.envelope,b,style=Stroke(3f,join=StrokeJoin.Round))
                    scale(.25f,.25f,Offset.Zero) {translate(150f,175f) {drawPath(FestivalPaths.heart,a)}}}
            }
            repeat(3) {i->drawCircle(cream.copy(alpha=.22f),.7f*u,Offset(w*.045f,h*(.48f+i*.035f)))}
        }
        FestivalMotif.FLOWER -> {
            detail(Offset(w-23*u,h*.39f),24*u) {
                rotate(sin(t*.18f)*9f,Offset(50f,50f)) {
                    drawPath(DetailPaths.wing,b);drawLine(cream,Offset(50f,35f),Offset(50f,75f),3f)
                }
            }
            repeat(4) {i->val p=Offset((10f+i*4)*u,h*.88f+i*8*u)
                drawCircle(b.copy(alpha=.3f),2*u,p);drawCircle(cream.copy(alpha=.4f),.6f*u,p-Offset(.7f*u,.7f*u))}
        }
        FestivalMotif.STAR, FestivalMotif.RIBBON, FestivalMotif.BALLOON -> {
            gift(Offset(-8*u,h*.91f),32*u,a,b)
            detail(Offset(w-15*u,h*.46f),24*u) {
                repeat(4) {i->rotate(i*90f,Offset(50f,50f)) {
                    drawOval(if(i%2==0) a else b,Offset(45f,6f),Size(30f,44f))}}
                drawCircle(cream,6f,Offset(50f,50f));drawLine(b,Offset(50f,57f),Offset(50f,100f),2f)
            }
        }
        FestivalMotif.BOOK -> {
            detail(Offset(w-15*u,h*.50f),26*u) {
                rotate(28f,Offset(50f,50f)) {
                    drawRoundRect(b,Offset(42f,12f),Size(15f,65f),CornerRadius(4f))
                    drawLine(cream,Offset(49f,78f),Offset(49f,94f),3f)
                    drawLine(a,Offset(44f,27f),Offset(56f,27f),4f)
                }
            }
        }
        FestivalMotif.PUMPKIN -> {
            val at=Offset(w,0f)
            for(i in 1..3) drawArc(b.copy(alpha=.16f),90f,90f,false,at-Offset(i*18*u,i*18*u),Size(i*36*u,i*36*u),style=Stroke(.5f*u))
            for(i in 1..3) {val angle=(90f+i*22.5f)*PI.toFloat()/180
                drawLine(b.copy(alpha=.17f),at,at+Offset(cos(angle),sin(angle))*54f*u,.5f*u)}
            detail(Offset(w-26*u,h*.90f),31*u) {
                drawOval(a,Offset(24f,31f),Size(52f,38f));drawLine(b,Offset(8f,25f),Offset(24f,45f),7f)
                drawLine(b,Offset(8f,75f),Offset(24f,55f),7f);drawLine(b,Offset(76f,45f),Offset(92f,25f),7f)
                drawLine(b,Offset(76f,55f),Offset(92f,75f),7f)
            }
        }
        FestivalMotif.MAPLE -> detail(Offset(w-23*u,h*.45f),28*u) {
            drawOval(a,Offset(30f,27f),Size(42f,58f));drawArc(b,180f,180f,true,Offset(23f,15f),Size(56f,42f))
            drawLine(b,Offset(50f,11f),Offset(58f,2f),5f);drawLine(cream.copy(alpha=.5f),Offset(40f,48f),Offset(40f,69f),2f)
        }
        FestivalMotif.WILLOW -> detail(Offset(w-20*u,h*.53f),35*u) {
            rotate(-10f,Offset(50f,50f)) {drawPath(DetailPaths.kite,b,style=Stroke(2f))
                drawLine(a,Offset(16f,37f),Offset(82f,33f),2f);drawLine(a,Offset(48f,2f),Offset(51f,72f),2f)}
        }
        FestivalMotif.SNOW -> detail(Offset(-5*u,h*.88f),38*u) {
            drawPath(DetailPaths.steam,b.copy(alpha=.3f),style=Stroke(2f))
            drawArc(a,0f,180f,true,Offset(8f,40f),Size(84f,80f))
            drawOval(cream,Offset(8f,38f),Size(84f,28f));repeat(3) {i->drawCircle(b,7f,Offset(31f+i*20,50f))}
            drawLine(b,Offset(31f,91f),Offset(69f,91f),3f)
        }
    }
}
