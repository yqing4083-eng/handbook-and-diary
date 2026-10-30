package com.qingyu.personalaios.feature.diary

import kotlin.math.*

internal data class OverviewSlot(val left:Float,val top:Float,val width:Float,val height:Float) {
    val centreX get()=left+width/2
    val centreY get()=top+height/2
}

/** Screen layout and rigid poses share one coordinate system, including hit
 * targets. Each cell is a complete spread; its two halves keep their thickness. */
internal object BookOverviewGeometry {
    const val DurationMillis=820
    private fun smooth(x:Float)=x.coerceIn(0f,1f).let { it*it*(3-2*it) }
    private fun delay(index:Int,current:Int)=min(abs(index-current),6)*.012f
    private fun mix(a:Float,b:Float,t:Float)=a+(b-a)*t
    private fun hermite(t:Float,a:Float,b:Float,va:Float,vb:Float,span:Float):Float {
        val t2=t*t;val t3=t2*t
        return (2*t3-3*t2+1)*a+(t3-2*t2+t)*va*span+
            (-2*t3+3*t2)*b+(t3-t2)*vb*span
    }
    // Measured reference: a quick column entry, ~9.5% positional overshoot,
    // then one quiet recovery. C1 joins keep both entry and reversal continuous.
    // This is a reconstruction of the visible trajectory, not source easing data.
    private fun columnResponse(t:Float):Float = when {
        t<=0f -> 0f
        t<.08f -> hermite(t/.08f,0f,.24f,0f,4f,.08f)
        t<.28f -> hermite((t-.08f)/.20f,.24f,.95f,4f,1.8f,.20f)
        t<.52f -> hermite((t-.28f)/.24f,.95f,1.095f,1.8f,0f,.24f)
        t<1f -> hermite((t-.52f)/.48f,1.095f,1f,0f,0f,.48f)
        else -> 1f
    }
    fun slot(index:Int,width:Float,height:Float,anchor:Int,scroll:Float=0f,top:Float=Float.NaN):OverviewSlot {
        val cell=width*.415f
        val cellHeight=cell*BookGeometry.HalfHeight/BookGeometry.HalfWidth
        val gap=width*.039f
        return OverviewSlot(width*.0655f+(index%2)*(cell+gap),
            (if(top.isFinite()) top+(index/2)*(cellHeight+gap) else height*.5f-cellHeight/2+(index/2-anchor/2)*(cellHeight+gap))+scroll,cell,cellHeight)
    }
    fun scrollBounds(width:Float,height:Float,count:Int,anchor:Int,top:Float=Float.NaN):ClosedFloatingPointRange<Float> {
        if(count<=0) return 0f..0f
        val first=slot(0,width,height,anchor,top=top)
        val last=slot(count-1,width,height,anchor,top=top)
        return min(0f,height*.84f-last.top-last.height)..(if(top.isFinite()) 0f else max(0f,height*.32f-first.top))
    }
    fun visible(width:Float,height:Float,count:Int,anchor:Int,scroll:Float,top:Float=Float.NaN):IntRange {
        if(count==0) return IntRange.EMPTY
        val cell=slot(0,width,height,anchor,scroll,top)
        val step=cell.height+width*.039f
        val first=(floor((-cell.top-cell.height)/step).toInt()-1).coerceAtLeast(0)*2
        val end=((ceil((height-cell.top)/step).toInt()+1)*2-1).coerceAtMost(count-1)
        return if(first>end) IntRange.EMPTY else first..end
    }
    fun travel(progress:Float,index:Int,current:Int):Float {
        return smooth((progress-.16f-delay(index,current))/.46f)
    }
    fun flatten(progress:Float)=smooth(progress/.28f)
    fun flatten(progress:Float,index:Int,current:Int)=if(index==current) flatten(progress) else
        smooth((progress-.08f-delay(index,current))/.42f)
    fun rowTravel(progress:Float,index:Int,current:Int)=smooth((progress-.32f-delay(index,current))/.34f)
    fun columnTravel(progress:Float,index:Int,current:Int)=columnResponse((progress-.65f-delay(index,current))/.26f)
    fun scale(progress:Float,index:Int,current:Int,target:Float)=1f+(target-1f)*travel(progress,index,current)
    fun shadowAlpha(progress:Float,index:Int,current:Int)=mix(if(index==current) .52f else .14f,.32f,
        smooth(progress/.62f))
    // A shallow common tilt reads as a floating sheet without separating its halves.
    fun pitch(progress:Float)=0f
    fun shadowReceiverTravel(progress:Float)=smooth(progress)
    fun panel(source:FoldPanelPose,back:Boolean,index:Int,scene:BookScene,width:Float,height:Float,
        companion:FoldPanelPose=source):FoldPanelPose {
        if(scene.overview<=0f) return source
        val slot=slot(index,width,height,scene.overviewAnchor,scene.overviewScroll,scene.overviewTop)
        val focal=BookGeometry.focal(width,height,scene.portrait,scene.open)
        val camera=BookGeometry.cameraDistance(scene.open)
        val q=travel(scene.overview,index,scene.current)
        val flatten=flatten(scene.overview,index,scene.current)
        val angle=if(back) -180f else 0f
        // Pair the two halves before they leave the stack. They share a hinge,
        // uniform scale and destination, so a complete artwork never tears apart.
        val origin=PagePose(mix(source.hinge.x,(source.hinge.x+companion.hinge.x)/2,flatten),
            mix(source.hinge.y,(source.hinge.y+companion.hinge.y)/2,flatten),
            mix(source.hinge.z,(source.hinge.z+companion.hinge.z)/2,flatten))
        val lag=delay(index,scene.current)
        // Hidden backs rotate behind the current paper, but face front before
        // the columns separate. The previous late rotation exposed white walls.
        val retreat=if(index==scene.current) 0f else 1.18f*smooth(scene.overview/.18f)*
            (1-smooth((scene.overview-.32f-lag)/.24f))
        // Flat pages still overlap before column entry. Keep a unique, thin
        // layer for each complete spread until it separates; equal Z values
        // here would flicker or let a later page cover the selected artwork.
        val rank=abs(index-scene.current)*2+if(index>scene.current) 1 else 0
        val stackDepth=if(index==scene.current) 0f else .014f*rank*flatten*
            (1-smooth((scene.overview-.65f-lag)/.26f))
        val dragged=scene.overviewDragId!=null && scene.pages.getOrNull(index)?.id==scene.overviewDragId
        val z=mix(origin.z,if(dragged) .08f else .03f,q)-retreat-stackDepth
        val row=rowTravel(scene.overview,index,scene.current)
        val lower=width*.10f*sin(PI.toFloat()*(scene.overview/.66f).coerceIn(0f,1f)).pow(2)*(1-row)
        val startX=width/2+origin.x*focal/(camera-origin.z)
        val startY=BookCameraGeometry.projectY(origin.y,origin.z,width,height,scene)
        val screenX=mix(startX,slot.centreX,columnTravel(scene.overview,index,scene.current))+(if(dragged) scene.overviewDragX else 0f)
        val screenY=mix(startY,slot.centreY,row)+lower+(if(dragged) scene.overviewDragY else 0f)
        // Resolve the independently timed screen axes at the live depth. A Z
        // retreat must not accidentally lower a row or start its column early.
        return FoldPanelPose(PagePose((screenX-width/2)*(camera-z)/focal,
            (BookCameraGeometry.principalY(height,scene)-screenY)*(camera-z)/focal-
                BookLibraryGeometry.cameraBaseY(width,height,scene),z),mix(source.angle,angle,flatten))
    }
    fun targetScale(width:Float,height:Float,scene:BookScene):Float =
        width*.415f*(BookGeometry.cameraDistance(scene.open)-.03f)/
            BookGeometry.focal(width,height,scene.portrait,scene.open)/(2*BookGeometry.HalfWidth)
}
