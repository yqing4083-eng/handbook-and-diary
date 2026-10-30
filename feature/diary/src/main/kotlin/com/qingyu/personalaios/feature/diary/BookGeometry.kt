package com.qingyu.personalaios.feature.diary

import kotlin.math.*

/** Rigid half-panels: the shallow centre V comes from angle and perspective. */
internal object BookGeometry {
    const val HalfWidth = 1.235f
    // 720x1556 reference: 510px spread, 428px height, each half is portrait.
    const val HalfHeight = .914f
    const val RestAngle = 25f
    const val FanSpacing = .12f
    const val FanDepth = .065f
    const val ClosedPitch = 0f
    const val ClosedYaw = 0f
    const val CoverWidth = 1.25f
    const val CoverHeight = 1.84f
    // A physical leaf keeps its dimensions. Phone orientation changes the viewport, not the book.
    fun heightScale(portrait: Boolean,open:Float=1f) = 1f
    fun cameraDistance(open:Float=1f) = 5f + 3.2f*BookOpeningGeometry.spread(open)
    fun restingOuterZ()=.03f+HalfWidth*sin(RestAngle*PI.toFloat()/180)+PaperMaterial.Thickness/2*cos(RestAngle*PI.toFloat()/180)
    fun focal(width:Float,height:Float,portrait:Boolean,open:Float=1f):Float {
        val spread=BookOpeningGeometry.spread(open)
        // Keep the closed carousel camera unchanged. Open-panel depth naturally
        // raises the free corners; no shader pinch is used to imitate perspective.
        // The thicker front board moved the visible plane toward the camera.
        // Retain the calibrated closed cover size while projecting its real face.
        val closedCalibration=1f-(1f-BookLibraryGeometry.ClosedViewDepth/4.51f)*(1-spread)
        return min(width*(if(portrait) 2.47f-.952f*spread else .86f+.148f*spread),height*if(portrait) 2f else 1.55f) *
            (cameraDistance(open)-.03f)/4.97f*closedCalibration
    }
    fun angle(side: Int, open: Float, delta: Int = 0): Float =
        if (side < 0) 180f * (1 - open) + AccordionGeometry.fold(delta.toFloat()) * open
        else -AccordionGeometry.fold(delta.toFloat()) * open
    fun turningAngle(side: Int, progress: Float): Float = -side * (RestAngle + (180 - 2 * RestAngle) * progress)
    fun textureStart(side:Int,back:Boolean)=if(back && side<0) 1f else if(side>0) .5f else 0f
    fun textureEnd(side:Int,back:Boolean)=if(back) { if(side>0) 0f else .5f } else { if(side>0) 1f else .5f }
    fun project(x: Float, y: Float, side: Int, delta: Int, selected: Boolean, width: Float, height: Float,portrait:Boolean=false,centreY:Float=BookLibraryGeometry.centreY(height,portrait)): Pair<Float, Float> {
        return projectLayer(x,y,angle(side,1f,delta),delta.toFloat(),if(selected) 1f else 0f,width,height,portrait,centreY)
    }
    /** Projects the same 3D fan placement as the GPU renderer for hit testing. */
    fun projectLayer(x:Float,y:Float,angle:Float,delta:Float,selection:Float,width:Float,height:Float,portrait:Boolean,centreY:Float):Pair<Float,Float> {
        val yaw = angle * PI.toFloat() / 180
        val pose=BookPageMotion.pose(delta,selection)
        val faceZ=PaperMaterial.Thickness/2
        val wx = x * cos(yaw) + faceZ*sin(yaw) + pose.x
        val wy = y + pose.y
        val wz = -x * sin(yaw) + faceZ*cos(yaw) + pose.z
        val depth = cameraDistance() - wz
        val focal = focal(width,height,portrait)
        val scene=BookScene(open=1f,portrait=portrait,openCentreY=centreY)
        return (width / 2 + wx * focal / depth) to BookCameraGeometry.projectY(wy,wz,width,height,scene)
    }
}
