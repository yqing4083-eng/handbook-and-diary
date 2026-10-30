package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Project the same world-space page planes as the renderer, only when a user taps. */
internal object BookPagePicker {
    data class Hit(val index:Int,val side:Int)
    fun pick(x: Float, y: Float, width: Float, height: Float, current: Int, count: Int, selected: Boolean,portrait:Boolean=false,centreY:Float=BookLibraryGeometry.centreY(height,portrait)):Int? =
        pickFace(x,y,width,height,current,count,selected,portrait,centreY)?.index
    fun pickFace(x: Float, y: Float, width: Float, height: Float, current: Int, count: Int, selected: Boolean,portrait:Boolean=false,centreY:Float=BookLibraryGeometry.centreY(height,portrait)): Hit? {
        if(width<=0 || height<=0) return null
        for(delta in listOf(0,-1,1,-2,2,-3,3)) {
            val index=current+delta
            if(index !in 0 until count) continue
            for(side in listOf(-1,1)) {
            val h=BookGeometry.HalfHeight
            val quad=listOf(0f to h, side*BookGeometry.HalfWidth to h, side*BookGeometry.HalfWidth to -h, 0f to -h).map { (px,py) ->
                BookGeometry.project(px,py,side,delta,selected,width,height,portrait,centreY)
            }
            var positive=false; var negative=false
            quad.indices.forEach { i ->
                val a=quad[i];val b=quad[(i+1)%4]
                val cross=(b.first-a.first)*(y-a.second)-(b.second-a.second)*(x-a.first)
                if(cross>0) positive=true else if(cross<0) negative=true
            }
            if(!positive || !negative) return Hit(index,side)
            }
        }
        return null
    }
}
