package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.core.glass.LiquidMotion

internal data class BookSprite(val x:Float=0f,val y:Float=0f,val sx:Float=1f,val sy:Float=1f,val alpha:Float=1f,val hover:Float=1f)
/** Measured reference: land, move aside, short accelerating fall, lift and settle.
 * Depth changes use a single uniform scale; the cover aspect ratio never changes.
 */
internal object BookCreationMotion {
    private fun rest(delta:Int)=if(delta==0) BookSprite() else BookSprite(x=delta*BookCarouselMotion.Spacing+.075f,y=.15f,sx=.85f,sy=.85f,hover=0f)
    fun creation(delta:Int,progress:Float):BookSprite {
        val p=progress.coerceIn(0f,1f)
        if(p==1f) return rest(delta)
        if(delta>0) return rest(delta)
        val shift=LiquidMotion.position(((p-.16f)/.30f).coerceIn(0f,1f)).coerceIn(0f,1f)
        if(delta==0) {
            if(p<.38f) return BookSprite(y=-1.75f,alpha=0f)
            if(p<.52f) {
                val fall=(p-.38f)/.14f
                val eased=fall*fall
                val scale=1f-.15f*eased
                return BookSprite(x=(1-scale)/2,y=-1.75f+1.90f*eased,sx=scale,sy=scale,hover=1-eased)
            }
            val rise=LiquidMotion.position((p-.52f)/.48f)
            val scale=.85f+.15f*rise
            return BookSprite(x=(1-scale)/2,y=.15f*(1-rise),sx=scale,sy=scale,hover=rise.coerceIn(0f,1f))
        }
        val from=if(delta==-1) {
            val land=LiquidMotion.position((p/.16f).coerceIn(0f,1f)).coerceIn(0f,1f)
            BookSprite(x=.075f*land,y=.15f*land,sx=1-.15f*land,sy=1-.15f*land,hover=1-land)
        } else rest(delta+1)
        val to=rest(delta)
        return BookSprite(x=from.x+(to.x-from.x)*shift,y=from.y+(to.y-from.y)*shift,
            sx=from.sx+(to.sx-from.sx)*shift,sy=from.sy+(to.sy-from.sy)*shift,
            hover=from.hover+(to.hover-from.hover)*shift)
    }
}
