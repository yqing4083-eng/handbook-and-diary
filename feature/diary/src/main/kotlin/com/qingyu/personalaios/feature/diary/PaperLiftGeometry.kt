package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Both orientations share the same retained artwork and progress clock. */
internal object PaperLiftGeometry {
    data class Source(val quad:BookQuad,val width:Float,val height:Float,val landscape:Boolean) {
        fun viewport(w:Float,h:Float,wide:Boolean):BookQuad {
            if(wide==landscape) return BookQuad(FloatArray(8) { i->quad.points[i]*(if(i%2==0) w/width else h/height) })
            return BookQuad(FloatArray(8) { i->
                val x=quad.points[i/2*2];val y=quad.points[i/2*2+1]
                if(wide) { if(i%2==0) y*w/height else (width-x)*h/width }
                else { if(i%2==0) (height-y)*w/height else x*h/width }
            })
        }
    }
    fun flat(rect:PaperRect,rotation:Float=0f):BookQuad {
        val angle=rotation*PI.toFloat()/180f;val c=cos(angle);val s=sin(angle)
        val corners=floatArrayOf(-rect.width/2,-rect.height/2,rect.width/2,-rect.height/2,rect.width/2,rect.height/2,-rect.width/2,rect.height/2)
        return BookQuad(FloatArray(8) { i->val x=corners[i/2*2];val y=corners[i/2*2+1]
            if(i%2==0) rect.centreX+x*c-y*s else rect.centreY+x*s+y*c })
    }
    /** Interpolate in the leaf's own frame. Linear corner interpolation alone
     * shrinks a rotating rectangle at its midpoint, like a collapsing book. */
    fun project(source:BookQuad,target:BookQuad,progress:Float):BookQuad {
        val p=progress.coerceIn(0f,1f)
        if(p==0f) return source;if(p==1f) return target
        fun angle(q:BookQuad)=atan2(q.points[3]-q.points[1],q.points[2]-q.points[0])
        fun cx(q:BookQuad)=(q.points[0]+q.points[2]+q.points[4]+q.points[6])/4
        fun cy(q:BookQuad)=(q.points[1]+q.points[3]+q.points[5]+q.points[7])/4
        val a=angle(source);val b=angle(target)
        var turn=b-a;while(turn>PI) turn-=2*PI.toFloat();while(turn< -PI) turn+=2*PI.toFloat()
        val c=cos(a+turn*p);val s=sin(a+turn*p)
        val x0=cx(source);val y0=cy(source);val x1=cx(target);val y1=cy(target)
        return BookQuad(FloatArray(8) { i->val k=i/2*2
            val sx=source.points[k]-x0;val sy=source.points[k+1]-y0
            val tx=target.points[k]-x1;val ty=target.points[k+1]-y1
            val x=(sx*cos(a)+sy*sin(a))*(1-p)+(tx*cos(b)+ty*sin(b))*p
            val y=(-sx*sin(a)+sy*cos(a))*(1-p)+(-tx*sin(b)+ty*cos(b))*p
            if(i%2==0) x0+(x1-x0)*p+x*c-y*s else y0+(y1-y0)*p+x*s+y*c
        })
    }
    data class Pose(val pitch:Float,val roll:Float,val backdrop:Float)
    fun pose(progress:Float,landscape:Boolean,aspect:Float):Pose {
        val p=progress.coerceIn(0f,1f)
        val lift=sin(PI.toFloat()*p)
        return Pose(if(landscape) -8f*lift else -4f*lift,
            if(landscape && aspect<1f) 90f*p else if(landscape) 3f*lift else 0f,
            p*p*(3-2*p))
    }
}

/** An uploaded texture is insufficient: the matching viewport must have
 * reached TextureView before the editor relinquishes its opaque snapshot. */
internal data class BookPresentedFrame(val width:Int,val height:Int,val page:String?,val revision:Long) {
    fun viewportMatches(w:Int,h:Int)=w>0 && h>0 && width==w && height==h
    fun matches(id:String,version:Long,w:Int,h:Int)=viewportMatches(w,h) && page==id && revision==version
}
