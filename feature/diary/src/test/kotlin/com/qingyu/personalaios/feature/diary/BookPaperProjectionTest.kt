package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.PaperFace
import org.junit.Assert.*
import org.junit.Test

class BookPaperProjectionTest {
    @Test fun faceCornersArePerspectiveQuadsAndMeetAtTheHinge() {
        for(w in listOf(320f,411f,480f)) {
            val l=BookPaperProjection.face(0,PaperFace.LEFT,w,800f,400f)
            val r=BookPaperProjection.face(0,PaperFace.RIGHT,w,800f,400f)
            assertTrue(l.points.all { it.isFinite() } && r.points.all { it.isFinite() })
            assertTrue(l.left<r.left);assertTrue(l.right<r.right)
            assertTrue(kotlin.math.abs(r.points[1]-r.points[3])>1f)
            assertEquals(w/2,r.points[0],2f);assertEquals(w/2,l.points[2],2f)
        }
    }
    @Test fun bothEndsOfSnapshotHandoffAreExactAndAllCornersStayContinuous() {
        val target=PaperRect(0f,66f,411f,610f)
        for(side in PaperFace.entries) {
            val source=BookPaperProjection.face(2,side,411f,800f,400f)
            assertArrayEquals(source.points,BookPaperProjection.interpolate(source,target,0f).points,0f)
            assertArrayEquals(floatArrayOf(0f,66f,411f,66f,411f,676f,0f,676f),BookPaperProjection.interpolate(source,target,1f).points,.0001f)
            var previous=source.points
            for(i in 1..1000) {
                val current=BookPaperProjection.interpolate(source,target,i/1000f).points
                assertTrue(current.indices.all { kotlin.math.abs(current[it]-previous[it])<1f });previous=current
            }
        }
    }
}
