package com.qingyu.personalaios.feature.diary

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class AccordionGeometryTest {
    @Test fun distantSheetsRemainDistinctWithoutShrinkingNearestExposure() {
        assertEquals(.62f,AccordionGeometry.depth(1f),.000001f)
        assertTrue(AccordionGeometry.depth(2f)<AccordionGeometry.depth(3f))
        assertTrue(AccordionGeometry.depth(3f)<AccordionGeometry.MaxLayerDepth)
        assertEquals(AccordionGeometry.depth(2f),AccordionGeometry.depth(-2f),0f)
    }
    @Test fun hingedUnderPagesStayJoinedAndBelowTheTurningSolidSheet() {
        for(selection in listOf(0f,1f)) for(i in 0..100) {
            val base=AccordionGeometry.resting(0f,selection)
            val p=AccordionGeometry.underlay(i/100f,selection)
            assertEquals(base.x,p.x,0f);assertEquals(base.y,p.y,0f)
            assertTrue(p.z<=base.z+.000001f)
            assertTrue(base.z-p.z<PaperMaterial.Thickness)
        }
    }
    @Test fun physicalEdgeIsMoreVisibleButStillMuchThinnerThanLayerSpacing() {
        val pixels=PaperMaterial.Thickness*BookGeometry.focal(720f,1556f,true)/(BookGeometry.cameraDistance()-.03f)
        assertTrue("increased side edge: $pixels px",pixels in 4.5f..6f)
        assertTrue(PaperMaterial.Thickness<AccordionGeometry.LayerDepth/8f)
    }
    @Test fun neighbouringFoldsStayShallowAndOrderedDuringDragAndRebound() {
        for(dir in listOf(-1,1)) for(t in listOf(-.02f,0f,.2f,.5f,.8f,1f,1.04f)) for(delta in -3..2) {
            val a=AccordionGeometry.pose(delta,t,dir);val b=AccordionGeometry.pose(delta+1,t,dir)
            assertTrue(b.x>a.x)
            assertTrue(abs(b.z-a.z)<=AccordionGeometry.LayerDepth+.00001f)
            assertTrue(a.z>=.03f-AccordionGeometry.MaxLayerDepth-.00001f)
            // Side pages expose narrow edges, rather than large triangular wings.
            assertTrue(AccordionGeometry.fold(delta-dir*t) in 20f..30f)
        }
    }
    @Test fun committingEitherDirectionDoesNotJumpToTheNextPage() {
        for(dir in listOf(-1,1)) for(delta in -3..3) {
            val before=AccordionGeometry.pose(delta,1f,dir)
            val after=AccordionGeometry.pose(delta-dir)
            assertEquals(before.x,after.x,.00001f);assertEquals(before.z,after.z,.00001f)
        }
    }
    @Test fun referenceSpreadHasTheMeasuredWidthHeightAndVerticalPlacement() {
        val w=720f;val h=1556f;val centre=h*AccordionGeometry.ScreenCentre
        val left=BookGeometry.project(-BookGeometry.HalfWidth,BookGeometry.HalfHeight,-1,0,false,w,h,true,centre)
        val right=BookGeometry.project(BookGeometry.HalfWidth,-BookGeometry.HalfHeight,1,0,false,w,h,true,centre)
        // Wider paper reduces exposed cover; height and the screen anchor stay fixed.
        assertEquals(524f,right.first-left.first,5f)
        assertEquals(428f,right.second-left.second,8f)
        assertEquals(h*.513f,(left.second+right.second)/2,3f)
        val peer=BookGeometry.project(BookGeometry.HalfWidth,-BookGeometry.HalfHeight,1,1,false,w,h,true,centre)
        assertEquals(63f,peer.first-right.first,5f)
    }
    @Test fun sixExposedLayersMatchTheMeasuredProgressiveHeightsAndNarrowerEdges() {
        val camera=BookGeometry.cameraDistance()-BookGeometry.restingOuterZ()
        val expected=listOf(.925f,.880f,.843f,.807f,.775f,.744f)
        var previousEdge=BookGeometry.HalfWidth/camera
        var previousExposure=Float.POSITIVE_INFINITY
        for(layer in 1..6) {
            val depth=AccordionGeometry.depth(layer.toFloat())
            assertEquals(expected[layer-1],camera/(camera+depth),.012f)
            val edge=(BookGeometry.HalfWidth+AccordionGeometry.lateral(layer.toFloat()))/(camera+depth)
            val exposure=edge-previousEdge
            assertTrue(exposure>0f && exposure<previousExposure)
            previousEdge=edge;previousExposure=exposure
        }
    }
    @Test fun backFacesLandUprightOnTheOppositeHalfAtTheEndOfEachTurn() {
        for(side in listOf(-1,1)) {
            val end=BookGeometry.turningAngle(side,1f)*PI.toFloat()/180
            val rest=BookGeometry.angle(-side,1f)*PI.toFloat()/180
            assertEquals(side*cos(end),-side*cos(rest),.00001f)
            assertEquals(-side*sin(end),side*sin(rest),.00001f)
            assertTrue(BookGeometry.textureStart(side,true)!=BookGeometry.textureEnd(side,true))
        }
    }
    @Test fun selectingTheSpreadKeepsTheConnectedPeerHitRegionsUsable() {
        for(delta in listOf(-2,-1,1,2)) for(side in listOf(-1,1)) {
            val a=BookGeometry.project(side*1.2f,BookGeometry.HalfHeight,side,delta,false,720f,1556f,true)
            val b=BookGeometry.project(side*1.2f,BookGeometry.HalfHeight,side,delta,true,720f,1556f,true)
            assertEquals(a.first,b.first,0f)
            assertEquals(a.second,b.second,0f)
        }
    }
}
