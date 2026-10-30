package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.DiarySticker
import kotlin.math.*

/** The occupied band comes only from stickers. Ink never participates in text flow. */
internal object RuledFlowGeometry {
    data class Interval(val left:Float,val right:Float) { val width get()=right-left }
    data class Cell(val left:Float,val top:Float,val right:Float,val bottom:Float)
    fun slots(width:Float,height:Float,top:Float,bottom:Float,stickers:List<DiarySticker>,left:Float=width*.065f,
        contours:Map<String,List<Cell>> = emptyMap()):List<Interval> {
        val right=width*.935f
        var slots=listOf(Interval(left.coerceAtMost(right),right))
        val pad=max(1f,width*.0015f)
        for(s in stickers) {
            val cx=(s.x+s.width/2)*width;val cy=(s.y+s.height/2)*height
            val a=s.rotation*PI.toFloat()/180;val c=cos(a);val sn=sin(a)
            val cells=contours[s.id] ?: listOf(Cell(0f,0f,1f,1f))
            val blocked=ArrayList<Interval>()
            for(cell in cells) {
            val points=listOf(cell.left to cell.top,cell.right to cell.top,cell.right to cell.bottom,cell.left to cell.bottom).map { (dx,dy)->
                val x=(dx-.5f)*s.width*width;val y=(dy-.5f)*s.height*height
                (cx+x*c-y*sn) to (cy+x*sn+y*c)
            }
            val lo=top-pad;val hi=bottom+pad
            if(points.maxOf { it.second }<lo || points.minOf { it.second }>hi) continue
            val xs=points.filter { it.second in lo..hi }.map { it.first }.toMutableList()
            for(i in points.indices) {
                val p=points[i];val q=points[(i+1)%4]
                for(y in listOf(lo,hi)) if(y in min(p.second,q.second)..max(p.second,q.second) && abs(q.second-p.second)>.001f)
                    xs+=p.first+(q.first-p.first)*(y-p.second)/(q.second-p.second)
            }
            if(xs.isEmpty()) continue
            blocked+=Interval(xs.min()-pad,xs.max()+pad)
            }
            val merged=ArrayList<Interval>()
            for(band in blocked.sortedBy { it.left }) {
                val last=merged.lastOrNull()
                if(last!=null && band.left<=last.right) merged[merged.lastIndex]=Interval(last.left,max(last.right,band.right))
                else merged+=band
            }
            for(band in merged) {
            val l=band.left;val r=band.right
            slots=slots.flatMap { slot ->
                if(r<=slot.left || l>=slot.right) listOf(slot) else buildList {
                    if(l>slot.left) add(Interval(slot.left,min(l,slot.right)))
                    if(r<slot.right) add(Interval(max(r,slot.left),slot.right))
                }
            }
            }
        }
        return slots.filter { it.width>0f }
    }
}
