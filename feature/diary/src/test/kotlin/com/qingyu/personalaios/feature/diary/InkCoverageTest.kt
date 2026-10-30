package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class InkCoverageTest {
    @Test fun thinInkIsVisibleWithoutExpandingItsPixelFootprint() {
        assertTrue(InkCoverage.alpha(255,255,16)>255/16)
        assertEquals(0,InkCoverage.alpha(0,0,16))
    }
    @Test fun selectedOpacityAndSolidInkRemainUnchanged() {
        assertEquals(128,InkCoverage.alpha(128*16,128,16))
        assertEquals(255,InkCoverage.alpha(255*16,255,16))
        assertTrue(InkCoverage.alpha(128,128,16)<=128)
    }
    @Test fun gridAndPaperUseTheSameFingerPressureCurve() {
        assertEquals(1.6f,PaperPenDynamics.fingerPressure(0f),0f)
        assertEquals(.25f,PaperPenDynamics.fingerPressure(.05f),0f)
        assertTrue(PaperPenDynamics.fingerPressure(.001f)>PaperPenDynamics.fingerPressure(.005f))
    }
}
