package com.qingyu.personalaios.core.glass

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import kotlin.math.*

val LocalGlassLight = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(45f) }
/** Scoped material contrast, independent of the user's persisted glass tuning. */
val LocalGlassEdgeColor = compositionLocalOf { Color.White }
val LocalGlassInteractionColor = compositionLocalOf { Color.White }
fun glassHighlight(alpha:Float,angle:Float,color:Color=Color.White):Highlight = Highlight.Default.copy(alpha=alpha,
    style=if(color==Color.White) HighlightStyle.Default(angle=angle)
        else HighlightStyle.Default(color=color.copy(alpha=.75f),blendMode=BlendMode.SrcOver,angle=angle))

/** Shortest-arc filtering avoids a full spin when the pose crosses +/-180 degrees. */
object GlassLightMath {
    fun amplify(previous:Float,target:Float):Float {
        if(!target.isFinite()) return 0f
        return (((target-previous+540f)%360f+360f)%360f-180f)*2.4f
    }
    fun screenAngle(x:Float,y:Float,quarterTurns:Int):Float? {
        if(!x.isFinite() || !y.isFinite() || x*x+y*y<.015f)return null
        val vector=when(Math.floorMod(quarterTurns,4)) {
            1 -> -y to x
            2 -> -x to -y
            3 -> y to -x
            else -> x to y
        }
        return atan2(vector.second,vector.first)*180f/PI.toFloat()+180f
    }
    fun smooth(current:Float,target:Float,seconds:Float):Float {
        if(!target.isFinite()) return current
        val delta=((target-current+540f)%360f+360f)%360f-180f
        return current+delta*(1f-exp(-seconds.coerceIn(0f,.1f)/.045f))
    }
}
