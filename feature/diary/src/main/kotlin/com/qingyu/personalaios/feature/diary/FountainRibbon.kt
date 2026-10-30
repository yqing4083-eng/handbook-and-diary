package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Continuous filled nib outline, with filtered pressure and rounded ends.
 * Live ink, handwriting glyphs and saved artwork share this exact painter. */
internal object FountainRibbon {
    private class Work {
        val path=Path();val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        var geometry=FloatArray(256)
        fun capacity(count:Int) { if(geometry.size<count*5) geometry=FloatArray(Integer.highestOneBit(count*5-1)*2) }
    }
    private val scratch=object:ThreadLocal<Work>() { override fun initialValue()=Work() }
    fun draw(canvas:Canvas,stroke:InkStroke,w:Float,h:Float,widthScale:Float=1f) {
        val work=checkNotNull(scratch.get());val ribbon=work.path;val paint=work.paint
        paint.color=stroke.color.toInt();paint.style=Paint.Style.FILL
        val nibWidth=stroke.width*w*widthScale
        val points=FountainNib.sample(InkCurve.sample(stroke.points,InkCurve.Density),w,h,nibWidth)
        if(points.isEmpty()) return
        if(points.size==1) {
            val p=points[0];canvas.drawCircle(p.x*w,p.y*h,nibWidth*.5f*(.68f+.32f*p.pressure.coerceIn(.15f,1.6f)),paint);return
        }
        work.capacity(points.size)
        val data=work.geometry;var total=0f;var pressure=points.first().pressure
        for(i in points.indices) {
            val p=points[i]
            val distance=if(i>0) hypot((p.x-points[i-1].x)*w,(p.y-points[i-1].y)*h) else 0f
            total+=distance
            val k=i*5;data[k]=p.x*w;data[k+1]=p.y*h;data[k+4]=total
        }
        var left=0;var right=0
        val window=(nibWidth*.65f).coerceAtLeast(.01f)
        for(i in points.indices) {
            val k=i*5;val p=points[i]
            // Arc-length tangent neighbourhood, rather than the two nearest
            // touch samples. Both cursors advance once, keeping this O(n).
            while(left<i && data[(left+1)*5+4]<data[k+4]-window) left++
            right=max(right,i)
            while(right<points.lastIndex && data[right*5+4]<data[k+4]+window) right++
            var dx=data[right*5]-data[left*5];var dy=data[right*5+1]-data[left*5+1]
            if(hypot(dx,dy)<.001f) { val a=points[max(0,i-1)];dx=(p.x-a.x)*w;dy=(p.y-a.y)*h }
            val length=hypot(dx,dy).coerceAtLeast(.001f)
            val distance=if(i>0) data[k+4]-data[k-1] else 0f
            pressure+=(p.pressure-pressure)*(1-exp(-distance/(nibWidth*.8f).coerceAtLeast(1f)))
            data[k+2]=-dy/length;data[k+3]=dx/length
            // Cache the normal scaled by the filtered physical nib footprint.
            val radius=FountainNib.radius(nibWidth,pressure,data[k+2],data[k+3],Float.MAX_VALUE,Float.MAX_VALUE)
            data[k+2]*=radius;data[k+3]*=radius
        }
        for(i in points.indices) {
            val k=i*5
            val taper=FountainNib.taper(nibWidth,data[k+4],total-data[k+4]);data[k+2]*=taper;data[k+3]*=taper
        }
        fun x(i:Int,side:Float)=data[i*5]+side*data[i*5+2]
        fun y(i:Int,side:Float)=data[i*5+1]+side*data[i*5+3]
        fun edge(from:Int,to:Int,step:Int,side:Float) {
            var i=from
            while(i!=to) {
                val next=i+step
                ribbon.quadTo(x(i,side),y(i,side),(x(i,side)+x(next,side))/2,(y(i,side)+y(next,side))/2)
                i=next
            }
            ribbon.lineTo(x(to,side),y(to,side))
        }
        val last=points.lastIndex;val end=last*5
        ribbon.rewind();ribbon.moveTo(x(0,1f),y(0,1f));edge(0,last,1,1f)
        fun cap(k:Int,direction:Float) {
            val nx=data[k+2]*direction;val ny=data[k+3]*direction
            val tx=ny;val ty=-nx;val cx=data[k];val cy=data[k+1];val q=.55228475f
            ribbon.cubicTo(cx+nx+tx*q,cy+ny+ty*q,cx+tx+nx*q,cy+ty+ny*q,cx+tx,cy+ty)
            ribbon.cubicTo(cx+tx-nx*q,cy+ty-ny*q,cx-nx+tx*q,cy-ny+ty*q,cx-nx,cy-ny)
        }
        cap(end,1f)
        edge(last,0,-1,-1f)
        cap(0,-1f)
        ribbon.close();canvas.drawPath(ribbon,paint)
    }
}

