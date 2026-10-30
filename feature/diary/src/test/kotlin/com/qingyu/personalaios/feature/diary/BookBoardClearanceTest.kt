package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class BookBoardClearanceTest {
    @Test fun rearLiningAndFrontBoardStayOutsidePaperThroughoutOpeningAndClosing() {
        for(step in 0..1000) {
            val spread=step/1000f
            // All surfaces use the attached panel's frame, so normal distance
            // is independent of camera, yaw, fan depth and the selected page.
            val liner=BookBoardMaterial.rearOffset(spread)+BookBoardMaterial.rearLinerZ(spread)
            assertTrue(liner<=-PaperMaterial.Thickness/2-.0019f)
            assertTrue(BookBoardMaterial.frontOffset-BookBoardMaterial.Thickness>=PaperMaterial.Thickness/2+.0019f)
        }
    }
    @Test fun closedAndOpeningVolumesHaveMatchingSurfaceEndpointsAndSmallerCoverReveal() {
        assertEquals(BookBoardMaterial.ClosedRearFace,
            BookBoardMaterial.paperBase(0f)+BookBoardMaterial.rearOffset(0f),.000001f)
        assertEquals(BookBoardMaterial.ClosedBlockFace,
            BookBoardMaterial.paperBase(0f)+BookBoardMaterial.rearOffset(0f)+BookBoardMaterial.rearLinerZ(0f),.000001f)
        assertEquals(BookBoardMaterial.closedFrontFace,
            BookBoardMaterial.paperBase(0f)+BookBoardMaterial.frontOffset,.000001f)
        assertEquals(.015f,BookGeometry.CoverWidth-BookGeometry.HalfWidth,.000001f)
        assertTrue(BookGeometry.CoverHeight>BookGeometry.HalfHeight*2)
    }
}
