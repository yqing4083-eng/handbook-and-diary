package com.qingyu.personalaios.feature.diary

import androidx.compose.ui.geometry.Offset
import com.qingyu.personalaios.domain.InkTool
import org.junit.Assert.*
import org.junit.Test

class PaperRulerTest {
    @Test fun resizeKeepsRelativeRulerPositionWhenNavigationInsetsChange() {
        val ruler=PaperRulerState();ruler.resize(1000f,600f,30f)
        assertEquals(Offset(500f,270f),ruler.center)
        ruler.center=Offset(600f,300f);ruler.resize(1200f,800f,30f)
        assertEquals(Offset(720f,400f),ruler.center)
    }
    @Test fun signatureDrawsOnPaperAndEraserAndLassoStillWork() {
        assertTrue(PaperInkPolicy.canDrawOnPaper(InkTool.FELT,false,false))
        assertTrue(PaperInkPolicy.canDrawOnPaper(InkTool.FELT,true,false))
        assertTrue(PaperInkPolicy.canDrawOnPaper(InkTool.FELT,false,true))
        assertTrue(PaperInkPolicy.canDrawOnPaper(InkTool.FOUNTAIN,false,false))
    }
    @Test fun nearEdgeLocksStrokeToThatEdgeAndFarAwayInkStaysFree() {
        val ruler=PaperRulerState().apply { center=Offset(400f,500f);halfWidth=30f }
        assertEquals(-30f,ruler.edge(Offset(200f,465f),10f)!!,0f)
        assertNull(ruler.edge(Offset(200f,100f),10f))
        assertEquals(Offset(700f,470f),ruler.project(Offset(700f,461f),-30f))
        ruler.angle=90f
        val point=ruler.project(Offset(439f,700f),-30f)
        assertEquals(430f,point.x,.001f);assertEquals(700f,point.y,.001f)
    }
    @Test fun rotatedRulerKeepsEveryProjectedSampleCollinear() {
        val ruler=PaperRulerState().apply { center=Offset(180f,250f);halfWidth=20f;angle=37f }
        val samples=(0..20).map { ruler.project(Offset(it*21f,it*13f),20f) }
        assertTrue(samples.all { kotlin.math.abs(ruler.local(it).y-20f)<.001f })
    }
}
