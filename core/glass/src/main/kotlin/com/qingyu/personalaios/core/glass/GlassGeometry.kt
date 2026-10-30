package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.shape.IOSContinuousCornerShape

/** Pointer height is resolved to pixels alongside the popup's measured anchor. */
data class GlassMenuPointer(val fraction:Float,val top:Boolean=false,val height:Float=0f,val horizontal:Boolean=false)

sealed interface GlassGeometry {
    data class Corner(val extent: Dp, val roundness: Float = 0f,val pointer:GlassMenuPointer?=null) : GlassGeometry
    data object Capsule : GlassGeometry
    data object Circle : GlassGeometry
    fun shape(): Shape = when (this) {
        is Corner -> ContinuousGlassShape(CornerSize(extent), roundness,pointer)
        Capsule -> com.kyant.shapes.Capsule()
        Circle -> CircleShape
    }
}

/** Corner metrics for upstream lens(); the actual outline remains the app's cached G2 path. */
class ContinuousGlassShape(corner: CornerSize, private val roundness: Float = 0f,private val pointer:GlassMenuPointer?=null) : CornerBasedShape(corner, corner, corner, corner) {
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float,
        bottomStart: Float, layoutDirection: LayoutDirection): Outline {
        val radius=minOf(topStart,topEnd,bottomEnd,bottomStart,size.minDimension/2)
        pointer?.takeIf { it.horizontal && it.height>0f }?.let { tip ->
            val vertical=ContinuousGlassShape(CornerSize(radius.dp),roundness,tip.copy(horizontal=false))
                .createOutline(Size(size.height,size.width),radius,radius,radius,radius,layoutDirection) as Outline.Generic
            val result=Path().apply { addPath(vertical.path) }
            result.asAndroidPath().transform(android.graphics.Matrix().apply { setValues(floatArrayOf(0f,1f,0f,1f,0f,0f,0f,0f,1f)) })
            return Outline.Generic(result)
        }
        pointer?.takeIf { it.height>0f }?.let { tip ->
            val tail=tip.height.coerceIn(0f,size.height*.15f)
            val bodyHeight=size.height-tail
            val body=IOSContinuousCornerShape(radius.dp).createOutline(Size(size.width,bodyHeight),layoutDirection,Density(1f))
            val path=Path()
            val offset=Offset(0f,if(tip.top) tail else 0f)
            when(body) {
                is Outline.Generic -> path.addPath(body.path,offset)
                is Outline.Rounded -> path.addPath(Path().apply { addRoundRect(body.roundRect) },offset)
                is Outline.Rectangle -> path.addRect(body.rect.translate(offset))
            }
            val half=tail*1.5f
            val edge=(radius+half).coerceAtMost(size.width/2)
            val x=(size.width*tip.fraction).coerceIn(edge,size.width-edge)
            val y=if(tip.top) tail else bodyHeight
            val direction=if(tip.top) -1f else 1f
            fun at(value:Float)=y+direction*value
            val arrow=Path().apply {
                moveTo(x-half,at(-1f))
                cubicTo(x-half*.6f,at(-1f),x-3f,at(tail),x,at(tail))
                cubicTo(x+3f,at(tail),x+half*.6f,at(-1f),x+half,at(-1f))
                close()
            }
            return Outline.Generic(Path.combine(PathOperation.Union,path,arrow))
        }
        if(roundness<=0f) return IOSContinuousCornerShape(radius.dp)
            .createOutline(size,layoutDirection,Density(1f))
        // Two cubics per corner interpolate the circle's 45-degree arcs into our continuous
        // corner. The button cannot abruptly become a squircle on the very first/last frame.
        val t=roundness.coerceIn(0f,1f)
        fun mix(a:Float,b:Float)=a+(b-a)*t
        val a=mix(.36f,.2652165f);val b=mix(.68f,.5195704f)
        val c=mix(0f,.1055708f);val d=mix(.84f,.7071068f);val e=mix(.16f,.2928932f)
        val f=mix(1f,.8944292f);val g=mix(.32f,.4804296f);val h=mix(.64f,.7347835f)
        val path=Path();val w=size.width;val height=size.height
        path.moveTo(radius,0f);path.lineTo(w-radius,0f)
        path.cubicTo(w-radius+a*radius,0f,w-radius+b*radius,c*radius,w-radius+d*radius,e*radius)
        path.cubicTo(w-radius+f*radius,g*radius,w,h*radius,w,radius)
        path.lineTo(w,height-radius)
        path.cubicTo(w,height-radius+a*radius,w-c*radius,height-radius+b*radius,w-e*radius,height-radius+d*radius)
        path.cubicTo(w-g*radius,height-radius+f*radius,w-h*radius,height,w-radius,height)
        path.lineTo(radius,height)
        path.cubicTo(radius-a*radius,height,radius-b*radius,height-c*radius,radius-d*radius,height-e*radius)
        path.cubicTo(radius-f*radius,height-g*radius,0f,height-h*radius,0f,height-radius)
        path.lineTo(0f,radius)
        path.cubicTo(0f,radius-a*radius,c*radius,radius-b*radius,e*radius,radius-d*radius)
        path.cubicTo(g*radius,radius-f*radius,h*radius,0f,radius,0f)
        path.close()
        return Outline.Generic(path)
    }
    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize,
        bottomStart: CornerSize): CornerBasedShape {
        require(topStart == topEnd && topStart == bottomEnd && topStart == bottomStart) {
            "Glass geometry uses uniform continuous corners."
        }
        return ContinuousGlassShape(topStart,roundness,pointer)
    }
}
