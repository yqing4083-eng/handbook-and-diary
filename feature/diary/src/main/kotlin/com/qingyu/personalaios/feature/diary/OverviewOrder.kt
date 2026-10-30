package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.DiaryPage
import kotlin.math.*

internal object OverviewOrder {
    fun move(pages:List<DiaryPage>,id:String,target:Int):List<DiaryPage> {
        val from=pages.indexOfFirst { it.id==id }
        if(from<0 || pages.isEmpty()) return pages
        return pages.toMutableList().apply { val page=removeAt(from);add(target.coerceIn(0,size),page) }
    }
    fun target(x:Float,y:Float,width:Float,height:Float,count:Int,anchor:Int,scroll:Float,top:Float):Int {
        if(count<=0) return 0
        val first=BookOverviewGeometry.slot(0,width,height,anchor,scroll,top)
        val row=((y-first.centreY)/(first.height+width*.039f)).roundToInt().coerceAtLeast(0)
        return (row*2+if(x<width/2) 0 else 1).coerceIn(0,count-1)
    }
}
