package com.qingyu.personalaios.feature.me

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** A small neck grows toward the header during scroll, then retraces on return. */
internal class AvatarCollapseShape(private val neck:Float):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val w=size.width;val h=size.height;val n=neck.coerceIn(0f,1f)
        return Outline.Generic(Path().apply {
            if(n<.001f) addOval(Rect(0f,0f,w,h)) else {
                val bodyY=h*(.5f+.14f*n)
                moveTo(w*.5f,0f)
                cubicTo(w*(.6104f+.0696f*n),0f,w*(.71248f-.01248f*n),h*(.03584f+.08416f*n),w*(.795232f-.205232f*n),h*(.096512f+.123488f*n))
                cubicTo(w*(.91936f-.37936f*n),h*(.18752f+.13648f*n),w,h*(.3344f+.0396f*n),w,bodyY)
                cubicTo(w,h*(.776f+.06f*n),w*.776f,h,w*.5f,h)
                cubicTo(w*.224f,h,0f,h*(.776f+.06f*n),0f,bodyY)
                cubicTo(0f,h*(.3344f+.0396f*n),w*(.08064f+.37936f*n),h*(.18752f+.13648f*n),w*(.204768f+.205232f*n),h*(.096512f+.123488f*n))
                cubicTo(w*(.28752f+.01248f*n),h*(.03584f+.08416f*n),w*(.3896f-.0696f*n),0f,w*.5f,0f);close()
            }
        })
    }
}
