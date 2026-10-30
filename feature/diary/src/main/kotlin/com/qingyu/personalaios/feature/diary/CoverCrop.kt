package com.qingyu.personalaios.feature.diary

internal data class CoverCrop(val zoom:Float=1f,val x:Float=0f,val y:Float=0f) {
    fun constrained(imageAspect:Float,bookAspect:Float=2f/3):CoverCrop {
        val z=zoom.coerceIn(1f,5f)
        val w=maxOf(1f,imageAspect/bookAspect)*z
        val h=maxOf(1f,bookAspect/imageAspect)*z
        return CoverCrop(z,x.coerceIn(-(w-1)/2,(w-1)/2),y.coerceIn(-(h-1)/2,(h-1)/2))
    }
}
