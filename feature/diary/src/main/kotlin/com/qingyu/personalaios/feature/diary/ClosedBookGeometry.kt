package com.qingyu.personalaios.feature.diary

import kotlin.math.*

internal data class ClosedBookRect(val left:Float,val top:Float,val width:Float,val height:Float,val scale:Float=1f) {
    val right get()=left+width
    val bottom get()=top+height
    fun contains(x:Float,y:Float)=x in left..right && y in top..bottom
}

/** Screen-space path ported from the supplied HTML; this object renders nothing.
 * Shared bottom, .85 side scale and staggered lift windows
 * feed BookShelfGeometry's rigid Vulkan poses and the cover editor's bounds.
 */
internal object ClosedBookGeometry {
    private fun clamp(x:Float)=x.coerceIn(0f,1f)
    fun base(width:Float,height:Float,scene:BookScene):ClosedBookRect {
        val quad=BookLibraryGeometry.coverQuad(width,height,scene.copy(open=0f),BookLibraryGeometry.pose(0,0f))
        return ClosedBookRect(quad.left,quad.top,quad.right-quad.left,quad.bottom-quad.top)
    }
    fun scale(delta:Int,offset:Float):Float {
        val p=abs(offset/BookCarouselMotion.Step)
        val direction=if(offset<0) 1 else -1
        return when(delta) {
            0 -> 1-.15f*(1-(1-clamp(p/.38f)).pow(3))
            direction -> {
                val u=clamp((p-.45f)/.50f)
                // Delayed lift with a soft start and zero landing slope. The
                // outgoing book settles first; peers remain on the desk.
                .85f+.15f*(1-(1-u).pow(4)*(1+4*u))
            }
            else -> .85f
        }
    }
    @JvmOverloads fun rect(delta:Int,width:Float,height:Float,scene:BookScene,base:ClosedBookRect=base(width,height,scene)):ClosedBookRect {
        val creating=scene.creationFrom!=null && scene.creationFrom.id!=scene.journal.id && scene.creationProgress<1f
        if(creating) {
            val pose=BookCreationMotion.creation(delta,scene.creationProgress)
            // Preserve the archived path, timing and vertical rebound, while keeping the
            // physical cover aspect ratio: independent X stretching caused the distortion.
            val scale=pose.sy
            return ClosedBookRect(base.left+(pose.x+(pose.sx-scale)/2)*base.width,base.top+pose.y*base.height,base.width*scale,base.height*scale,scale)
        }
        val offset=BookRemovalGeometry.offset(delta,scene)
        val slot=if(creating && delta<0) delta+1 else delta
        val scale=scale(slot,offset)
        val x=base.left+base.width/2+(slot+offset/BookCarouselMotion.Step)*base.width*BookCarouselMotion.Spacing
        return ClosedBookRect(x-base.width*scale/2,base.bottom-base.height*scale,base.width*scale,base.height*scale,scale)
    }
}
