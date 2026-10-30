package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Physical paper coordinates keep translation independent of sticker rotation. */
internal object StickerPlacement {
    /** Expand a page-fill viewport to the actual photo bounds for editing.
     * Pinch then scales the complete original, so previously hidden edges return. */
    fun fitPageImage(s:DiarySticker,imageAspect:Float,paperAspect:Float):DiarySticker {
        if(!s.fillPage || s.kind!=StickerKind.IMAGE) return s
        val cover=PhotoPageFill.cover(imageAspect,paperAspect*s.width/s.height)
        val w=s.width*cover.width;val h=s.height*cover.height
        // Existing object constraints cap huge panoramas; shrink uniformly,
        // rather than clamping either dimension and deforming the photo.
        val cap=minOf(1f,64f/maxOf(w,h))
        val width=w*cap;val height=h*cap
        if(width<.08f || height<.08f) return s
        return s.copy(x=(s.x+s.width/2-width/2).coerceIn(minOf(0f,1-width),maxOf(0f,1-width)),
            y=(s.y+s.height/2-height/2).coerceIn(minOf(0f,1-height),maxOf(0f,1-height)),width=width,height=height)
    }
    /** Replace legacy text's oversized frame without moving its rotated glyph origin. */
    fun fitText(s:DiarySticker,width:Float,height:Float,aspect:Float):DiarySticker {
        if(s.kind!=StickerKind.TEXT || s.text.isEmpty()) return s
        val w=width.coerceIn(.08f,1f);val h=height.coerceIn(.08f,1f)
        val dx=(s.width-w)/2;val dy=(s.height-h)/2/aspect
        val a=s.rotation*PI.toFloat()/180
        return s.copy(width=w,height=h,
            x=(s.x+dx-(dx*cos(a)-dy*sin(a))).coerceIn(0f,1f-w),
            y=(s.y+(dy-(dx*sin(a)+dy*cos(a)))*aspect).coerceIn(0f,1f-h))
    }
    data class Point(val x:Float,val y:Float) {
        fun clipped()=InkPoint(x.coerceIn(0f,1f),y.coerceIn(0f,1f))
    }
    fun local(s:DiarySticker,x:Float,y:Float,aspect:Float):Point {
        val a=-s.rotation*PI.toFloat()/180
        val dx=x-s.x-s.width/2;val dy=(y-s.y-s.height/2)/aspect
        return Point(.5f+(dx*cos(a)-dy*sin(a))/s.width,.5f+(dx*sin(a)+dy*cos(a))*aspect/s.height)
    }
    fun hit(s:DiarySticker,x:Float,y:Float,aspect:Float):Boolean {
        val p=local(s,x,y,aspect)
        return p.x in 0f..1f && p.y in 0f..1f && (s.crop.size<3 || inkInside(s.crop,p.clipped()))
    }
    fun move(s:DiarySticker,dx:Float,dy:Float,zoom:Float,turn:Float,cx:Float,cy:Float,aspect:Float):DiarySticker {
        if(s.locked) return s
        val pivot=local(s,cx,cy,aspect)
        return s.transformed(dx,dy,zoom,turn,pivot.x,pivot.y)
    }
}
