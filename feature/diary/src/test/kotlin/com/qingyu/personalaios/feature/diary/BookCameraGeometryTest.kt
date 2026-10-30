package com.qingyu.personalaios.feature.diary

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class BookCameraGeometryTest {
    @Test fun everyVerticalPaperEdgeKeepsItsScreenMidpointThroughoutEitherPull() {
        // An off-centre viewport used to turn common Z retreat into Y drift.
        // Test the actual rigid panel corners, not just their world-space Y=0.
        for ((width,height) in listOf(1080f to 2138f,2400f to 942f))
            for (centre in listOf(height*.37f,height*.5f,height*.61f)) {
                val scene=BookScene(open=1f,portrait=height>width,openCentreY=centre)
                for (direction in listOf(-1,1)) for (step in 0..1000) for (leaf in -1..9) {
                    val pose=AlbumLeafGeometry.pose(leaf,4,step/1000f,direction)
                    for (back in listOf(false,true)) {
                        val panel=AlbumLeafGeometry.panel(pose,back)
                        val tip=AlbumLeafGeometry.foldEdge(panel)
                        for (point in listOf(panel.hinge,tip)) {
                            val top=BookCameraGeometry.projectY(BookGeometry.HalfHeight,point.z,width,height,scene)
                            val bottom=BookCameraGeometry.projectY(-BookGeometry.HalfHeight,point.z,width,height,scene)
                            assertEquals(centre,(top+bottom)/2,.0003f)
                        }
                    }
                }
            }
    }

    @Test fun openingKeepsTheCalibratedReferencePlaneAtTheInterpolatedLayoutCentre() {
        for ((w,h) in listOf(1080f to 2138f,2400f to 942f)) for (step in 0..1000) {
            val scene=BookScene(open=step/1000f,portrait=h>w,closedCentreY=h*.54f,openCentreY=h*.42f)
            val s=BookOpeningGeometry.spread(scene.open)
            val y=BookLibraryGeometry.HoverHeight*(1-s)
            val z=(BookLibraryGeometry.HoverDepth+BookBoardMaterial.closedFrontFace)*(1-s)+BookGeometry.restingOuterZ()*s
            val expected=scene.closedCentreY+(scene.openCentreY-scene.closedCentreY)*s
            assertEquals(expected,BookCameraGeometry.projectY(y,z,w,h,scene),.0003f)
        }
    }

    @Test fun closedShelfCameraIsUnchangedForCustomInsetsAndBothOrientations() {
        for ((w,h) in listOf(1080f to 2138f,2400f to 942f)) for (centre in listOf(h*.4f,h*.513f,h*.6f)) {
            val scene=BookScene(portrait=h>w,closedCentreY=centre)
            val expected=-(centre-h/2)*BookLibraryGeometry.ClosedViewDepth/BookGeometry.focal(w,h,scene.portrait,0f)-BookLibraryGeometry.HoverHeight
            assertEquals(h/2,BookCameraGeometry.principalY(h,scene),0f)
            assertEquals(expected,BookCameraGeometry.baseY(w,h,scene),.000001f)
        }
    }

    @Test fun paperPickingAndEditorBoundsUseTheSameOpenProjection() {
        for ((w,h) in listOf(1080f to 2138f,2400f to 942f)) for (centre in listOf(h*.4f,h*.513f,h*.6f)) {
            for (delta in -4..4) for (x in listOf(0f,BookGeometry.HalfWidth)) {
                val top=BookGeometry.project(x,BookGeometry.HalfHeight,1,delta,false,w,h,h>w,centre)
                val bottom=BookGeometry.project(x,-BookGeometry.HalfHeight,1,delta,false,w,h,h>w,centre)
                assertEquals(centre,(top.second+bottom.second)/2,.0003f)
                assertEquals(top.first,bottom.first,0f)
            }
            assertEquals(4,BookPagePicker.pick(w/2,centre,w,h,4,9,false,h>w,centre))
        }
    }

    @Test fun projectedPaperHeightStillComesFromDepthWithoutResizingTheMesh() {
        val w=1080f;val h=2138f;val scene=BookScene(open=1f,openCentreY=900f)
        for (z in listOf(-2.67f,-.59f,.03f,.65f,1.2f)) {
            val top=BookCameraGeometry.projectY(BookGeometry.HalfHeight,z,w,h,scene)
            val bottom=BookCameraGeometry.projectY(-BookGeometry.HalfHeight,z,w,h,scene)
            val expected=2*BookGeometry.HalfHeight*BookGeometry.focal(w,h,true)/(BookGeometry.cameraDistance()-z)
            assertEquals(expected,bottom-top,.0003f)
        }
    }
}
