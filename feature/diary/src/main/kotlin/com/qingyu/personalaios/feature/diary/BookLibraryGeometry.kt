package com.qingyu.personalaios.feature.diary

import kotlin.math.*

internal data class LibraryPose(val x:Float=0f,val y:Float=0f,val z:Float=0f,
    val pitch:Float=BookGeometry.ClosedPitch,val yaw:Float=BookGeometry.ClosedYaw,
    val roll:Float=0f,val scale:Float=1f)

/** World-space carousel. Both neighbouring and selected books use the same pose. */
internal object BookLibraryGeometry {
    const val TablePitch=6f
    const val TableDepth=-.635f
    const val HoverHeight=.24f
    const val HoverDepth=.26f
    val ClosedViewDepth get()=5f-HoverDepth-BookBoardMaterial.closedFrontFace
    fun tableZ(y:Float)=TableDepth+tan(TablePitch*PI.toFloat()/180)*(y+1f)
    // The rear paper-block face is 0.02 world units in front of the model origin.
    // Match its entire plane, including the pitch-induced y offset, to the desk.
    fun restingDepth(y:Float)=tableZ(y)-.02f/cos(TablePitch*PI.toFloat()/180)
    const val Spacing=1.51f
    const val DragScale=1.7f
    const val Step=BookCarouselMotion.Step
    private fun smooth(x:Float)=x.coerceIn(0f,1f).let { it*it*(3-2*it) }
    fun pose(delta:Int,offset:Float):LibraryPose {
        val distance=delta+offset/Step
        val side=smooth(abs(distance)/.30f)
        val z=HoverDepth+(restingDepth(.08f)-HoverDepth)*side
        // Compensate perspective translation, never scale the mesh to fake depth.
        // A book on the desk follows exactly the same screen-space drag as its peers.
        val x=distance*BookGeometry.CoverWidth*BookCarouselMotion.Spacing*(5f-BookBoardMaterial.closedFrontFace-z)/ClosedViewDepth
        return LibraryPose(x=x,y=HoverHeight-.16f*side,z=z,
            pitch=BookGeometry.ClosedPitch+(TablePitch-BookGeometry.ClosedPitch)*side,
            yaw=BookGeometry.ClosedYaw)
    }
    fun oldBookShift(progress:Float)=smooth(progress/.46f)
    fun arrival(progress:Float):LibraryPose {
        val p=progress.coerceIn(0f,1f)
        if(p==1f) return pose(0,0f)
        // Rigid arrival: the incoming cover has its final dimensions and depth at
        // every frame. Motion is a lower-right arc, then a small vertical rebound.
        // No X/Y squash, scale overshoot, camera zoom or near-plane distortion.
        val travel=smooth((p-.16f)/.42f)
        val rebound=when {
            p<.58f -> .30f*travel
            else -> .30f*exp(-7f*(p-.58f))*(cos((p-.58f)*15f)+7f/15f*sin((p-.58f)*15f))*smooth((1-p)/.12f)
        }
        return LibraryPose(x=3f*(1-travel),y=HoverHeight-2f*(1-travel)+rebound,z=HoverDepth)
    }
    fun creationPose(delta:Int,progress:Float):LibraryPose = when {
        progress>=1f -> pose(delta,0f)
        delta==0 -> arrival(progress)
        delta<0 -> pose(delta+1,-oldBookShift(progress)*Step)
        else -> pose(delta,0f)
    }
    fun controlsAlpha(offset:Float,creation:Float?=null):Float =
        if(creation!=null) 1f else BookCarouselMotion.controls(offset)
    fun centreY(height:Float,portrait:Boolean)=height*if(portrait) AccordionGeometry.ScreenCentre else .5f
    fun cameraBaseY(width:Float,height:Float,scene:BookScene):Float = BookCameraGeometry.baseY(width,height,scene)
    fun cameraBaseX(width:Float,height:Float,scene:BookScene):Float = 0f
    /** Project the actual closed cover corner, so controls follow the book on any viewport. */
    fun coverTopRight(width:Float,height:Float,portrait:Boolean,closedCentreY:Float=Float.NaN):Pair<Float,Float> {
        val scene=BookScene(portrait=portrait,closedCentreY=closedCentreY)
        val focal=BookGeometry.focal(width,height,portrait,0f)
        val baseY=cameraBaseY(width,height,scene)
        val pitch=BookGeometry.ClosedPitch*PI.toFloat()/180
        val yaw=BookGeometry.ClosedYaw*PI.toFloat()/180
        val x=BookGeometry.CoverWidth/2*cos(yaw)+BookBoardMaterial.closedFrontFace*sin(yaw)
        val z=-BookGeometry.CoverWidth/2*sin(yaw)+BookBoardMaterial.closedFrontFace*cos(yaw)
        val y=HoverHeight+BookGeometry.CoverHeight/2*cos(pitch)-z*sin(pitch)
        val depth=5f-(HoverDepth+BookGeometry.CoverHeight/2*sin(pitch)+z*cos(pitch))
        return (width/2+(cameraBaseX(width,height,scene)+x)*focal/depth) to (height/2-(baseY+y)*focal/depth)
    }
    fun pick(x:Float,y:Float,width:Float,height:Float,scene:BookScene):Int? {
        val current=scene.journals.indexOfFirst { it.id==scene.journal.id }.coerceAtLeast(0)
        for(delta in listOf(0,-1,1)) {
            val index=current+delta
            if(index !in scene.journals.indices) continue
            val quad=coverQuad(width,height,scene,pose(delta,scene.libraryOffset))
            if(quad.contains(x,y)) return index
        }
        return null
    }
    /** Exactly the same rigid transform order as SolidBookRenderer.closedBook. */
    fun coverQuad(width:Float,height:Float,scene:BookScene,pose:LibraryPose):BookQuad {
        val focal=BookGeometry.focal(width,height,scene.portrait,0f)
        val baseX=cameraBaseX(width,height,scene);val baseY=cameraBaseY(width,height,scene)
        val yaw=pose.yaw*PI.toFloat()/180;val pitch=pose.pitch*PI.toFloat()/180;val roll=pose.roll*PI.toFloat()/180
        val coords=floatArrayOf(-.625f,.92f,.625f,.92f,.625f,-.92f,-.625f,-.92f)
        return BookQuad(FloatArray(8).also { out -> for(i in 0..3) {
            val x=coords[i*2]*pose.scale;val y=coords[i*2+1]*pose.scale;val z=BookBoardMaterial.closedFrontFace*pose.scale
            val xx=x*cos(yaw)+z*sin(yaw);val zz=-x*sin(yaw)+z*cos(yaw)
            val yy=y*cos(pitch)-zz*sin(pitch);val pz=y*sin(pitch)+zz*cos(pitch)
            val px=xx*cos(roll)-yy*sin(roll);val py=xx*sin(roll)+yy*cos(roll)
            val depth=5-pose.z-pz
            out[i*2]=width/2+(baseX+pose.x+px)*focal/depth
            out[i*2+1]=height/2-(baseY+pose.y+py)*focal/depth
        } })
    }
}

internal data class BookQuad(val points:FloatArray) {
    val left get()=minOf(points[0],points[2],points[4],points[6])
    val right get()=maxOf(points[0],points[2],points[4],points[6])
    val top get()=minOf(points[1],points[3],points[5],points[7])
    val bottom get()=maxOf(points[1],points[3],points[5],points[7])
    fun contains(x:Float,y:Float):Boolean {
        var positive=false;var negative=false
        for(i in 0..3) {
            val j=(i+1)%4
            val cross=(points[j*2]-points[i*2])*(y-points[i*2+1])-(points[j*2+1]-points[i*2+1])*(x-points[i*2])
            if(cross>0f) positive=true else if(cross<0f) negative=true
        }
        return !positive || !negative
    }
}
