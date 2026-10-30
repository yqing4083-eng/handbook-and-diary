package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.InkPoint
import org.junit.Assert.*
import org.junit.Test

class PaperPenDynamicsTest {
    @Test fun opacityKeepsRgbAndIsStoredInTheStrokeColor() {
        assertEquals(0x7F245ABCL,PaperPenDynamics.color(0xFF245ABCL,.5f))
        assertEquals(0xFF245ABCL,PaperPenDynamics.color(0x00245ABCL,1f))
    }
    @Test fun pressureCanBeDisabledWithoutChangingPointLocations() {
        assertEquals(1f,PaperPenDynamics.pressure(.2f,0f),0f)
        assertEquals(.2f,PaperPenDynamics.pressure(.2f,1f),.0001f)
        assertEquals(.6f,PaperPenDynamics.pressure(.2f,.5f),.0001f)
    }
    @Test fun straightDrawingKeepsTheGestureEndpointsAndTap() {
        val points=listOf(InkPoint(.1f,.2f),InkPoint(.3f,.8f),InkPoint(.7f,.4f))
        assertEquals(listOf(points.first(),points.last()),PaperPenDynamics.points(points,true))
        assertSame(points,PaperPenDynamics.points(points,false))
        assertEquals(points.take(1),PaperPenDynamics.points(points.take(1),true))
    }
}
