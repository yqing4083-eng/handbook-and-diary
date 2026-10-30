package com.qingyu.personalaios.feature.me

/** One linear scroll coordinate drives the avatar, title and sticky end pose. */
internal object SpaceScrollMotion {
    const val CollapseDp=148
    fun progress(index:Int,offset:Float,travel:Float)=if(index>0) 1f else (offset/travel.coerceAtLeast(1f)).coerceIn(0f,1f)
    fun snap(offset:Float,travel:Float):Float=when {
        offset<=0f || offset>=travel -> offset
        offset<travel*.45f -> 0f
        else -> travel
    }
    data class AvatarPose(val width:Float,val height:Float,val rise:Float,val drift:Float,val neck:Float,val alpha:Float)
    fun avatar(progress:Float):AvatarPose {
        val p=progress.coerceIn(0f,1f)
        fun smooth(v:Float)=v.coerceIn(0f,1f).let { it*it*(3-2*it) }
        // Reference: the round photo rises first, then forms a narrow neck and
        // lower lobe, before contracting into the top. Retrace on reverse scroll.
        val neck=smooth((p-.18f)/.44f)*(1-smooth((p-.83f)/.17f))
        val scale=1f-.68f*p
        val fade=smooth((p-.72f)/.28f)
        return AvatarPose(scale*(1-.12f*neck),scale+.24f*neck,p,0f,neck,1-fade)
    }
}
