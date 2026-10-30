package com.qingyu.personalaios.domain

/** Raw touch coordinates deliberately remain unclamped. Leaving the paper ends
 * a segment, so re-entering cannot draw a false line along its edge. */
data class InkSample(val x:Float,val y:Float,val pressure:Float=1f) {
    val inside get()=x in 0f..1f && y in 0f..1f
    fun point()=InkPoint(x.coerceIn(0f,1f),y.coerceIn(0f,1f),pressure.coerceIn(0f,2f))
}
class BoundedInkTrace {
    val paths=ArrayList<MutableList<InkPoint>>()
    private var previous:InkSample?=null
    private var active:MutableList<InkPoint>?=null
    fun clear() { paths.clear();previous=null;active=null }
    fun add(sample:InkSample) {
        if(!sample.x.isFinite() || !sample.y.isFinite()) return
        val a=previous;previous=sample
        if(a==null) {
            if(sample.inside) active=mutableListOf(sample.point()).also { paths.add(it) }
            return
        }
        var lo=0f;var hi=1f
        val dx=sample.x-a.x;val dy=sample.y-a.y
        fun plane(p:Float,q:Float):Boolean {
            if(p==0f) return q>=0f
            val r=q/p
            if(p<0) { if(r>hi) return false;lo=maxOf(lo,r) }
            else { if(r<lo) return false;hi=minOf(hi,r) }
            return true
        }
        if(!plane(-dx,a.x) || !plane(dx,1-a.x) || !plane(-dy,a.y) || !plane(dy,1-a.y)) { active=null;return }
        fun at(t:Float)=InkSample(a.x+dx*t,a.y+dy*t,a.pressure+(sample.pressure-a.pressure)*t).point()
        val start=at(lo);val end=at(hi)
        val path=active?.takeIf { a.inside } ?: mutableListOf(start).also { paths.add(it);active=it }
        if(path.last()!=end) path.add(end)
        if(!sample.inside) active=null
    }
}
