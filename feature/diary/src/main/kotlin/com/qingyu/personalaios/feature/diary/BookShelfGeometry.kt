package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Lift the existing carousel's measured screen path into a rigid 3D scene.
 * Depth, not independent axis scaling, gives every book its apparent size.
 * Tracking/release timing remains owned by BookCarouselMotion.
 */
internal object BookShelfGeometry {
    @JvmOverloads fun pose(delta:Int,width:Float,height:Float,scene:BookScene,base:ClosedBookRect=ClosedBookGeometry.base(width,height,scene)):LibraryPose {
        val rect=ClosedBookGeometry.rect(delta,width,height,scene,base)
        val scale=rect.scale
        val flat=((1-scale)/.15f).coerceIn(0f,1f)
        val pitch=BookLibraryGeometry.TablePitch*flat
        val radians=pitch*PI.toFloat()/180
        val depth=BookLibraryGeometry.ClosedViewDepth/scale
        val focal=BookGeometry.focal(width,height,scene.portrait,0f)
        val nearest=(1-abs(delta+BookRemovalGeometry.offset(delta,scene)/BookCarouselMotion.Step)*4).coerceIn(0f,1f)
        val recoil=scene.libraryRecoil*nearest
        // Compensate the unequal perspective depth of the upper/lower corners.
        // Keep the existing carousel's visual centre while the mesh stays rigid.
        val halfHeight=BookGeometry.CoverHeight/2
        val depthOffset=halfHeight*sin(radians)
        val projectedDepth=(depth*depth-depthOffset*depthOffset)/depth
        val centreY=(height/2-rect.top-rect.height/2)*projectedDepth/focal-
            halfHeight*cos(radians)*depthOffset/depth
        return LibraryPose(
            x=(rect.left+rect.width/2-width/2)*projectedDepth/focal,
            y=centreY-BookLibraryGeometry.cameraBaseY(width,height,scene.copy(open=0f))+BookBoardMaterial.closedFrontFace*sin(radians)+recoil*.008f,
            z=5f-depth-BookBoardMaterial.closedFrontFace*cos(radians)+recoil*.012f,
            pitch=pitch,scale=1f)
    }
    fun pick(x:Float,y:Float,width:Float,height:Float,scene:BookScene):Int? {
        val current=scene.journals.indexOfFirst { it.id==scene.journal.id }.coerceAtLeast(0)
        for(delta in listOf(0,-1,1)) if(current+delta in scene.journals.indices &&
            BookLibraryGeometry.coverQuad(width,height,scene,pose(delta,width,height,scene)).contains(x,y)) return current+delta
        return null
    }
}
