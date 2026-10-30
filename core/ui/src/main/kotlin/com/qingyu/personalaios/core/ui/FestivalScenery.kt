package com.qingyu.personalaios.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.theme.*
import kotlin.math.*

/** Shared, cached silhouettes; no frame builds paths or adds blur/render targets. */
private object SeasonalPaths {
    val garland=Path().apply {moveTo(-5f,0f);cubicTo(20f,24f,30f,24f,50f,7f);cubicTo(70f,24f,80f,24f,105f,0f)}
    val cloud=Path().apply {moveTo(0f,21f);lineTo(12f,21f);cubicTo(11f,8f,32f,4f,36f,17f);cubicTo(42f,-2f,69f,2f,71f,19f);cubicTo(82f,9f,98f,16f,100f,25f)}
    val river=Path().apply {moveTo(-10f,26f);cubicTo(20f,0f,35f,52f,65f,25f);cubicTo(85f,9f,97f,14f,110f,22f)}
    val branch=Path().apply {moveTo(0f,0f);cubicTo(45f,7f,75f,42f,100f,100f)}
    val bat=Path().apply {moveTo(50f,55f);quadraticTo(22f,9f,0f,20f);quadraticTo(17f,36f,14f,65f);quadraticTo(30f,48f,38f,75f);lineTo(50f,65f);lineTo(62f,75f);quadraticTo(70f,48f,86f,65f);quadraticTo(83f,36f,100f,20f);quadraticTo(78f,9f,50f,55f);close()}
    val bookmark=Path().apply {moveTo(0f,0f);lineTo(28f,0f);lineTo(28f,100f);lineTo(14f,86f);lineTo(0f,100f);close()}
    val ribbon=Path().apply {moveTo(0f,0f);cubicTo(95f,20f,-60f,50f,30f,68f);cubicTo(75f,80f,37f,92f,25f,100f)}
    val edges=listOf(Offset(.035f,.32f),Offset(.96f,.42f),Offset(.07f,.75f),Offset(.93f,.83f),Offset(.20f,.96f),Offset(.80f,.06f))
}

@Composable internal fun FestivalSceneBackdrop(theme:FestivalTheme?,seconds:State<Float>,dayMode:Boolean,modifier:Modifier=Modifier) {
    val event=theme?.event ?: return
    Spacer(modifier.fillMaxSize().clearAndSetSemantics {}.drawWithCache {
        val accent=Color(event.accent);val secondary=Color(event.companion)
        // Color washes belong to the margins, so custom wallpaper and central text stay readable.
        val top=Brush.radialGradient(listOf(accent.copy(if(dayMode) .10f else .13f),Color.Transparent),Offset(size.width*.98f,size.height*.12f),size.width*.74f)
        val bottom=Brush.radialGradient(listOf(secondary.copy(if(dayMode) .09f else .11f),Color.Transparent),Offset(0f,size.height*.89f),size.width*.85f)
        onDrawBehind {
            drawRect(top);drawRect(bottom)
            val time=seconds.value
            drawSeason(event,time,if(dayMode) .50f else .58f)
            drawFestivalDetails(event,time,if(dayMode) .50f else .58f)
        }
    })
}

private fun DrawScope.art(path:Path,at:Offset,width:Float,height:Float,color:Color,stroke:Float?=null) {
    withTransform({translate(at.x,at.y);scale(width/100f,height/100f,Offset.Zero)}) {
        drawPath(path,color,style=if(stroke==null) Fill else Stroke(stroke,cap=StrokeCap.Round))
    }
}
private fun DrawScope.spark(at:Offset,radius:Float,color:Color) {
    drawLine(color,at-Offset(radius,0f),at+Offset(radius,0f),1.dp.toPx(),StrokeCap.Round)
    drawLine(color,at-Offset(0f,radius),at+Offset(0f,radius),1.dp.toPx(),StrokeCap.Round)
    drawCircle(color,radius*.19f,at)
}
private fun DrawScope.firework(at:Offset,r:Float,color:Color,phase:Float) {
    val glow=.7f+.3f*sin(phase)
    repeat(10) { i ->
        val angle=i*PI.toFloat()/5
        val direction=Offset(cos(angle),sin(angle))
        drawLine(color.copy(alpha=color.alpha*glow),at+direction*r*.55f,at+direction*r,1.dp.toPx(),StrokeCap.Round)
        drawCircle(color.copy(alpha=color.alpha*.7f),1.dp.toPx(),at+direction*r*1.23f)
    }
}
private fun DrawScope.drifting(event:FestivalEvent,t:Float,alpha:Float) {
    SeasonalPaths.edges.forEachIndexed { i,p ->
        val extent=(if(i%2==0) 19f else 12f).dp.toPx()
        val origin=Offset(p.x*size.width+sin(t*.11f+i)*3.dp.toPx(),p.y*size.height+sin(t*.16f+i)*6.dp.toPx())
        rotate(sin(t*.13f+i)*14f,origin+Offset(extent/2,extent/2)) {motif(event,origin,extent,alpha)}
    }
}
private fun DrawScope.botanical(at:Offset,extent:Float,color:Color,mirror:Boolean=false) {
    withTransform({translate(at.x,at.y);scale(if(mirror) -1f else 1f,1f,Offset.Zero)}) {
        art(SeasonalPaths.branch,Offset.Zero,extent,extent,color.copy(alpha=color.alpha*.65f),1f)
        repeat(7) { i ->
            val x=extent*(.13f+i*.105f);val y=extent*(.035f+i*i*.017f)
            rotate(if(i%2==0) -32f else 38f,Offset(x,y)) {
                drawOval(color,Offset(x-extent*.04f,y-extent*.02f),Size(extent*.18f,extent*.065f))
            }
        }
    }
}
private fun DrawScope.blossom(at:Offset,r:Float,petals:Int,a:Color,b:Color) {
    repeat(petals) { i -> rotate(i*360f/petals,at) {
        drawOval(a,at+Offset(-r*.18f,-r),Size(r*.36f,r*.82f))
    }}
    drawCircle(b,r*.21f,at)
    drawCircle(Color.White.copy(alpha=a.alpha*.65f),r*.065f,at-Offset(r*.06f,r*.06f))
}

private fun DrawScope.drawSeason(event:FestivalEvent,t:Float,opacity:Float) {
    val a=Color(event.accent).copy(opacity);val b=Color(event.companion).copy(opacity)
    val gold=Color(0xFFF0CF8F).copy(opacity)
    val w=size.width;val h=size.height;val u=1.dp.toPx()
    when(event.motif) {
        FestivalMotif.CHRISTMAS -> {
            art(SeasonalPaths.garland,Offset(0f,-8*u),w,100*u,b.copy(alpha=.35f),8f)
            art(SeasonalPaths.garland,Offset(0f,-8*u),w,100*u,gold.copy(alpha=.40f),.65f)
            repeat(11) { i ->
                val x=w*i/10;val y=(7f+sin(i*PI/5).toFloat().let {abs(it)}*10f)*u
                val glow=.7f+.3f*sin(t*.65f+i)
                drawCircle(gold.copy(alpha=.075f*glow),8*u,Offset(x,y))
                drawCircle(if(i%3==0) a.copy(alpha=.65f*glow) else gold.copy(alpha=.8f*glow),1.8f*u,Offset(x,y))
            }
            listOf(Offset(.045f,.10f),Offset(.93f,.13f)).forEachIndexed { i,p ->
                val c=Offset(w*p.x,h*p.y);drawLine(gold.copy(alpha=.4f),Offset(c.x,0f),c, .7f*u)
                drawCircle(if(i==0)a else gold,6*u,c);drawCircle(Color.White.copy(.30f),1.5f*u,c-Offset(2*u,2*u))
            }
            drifting(event.copy(motif=FestivalMotif.SNOW),t,.26f)
            motif(event,Offset(w-57*u,h*.86f),52*u,.4f)
        }
        FestivalMotif.LANTERN -> {
            listOf(Offset(.06f,.065f),Offset(.88f,.10f)).forEachIndexed { i,p ->
                val at=Offset(w*p.x,h*p.y);val extent=(if(i==0)42 else 32)*u
                drawLine(b.copy(alpha=.4f),Offset(at.x+extent/2,0f),at+Offset(extent/2,0f),.8f*u)
                rotate(sin(t*.23f+i)*3f,at+Offset(extent/2,0f)) {motif(event,at,extent,.65f)}
            }
            art(SeasonalPaths.cloud,Offset(-25*u,h*.88f),160*u,48*u,gold.copy(alpha=.25f),1f)
            art(SeasonalPaths.cloud,Offset(w-110*u,h*.29f),150*u,38*u,gold.copy(alpha=.18f),.8f)
            firework(Offset(w*.97f,h*.61f),19*u,gold.copy(alpha=.24f),t*.4f)
            repeat(8) {i -> val x=if(i%2==0)w*.035f else w*.96f
                drawCircle(gold.copy(alpha=.16f+.08f*sin(t*.3f+i)),(1f+i%3*.5f)*u,Offset(x,h*(.3f+i*.075f)+sin(t*.12f+i)*8*u))}
        }
        FestivalMotif.MOON -> {
            val moon=Offset(w*1.01f,h*.13f);val r=62*u
            drawCircle(a.copy(alpha=.045f),r*1.32f,moon);drawCircle(a.copy(alpha=.20f),r,moon)
            drawCircle(a.copy(alpha=.28f),r,moon,style=Stroke(.8f*u))
            drawCircle(b.copy(alpha=.08f),r*.14f,moon-Offset(r*.4f,r*.25f))
            art(SeasonalPaths.cloud,Offset(w-135*u,h*.23f),170*u,38*u,b.copy(alpha=.25f),1f)
            art(SeasonalPaths.cloud,Offset(-25*u,h*.87f),150*u,35*u,a.copy(alpha=.24f),1f)
            SeasonalPaths.edges.take(4).forEachIndexed {i,p->spark(Offset(w*p.x,h*p.y),3*u,a.copy(alpha=.22f+.10f*sin(t*.25f+i)))}
        }
        FestivalMotif.BOAT -> {
            repeat(3) { i->art(SeasonalPaths.river,Offset(-w*.05f,h*(.89f+i*.025f)),w*1.1f,32*u,b.copy(alpha=.12f-i*.025f),.75f)}
            botanical(Offset(-16*u,h*.79f),90*u,a.copy(alpha=.28f))
            botanical(Offset(w+6*u,h*.1f),62*u,a.copy(alpha=.2f),true)
            motif(event,Offset(w-68*u,h*.87f+sin(t*.18f)*2*u),61*u,.38f)
        }
        FestivalMotif.HEART -> {
            listOf(Offset(.04f,.20f),Offset(.94f,.34f),Offset(.08f,.82f)).forEachIndexed {i,p->
                val center=Offset(w*p.x,h*p.y+sin(t*.15f+i)*5*u);val s=(if(i==1)25 else 17)*u
                motif(event,center,s,.30f)
                spark(center+Offset(s*.8f,-s*.3f),2*u,b.copy(alpha=.45f))
            }
            art(SeasonalPaths.ribbon,Offset(w-12*u,h*.6f),25*u,165*u,a.copy(alpha=.15f),1f)
        }
        FestivalMotif.FLOWER -> {
            botanical(Offset(-22*u,h*.11f),90*u,b.copy(alpha=.25f))
            botanical(Offset(w+16*u,h*.79f),110*u,b.copy(alpha=.3f),true)
            val chrysanthemum="重阳" in event.name
            listOf(Offset(.025f,.15f),Offset(.93f,.85f),Offset(.99f,.78f)).forEachIndexed {i,p->
                blossom(Offset(w*p.x,h*p.y),(if(i==1)21 else 13)*u,if(chrysanthemum)16 else 7,a.copy(alpha=.4f),b.copy(alpha=.6f))
            }
            repeat(6) {i-> val x=if(i%2==0)w*.035f else w*.96f
                val p=Offset(x+sin(t*.15f+i)*4*u,h*(.31f+i*.08f)+sin(t*.12f+i)*5*u)
                rotate(i*29f+sin(t*.1f)*12f,p){drawOval(a.copy(alpha=.16f),p,Size(5*u,2.3f*u))}}
        }
        FestivalMotif.STAR -> {
            firework(Offset(w*.95f,h*.16f),31*u,gold.copy(alpha=.32f),t*.35f)
            firework(Offset(w*.025f,h*.82f),23*u,a.copy(alpha=.22f),t*.35f+2f)
            repeat(4) {i->spark(Offset(w*(if(i%2==0).06f else .95f),h*(.35f+i*.14f)),(2+i%2)*u,gold.copy(alpha=.3f))}
        }
        FestivalMotif.BALLOON -> {
            motif(event,Offset(-15*u,h*.075f),64*u,.38f)
            motif(event,Offset(w-40*u,h*.80f),57*u,.30f)
            confetti(t,a,b)
        }
        FestivalMotif.BOOK -> {
            art(SeasonalPaths.bookmark,Offset(w-25*u,0f),30*u,80*u,a.copy(alpha=.23f))
            repeat(4) {i->drawLine(b.copy(alpha=.12f),Offset(w-35*u,h*.72f+i*7*u),Offset(w+10*u,h*.72f+i*7*u),.8f*u)}
            motif(event,Offset(-10*u,h*.85f),55*u,.28f)
            spark(Offset(w*.94f,h*.33f),3*u,b.copy(alpha=.32f))
        }
        FestivalMotif.PUMPKIN -> {
            drawCircle(b.copy(alpha=.10f),55*u,Offset(w*1.04f,h*.15f))
            listOf(Offset(.92f,.19f),Offset(.04f,.39f),Offset(.96f,.65f)).forEachIndexed {i,p->
                val at=Offset(w*p.x+sin(t*.22f+i)*3*u,h*p.y)
                rotate(-10f+i*13f,at) {art(SeasonalPaths.bat,at,19*u,16*u,b.copy(alpha=.32f))}
            }
            motif(event,Offset(-6*u,h*.84f),58*u,.48f)
            botanical(Offset(w+10*u,h*.79f),65*u,b.copy(alpha=.18f),true)
            spark(Offset(w*.93f,h*.48f),3*u,a.copy(alpha=.3f))
        }
        FestivalMotif.MAPLE -> {
            motif(event,Offset(-27*u,h*.80f),80*u,.25f)
            drifting(event,t,.27f)
        }
        FestivalMotif.WILLOW -> {
            botanical(Offset(-8*u,18*u),103*u,a.copy(alpha=.27f))
            botanical(Offset(w+15*u,0f),125*u,b.copy(alpha=.21f),true)
            repeat(3){i->drawArc(b.copy(alpha=.09f-i*.018f),200f,140f,false,Offset(w*.72f,h*.88f+i*8*u),Size(120*u,24*u),style=Stroke(.6f*u))}
        }
        FestivalMotif.SNOW -> {
            listOf(Offset(.04f,.15f),Offset(.95f,.35f),Offset(.015f,.79f),Offset(.96f,.91f)).forEachIndexed {i,p->
                val e=(if(i%2==0) 29 else 18)*u;val at=Offset(w*p.x,h*p.y)
                rotate(sin(t*.1f+i)*7f,at){motif(event,at,e,.26f)}
            }
            art(SeasonalPaths.cloud,Offset(-30*u,h*.93f),w+60*u,48*u,b.copy(alpha=.12f),.65f)
        }
        FestivalMotif.RIBBON -> {
            art(SeasonalPaths.ribbon,Offset(-8*u,h*.06f),29*u,180*u,a.copy(alpha=.27f),1.1f)
            art(SeasonalPaths.ribbon,Offset(w-18*u,h*.70f),25*u,155*u,b.copy(alpha=.28f),1.1f)
            confetti(t,a,b)
        }
    }
}
private fun DrawScope.confetti(t:Float,a:Color,b:Color) {
    repeat(16) {i->
        val x=size.width*(if(i%2==0) .025f+(i%3)*.018f else .97f-(i%3)*.018f)
        val y=size.height*(.10f+i*.052f)+sin(t*.15f+i)*4.dp.toPx()
        val p=Offset(x,y)
        rotate(i*31f+sin(t*.13f)*8f,p) {
            drawRoundRect((if(i%3==0)a else b).copy(alpha=.24f),p,Size(2.dp.toPx(),5.dp.toPx()),CornerRadius(1.dp.toPx()))
        }
    }
}
