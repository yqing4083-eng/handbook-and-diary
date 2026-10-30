package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class PaperShadowGeometryTest {
    private fun project(m:FloatArray,p:FloatArray):FloatArray=FloatArray(4) { row -> (0..3).sumOf { column -> (m[column*4+row]*p[column]).toDouble() }.toFloat() }
    @Test fun elevatedLeafCastsOntoDeskWithDownwardDisplacement() {
        val m=FloatArray(16);PaperShadowGeometry.projection(0f,0f,-1.12f,0f,0f,1f,m)
        val p=project(m,floatArrayOf(.2f,.5f,.3f,1f))
        assertEquals(-1.12f,p[2],.00001f);assertTrue(p[1]<.5f);assertTrue(p[0]>.2f);assertEquals(1f,p[3],.00001f)
    }
    @Test fun pointsOnReceiverRemainFixed() {
        val m=FloatArray(16);PaperShadowGeometry.projection(0f,0f,-.6f,.2f,0f,1f,m)
        val p=floatArrayOf(.5f,.7f,-.7f,1f)
        assertArrayEquals(p,project(m,p),.00001f)
    }
    @Test fun projectionFollowsTiltedReceiverPlane() {
        val m=FloatArray(16);PaperShadowGeometry.projection(.2f,0f,-.4f,.3f,.1f,1f,m)
        for(x in listOf(-1f,0f,1f)) {
            val p=project(m,floatArrayOf(x,.7f,.8f,1f))
            assertEquals(0f,.3f*(p[0]-.2f)+.1f*p[1]+p[2]+.4f,.00001f)
        }
    }
}
