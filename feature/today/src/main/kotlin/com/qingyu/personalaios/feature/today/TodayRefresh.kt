package com.qingyu.personalaios.feature.today

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import kotlinx.coroutines.launch
import kotlin.math.*

internal object TodayPullRules {
    fun consume(distance:Float,available:Float,atTop:Boolean,refreshing:Boolean):Float = when {
        refreshing -> 0f
        available<0f && distance>0f -> maxOf(available,-distance)
        available>0f && atTop -> available
        else -> 0f
    }
    fun shouldRefresh(distance:Float,threshold:Float,refreshing:Boolean)=!refreshing && distance>=threshold
}

@Stable internal class TodayRefreshState(val threshold:Float,val scope:kotlinx.coroutines.CoroutineScope,val scroll:ScrollState,val action:State<suspend ()->Unit>) {
    var distance by mutableFloatStateOf(0f)
    var refreshing by mutableStateOf(false)
    val returnOffset=Animatable(0f)
    var returning by mutableStateOf(false)
    val displayOffset:Float get()=when { refreshing -> threshold;returning -> returnOffset.value;else -> distance*.5f }
    val connection=object:NestedScrollConnection {
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset {
            if(source!=NestedScrollSource.UserInput || available.y>=0 || refreshing) return Offset.Zero
            val take=TodayPullRules.consume(distance,available.y,scroll.value==0,refreshing)
            distance+=take
            return Offset(0f,take)
        }
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(source!=NestedScrollSource.UserInput || available.y<=0 || refreshing || returning) return Offset.Zero
            val take=TodayPullRules.consume(distance,available.y,scroll.value==0,refreshing)
            distance=(distance+take).coerceAtMost(threshold*3f)
            return Offset(0f,take)
        }
        override suspend fun onPreFling(available:Velocity):Velocity {
            if(distance<=0f || refreshing || returning) return Velocity.Zero
            release()
            return Velocity(0f,available.y)
        }
    }
    fun release() {
        if(refreshing || returning || distance<=0f) return
        val trigger=TodayPullRules.shouldRefresh(distance*.5f,threshold,false)
        val start=displayOffset
        distance=0f
        scope.launch {
            if(trigger) {
                refreshing=true
                try { action.value() } finally { refreshing=false;returning=true;returnOffset.snapTo(threshold) }
            } else { returning=true;returnOffset.snapTo(start) }
            try { returnOffset.animateTo(0f,spring(.8f,420f,.3f)) } finally { returning=false }
        }
    }
}
@Composable internal fun rememberTodayRefresh(scroll:ScrollState,action:suspend ()->Unit):TodayRefreshState {
    val density=LocalDensity.current;val threshold=with(density) { 60.dp.toPx() }
    val scope=rememberCoroutineScope();val latest=rememberUpdatedState(action)
    return remember(scroll,scope,threshold) { TodayRefreshState(threshold,scope,scroll,latest) }
}

@Composable internal fun TodayRefreshIndicator(state:TodayRefreshState,modifier:Modifier) {
    val environment=checkNotNull(LocalGlassEnvironment.current)
    val foreground=LocalSpaceColors.current.foreground
    // Remains laid out while invisible so the return never causes a top-row layout jump.
    GlassSurface(modifier.padding(top=4.dp).size(AppControls.Circle).graphicsLayer {
        val p=(state.displayOffset/state.threshold).coerceIn(0f,1f)
        alpha=p;translationY=(state.displayOffset-44.dp.toPx());scaleX=.65f+.35f*p;scaleY=scaleX
    }.clearAndSetSemantics { if(state.displayOffset>1f) contentDescription=if(state.refreshing) "正在同步天气" else "松手同步天气" },GlassGeometry.Circle) {
        Canvas(Modifier.align(Alignment.Center).size(AppControls.Icon).graphicsLayer {
            rotationZ=if(state.refreshing) environment.seconds.value*200f else state.displayOffset/state.threshold*180f
        }) {
            val r=size.minDimension*.35f
            val stroke=1.7.dp.toPx()
            drawArc(foreground,-90f,270f,false,center-Offset(r,r),androidx.compose.ui.geometry.Size(r*2,r*2),style=Stroke(stroke,cap=StrokeCap.Round))
            // The clockwise arc ends at the left edge, pointing upwards.
            val tip=center+Offset(-r,-r*.20f)
            val arrow=androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x,tip.y);lineTo(tip.x-r*.34f,tip.y+r*.44f);lineTo(tip.x+r*.34f,tip.y+r*.44f);close()
            }
            drawPath(arrow,foreground)
        }
    }
}
