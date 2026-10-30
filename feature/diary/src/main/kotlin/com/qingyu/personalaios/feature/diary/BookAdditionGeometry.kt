package com.qingyu.personalaios.feature.diary

/** One addition: expose the right layer, turn quickly, then settle the whole rail. */
internal object BookAdditionGeometry {
    const val DurationMillis=420
    private fun smooth(value:Float)=value.coerceIn(0f,1f).let { it*it*(3-2*it) }
    private fun curve(value:Float,start:Float,end:Float,startSpeed:Float,endSpeed:Float):Float {
        val t=value.coerceIn(0f,1f);val t2=t*t;val t3=t2*t
        return (2*t3-3*t2+1)*start+(t3-2*t2+t)*startSpeed+
            (-2*t3+3*t2)*end+(t3-t2)*endSpeed
    }
    fun reveal(time:Float)=smooth(time/.16f)
    fun turn(time:Float):Float = when {
        time<=.16f -> 0f
        time<.39f -> curve((time-.16f)/.23f,0f,.5f,0f,.23f*3.4f)
        time<.72f -> curve((time-.39f)/.33f,.5f,1f,.33f*3.4f,0f)
        time<.84f -> 1f+.025f*smooth((time-.72f)/.12f)
        else -> 1f+.025f*(1-smooth((time-.84f)/.16f))
    }
}
