package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class BookConnectedMotionTest {
    private fun outer(opening:Float,progress:Float=0f):PagePose = AlbumLeafGeometry.foldEdge(
        AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(2,2,progress,1,opening=opening),false))

    @Test fun openingOvershootsWidthByTheMeasuredAmountWithoutIncreasingOuterHeight() {
        val final=outer(1f);val peak=outer(.70f)
        val camera=BookGeometry.cameraDistance()
        val finalWidth=final.x/(camera-final.z)
        val peakWidth=peak.x/(camera-peak.z)
        assertEquals(1.087f,peakWidth/finalWidth,.006f)
        assertEquals(final.z,peak.z,.00001f)
        assertEquals(BookGeometry.RestAngle,BookOpeningGeometry.restAngle(1f),0f)
        assertEquals(1f,BookOpeningGeometry.scale(.70f),.00001f)
    }

    @Test fun openingAndLandingKeepTheTwoPanelsJoinedAndRigid() {
        for(open in listOf(.22f,.5f,.66f,.70f,.85f,1f))
            for(progress in listOf(0f,.25f,.5f,.75f,1f,1.025f)) for(leaf in -1..6) {
                val pose=AlbumLeafGeometry.pose(leaf,2,progress,1,opening=open)
                val panels=listOf(false,true).map { AlbumLeafGeometry.panel(pose,it) }
                val edges=panels.map { AlbumLeafGeometry.foldEdge(it) }
                assertEquals(edges[0].x,edges[1].x,.00001f)
                assertEquals(edges[0].z,edges[1].z,.00001f)
                panels.zip(edges).forEach { (panel,edge) ->
                    assertEquals(BookGeometry.HalfWidth,hypot(edge.x-panel.hinge.x,edge.z-panel.hinge.z),.00001f)
                }
            }
    }

    @Test fun insertionPreservesExistingRightLayersUntilTheNewLayerIsExposed() {
        for(current in 0..4) for(oldLeaf in current+1..current+5) {
            val old=AlbumLeafGeometry.pose(oldLeaf,current,0f,1)
            val inserted=AlbumLeafGeometry.pose(oldLeaf+1,current,0f,1,reveal=0f)
            assertEquals(old.hinge.x,inserted.hinge.x,.00001f)
            assertEquals(old.hinge.z-.002f,inserted.hinge.z,.00001f)
            assertEquals(old.angle,inserted.angle,.00001f)
        }
    }

    @Test fun revealMovesTheNewLayerOutOfOcclusionWithoutMovingTheCurrentFold() {
        val reference=AlbumLeafGeometry.pose(2,2,0f,1)
        var lastX=-1f
        for(step in 0..100) {
            val reveal=step/100f
            val old=AlbumLeafGeometry.pose(2,2,0f,1,reveal=reveal)
            assertEquals(reference,old)
            val incoming=AlbumLeafGeometry.pose(3,2,0f,1,reveal=reveal)
            assertTrue(incoming.hinge.x>=lastX)
            lastX=incoming.hinge.x
        }
        assertTrue(lastX>.3f)
    }

    @Test fun landingKeepsTheRailAndNeighboursStillAcrossCommit() {
        for(direction in listOf(-1,1)) {
            val target=2+direction
            for(leaf in -1..6) {
                val settled=AlbumLeafGeometry.pose(leaf,2,1f,direction)
                val rebound=AlbumLeafGeometry.pose(leaf,2,1.025f,direction)
                assertEquals(settled,rebound)
                assertEquals(0f,rebound.split,0f)
                val committed=AlbumLeafGeometry.pose(leaf,target,0f,direction)
                assertEquals(settled,committed)
            }
        }
        // Any residual clock overshoot cannot raise the settled paper fan.
        val landed=outer(1f,1f);val rebound=outer(1f,1.025f)
        // The outgoing half faces left at landing, hence opposite outer normal.
        assertEquals(landed.z,rebound.z,.00001f)
    }

    @Test fun additionExposesBeforeTurningAndPassesSideOnWithoutStopping() {
        assertEquals(0f,BookAdditionGeometry.reveal(0f),0f)
        assertEquals(1f,BookAdditionGeometry.reveal(.16f),0f)
        assertEquals(0f,BookAdditionGeometry.turn(.16f),0f)
        assertEquals(.5f,BookAdditionGeometry.turn(.39f),.00001f)
        val before=(BookAdditionGeometry.turn(.39f)-BookAdditionGeometry.turn(.3899f))/.0001f
        val after=(BookAdditionGeometry.turn(.3901f)-BookAdditionGeometry.turn(.39f))/.0001f
        assertTrue(before>3f && after>3f)
        assertEquals(before,after,.02f)
        assertEquals(1.025f,BookAdditionGeometry.turn(.84f),.00001f)
        assertEquals(1f,BookAdditionGeometry.turn(1f),.00001f)
        var previous=0f
        for(step in 0..720) {
            val next=BookAdditionGeometry.turn(step/1000f)
            assertTrue(next>=previous-.00001f);previous=next
        }
    }
}
