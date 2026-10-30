package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class BookGeometryTest {
    @Test fun reverseOfTurningLeafSamplesTheOtherHalfOfTheNextSheet() {
        assertEquals(.5f,BookGeometry.textureStart(1,true),.0001f)
        assertEquals(0f,BookGeometry.textureEnd(1,true),.0001f)
        assertEquals(1f,BookGeometry.textureStart(-1,true),.0001f)
        assertEquals(.5f,BookGeometry.textureEnd(-1,true),.0001f)
    }
    @Test fun turnPassesThroughAnEdgeOnLeafInBothDirections() {
        assertEquals(-90f,BookGeometry.turningAngle(1,.5f),.001f)
        assertEquals(90f,BookGeometry.turningAngle(-1,.5f),.001f)
    }
    @Test fun turnedLeafSettlesExactlyOntoOppositeHalf() {
        for(side in listOf(-1,1)) {
            val end=BookGeometry.turningAngle(side,1f)*PI/180
            val rest=BookGeometry.angle(-side,1f)*PI/180
            assertEquals((-side*cos(rest)).toFloat(),(side*cos(end)).toFloat(),.00001f)
            assertEquals((side*sin(rest)).toFloat(),(-side*sin(end)).toFloat(),.00001f)
        }
    }
    @Test fun closedLeftHalfFoldsOntoThePortraitRightHalf() {
        assertEquals(180f,BookGeometry.angle(-1,0f),.001f)
        assertEquals(0f,BookGeometry.angle(1,0f),.001f)
    }
    @Test fun physicalPagesKeepTheirDimensionsThroughOpeningAndRotation() {
        assertEquals(1f,BookGeometry.heightScale(true,0f),.001f)
        assertEquals(1f,BookGeometry.heightScale(true,.5f),.001f)
        assertEquals(1f,BookGeometry.heightScale(true,1f),.001f)
        assertEquals(1f,BookGeometry.heightScale(false),.001f)
        val portrait=BookGeometry.project(0f,.9f,1,0,false,1080f,1400f,true)
        val wide=BookGeometry.project(0f,.9f,1,0,false,1080f,1400f,false)
        // Compare points at the same physical depth. The layout centre anchors
        // the outer corners, not this inner edge on the angled panel.
        val portraitOrigin=BookGeometry.project(0f,0f,1,0,false,1080f,1400f,true)
        val wideOrigin=BookGeometry.project(0f,0f,1,0,false,1080f,1400f,false)
        val portraitExtent=portraitOrigin.second-portrait.second
        val wideExtent=wideOrigin.second-wide.second
        assertTrue(portraitExtent>wideExtent)
        assertEquals(
            BookGeometry.focal(1080f,1400f,true)/BookGeometry.focal(1080f,1400f,false),
            portraitExtent/wideExtent,.001f
        )
    }
    @Test fun pageSelectionWorksOnTheSameSheetInBothViewportModes() {
        assertEquals(3,BookPagePicker.pick(540f,700f,1080f,1400f,3,9,false,true))
        assertEquals(3,BookPagePicker.pick(1200f,540f,2400f,1080f,3,9,false,false))
        assertNull(BookPagePicker.pick(1200f,20f,2400f,1080f,3,9,false,false))
    }
}
