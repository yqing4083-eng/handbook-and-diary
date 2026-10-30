package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import com.qingyu.personalaios.domain.DiaryJournal

class BookLibraryGeometryTest {
    @Test fun sideBookRearCornersLieOnTheDeskWithoutYawOrElevation() {
        for(delta in listOf(-1,1)) {
            val pose=BookLibraryGeometry.pose(delta,0f)
            val pitch=pose.pitch*kotlin.math.PI.toFloat()/180
            assertEquals(0f,pose.yaw,.00001f)
            for(localY in listOf(-.9f,.9f)) {
                val rearY=pose.y+localY*kotlin.math.cos(pitch)-.02f*kotlin.math.sin(pitch)
                val rearZ=pose.z+localY*kotlin.math.sin(pitch)+.02f*kotlin.math.cos(pitch)
                assertEquals(BookLibraryGeometry.tableZ(rearY),rearZ,.00001f)
            }
        }
    }
    @Test fun portraitCoverMatchesFullPhoneReferenceDespiteSystemAndTabInsets() {
        val scene=BookScene(closedCentreY=2400f*.513f-136f)
        val corner=BookLibraryGeometry.coverTopRight(1080f,2138f,true,scene.closedCentreY)
        val width=BookGeometry.CoverWidth*BookGeometry.focal(1080f,2138f,true,0f)/BookLibraryGeometry.ClosedViewDepth
        val height=BookGeometry.CoverHeight*BookGeometry.focal(1080f,2138f,true,0f)/BookLibraryGeometry.ClosedViewDepth
        assertEquals(.685f,width/1080f,.003f)
        assertEquals(.286f,(corner.second+136f)/2400f,.003f)
        assertEquals(.740f,(corner.second+height+136f)/2400f,.003f)
        assertEquals(.500f,(corner.first-width/2)/1080f,.001f)
    }
    @Test fun midwayBooksShareTheLowerPlaneAndControlsDisappear() {
        val offset=-BookLibraryGeometry.Step*.5f
        val outgoing=BookLibraryGeometry.pose(0,offset)
        val incoming=BookLibraryGeometry.pose(1,offset)
        assertEquals(outgoing.y,incoming.y,.00001f)
        assertEquals(outgoing.z,incoming.z,.00001f)
        assertEquals(BookLibraryGeometry.TablePitch,incoming.pitch,.00001f)
        assertEquals(0f,BookLibraryGeometry.controlsAlpha(offset),.00001f)
        assertEquals(1f,BookLibraryGeometry.controlsAlpha(-BookLibraryGeometry.Step),.00001f)
    }
    @Test fun incomingBookOnlyLiftsNearTheCentre() {
        assertEquals(BookLibraryGeometry.pose(1,0f).y,BookLibraryGeometry.pose(1,-BookLibraryGeometry.Step*.6f).y,.00001f)
        assertTrue(BookLibraryGeometry.pose(1,-BookLibraryGeometry.Step*.9f).y>BookLibraryGeometry.pose(1,0f).y)
        assertEquals(BookLibraryGeometry.pose(0,0f),BookLibraryGeometry.pose(1,-BookLibraryGeometry.Step))
    }
    @Test fun changingTheSelectedIdDoesNotMoveTheIncomingBook() {
        for(direction in listOf(-1,1)) {
            assertEquals(BookLibraryGeometry.pose(0,0f),BookLibraryGeometry.pose(direction,-direction*BookLibraryGeometry.Step))
        }
    }
    @Test fun neighbouringBooksRetreatInDepthAndDropBelowTheCentreBook() {
        val centre=BookLibraryGeometry.pose(0,0f);val side=BookLibraryGeometry.pose(1,0f)
        assertTrue(side.z<centre.z);assertTrue(side.y<centre.y)
    }
    @Test fun newBookArrivesFromLowerRightAndSettlesWithoutChangingItsFinalGeometry() {
        assertTrue(BookLibraryGeometry.arrival(0f).x>2f)
        assertEquals(BookLibraryGeometry.pose(0,0f),BookLibraryGeometry.arrival(1f))
        assertEquals(1f,BookLibraryGeometry.oldBookShift(1f),.0001f)
    }
    @Test fun aBackgroundTapDoesNotOpenTheBook() {
        val journal=DiaryJournal(id="a");val scene=BookScene(journal=journal,journals=listOf(journal))
        assertNull(BookLibraryGeometry.pick(20f,20f,1080f,2100f,scene))
        assertEquals(0,BookLibraryGeometry.pick(540f,1260f,1080f,2100f,scene))
    }
}
