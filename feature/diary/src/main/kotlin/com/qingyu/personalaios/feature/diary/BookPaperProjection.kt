package com.qingyu.personalaios.feature.diary

import kotlin.math.*
import com.qingyu.personalaios.domain.PaperFace

/** Same rigid panel, outward face and off-axis camera as albumPanel in Vulkan.
 * The old editor used a different fan rail and an axis-aligned bounding box. */
internal object BookPaperProjection {
    fun face(current:Int,side:PaperFace,width:Float,height:Float,centreY:Float):BookQuad {
        val back=side==PaperFace.LEFT
        val pose=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(if(back) current-1 else current,current,0f,1),back)
        val a=pose.angle*PI.toFloat()/180f;val face=PaperMaterial.Thickness/2*(if(back) -1 else 1)
        val xs=if(back) floatArrayOf(BookGeometry.HalfWidth,0f,0f,BookGeometry.HalfWidth) else floatArrayOf(0f,BookGeometry.HalfWidth,BookGeometry.HalfWidth,0f)
        val scene=BookScene(open=1f,portrait=true,openCentreY=centreY)
        val focal=BookGeometry.focal(width,height,true)
        return BookQuad(FloatArray(8).also { out->for(i in 0..3) {
            val x=xs[i];val y=if(i<2) BookGeometry.HalfHeight else -BookGeometry.HalfHeight
            val wx=pose.hinge.x+x*cos(a)+face*sin(a)
            val z=pose.hinge.z-x*sin(a)+face*cos(a)
            out[i*2]=width/2+wx*focal/(BookGeometry.cameraDistance()-z)
            out[i*2+1]=BookCameraGeometry.projectY(y+pose.hinge.y,z,width,height,scene)
        } })
    }
    fun interpolate(source:BookQuad,target:PaperRect,progress:Float):BookQuad {
        val p=progress.coerceIn(0f,1f)
        val end=floatArrayOf(target.left,target.top,target.left+target.width,target.top,target.left+target.width,target.top+target.height,target.left,target.top+target.height)
        return BookQuad(FloatArray(8) { source.points[it]+(end[it]-source.points[it])*p })
    }
}
