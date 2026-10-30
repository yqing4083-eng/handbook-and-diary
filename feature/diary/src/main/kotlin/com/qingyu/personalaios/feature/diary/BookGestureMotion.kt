package com.qingyu.personalaios.feature.diary

internal object BookGestureMotion {
    const val TurnGain=2.2f
    const val ReleaseMillis=360
    // Signed progress retains the interrupted direction when grabbing a spring.
    fun turn(origin:Float,offset:Float)=origin-offset*TurnGain
    fun commits(progress:Float,velocity:Float)=when {
        velocity< -1.1f -> false
        velocity>1.1f -> progress>.035f
        else -> progress>.32f
    }
    fun closing(scale:Float)=((1-scale)*1.7f).coerceIn(0f,BookClosingGeometry.PackedAt)
    fun commitClose(clock:Float)=clock>.22f
}
