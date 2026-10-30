package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class FountainNibTest {
    @Test fun sparseTouchSegmentsGetInteriorNibSamplesWithoutChangingEndpoints() {
        val a=com.qingyu.personalaios.domain.InkPoint(.1f,.5f,.3f)
        val b=com.qingyu.personalaios.domain.InkPoint(.9f,.5f,1.2f)
        val samples=FountainNib.sample(listOf(a,b),400f,400f,14f)
        assertEquals(a,samples.first());assertEquals(b.x,samples.last().x,.000001f)
        assertTrue(samples.size>30);assertTrue(samples.zipWithNext().all { (x,y) -> y.x>=x.x && y.pressure>=x.pressure })
    }
    @Test fun pressureAndDirectionProduceControlledThickThinInk() {
        val down=FountainNib.radius(20f,1f,-1f,0f,100f,100f)
        val up=FountainNib.radius(20f,1f,1f,0f,100f,100f)
        assertTrue(down>up*1.7f);assertTrue(down<11f);assertTrue(up>4f)
        assertTrue(FountainNib.radius(20f,.3f,1f,0f,100f,100f)<FountainNib.radius(20f,1.5f,1f,0f,100f,100f))
    }
    @Test fun horizontalTurnsHaveNoWidthJumpAndLiftTapersMoreThanTouchDown() {
        val a=FountainNib.radius(20f,1f,-.001f,.9999995f,100f,100f)
        val b=FountainNib.radius(20f,1f,.001f,.9999995f,100f,100f)
        assertEquals(a,b,.03f)
        assertTrue(FountainNib.taper(20f,100f,0f)<FountainNib.taper(20f,0f,100f))
        assertTrue(FountainNib.taper(20f,100f,0f)>0f)
    }
    @Test fun glyphScalingPreservesNibAspectAndCapsStayVisible() {
        val large=FountainNib.radius(20f,1f,1f,0f,30f,30f)
        assertEquals(large*.25f,FountainNib.radius(5f,1f,1f,0f,7.5f,7.5f),.0001f)
        val cap=FountainNib.radius(20f,1f,1f,0f,0f,30f)
        assertTrue(cap>0f && cap<large)
    }
}
