package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Fixed extruded outline buffers, shared by the cover, spine and page block. */
internal class BookPlane(val width: Float, val height: Float, radius: Float = .05f, straightEdge: Int = 0) {
    val perimeter: List<Pair<Float, Float>> = buildList {
        val centers = listOf((width/2-radius) to (height/2-radius), (-width/2+radius) to (height/2-radius),
            (-width/2+radius) to (-height/2+radius), (width/2-radius) to (-height/2+radius))
        centers.forEachIndexed { corner, c ->
            val straight = (straightEdge>0 && corner in listOf(0,3)) || (straightEdge<0 && corner in listOf(1,2))
            if(straight) add((if(corner in listOf(0,3)) width/2 else -width/2) to (if(corner<2) height/2 else -height/2))
            else repeat(25) { step ->
            val a = (corner * 90 + step * 90f/24) * PI / 180
            fun curve(v: Double) = sign(v) * sqrt(abs(v))
            add((c.first + radius * curve(cos(a)).toFloat()) to (c.second + radius * curve(sin(a)).toFloat()))
        } }
    }
}
