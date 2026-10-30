package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class BookMeshGeometryTest {
    // Cover, paper and lining use different radii / hinge edges in production.
    private val formats=listOf(
        floatArrayOf(1.25f,1.84f,.13f,1f,.045f),
        floatArrayOf(1.2f,1.8f,.095f,1f,PaperMaterial.PanelThickness),
        floatArrayOf(1.2f,1.8f,.095f,-1f,PaperMaterial.PanelThickness),
        floatArrayOf(1.2f,1.8f,.075f,0f,.12f)
    )

    @Test fun everyFaceBoundaryExactlyMatchesExtrudedRimWithoutCracks() {
        for(f in formats) {
            val face=BookMeshGeometry(f[0],f[1],f[2],f[3].toInt())
            val rim=BookMeshGeometry(f[0],f[1],f[2],f[3].toInt(),f[4])
            val count=face.vertices.size/5-1
            assertEquals(count*2*5,rim.vertices.size)
            for(i in 0 until count) {
                for(axis in 0..2) assertEquals(face.vertices[(i+1)*5+axis],rim.vertices[i*10+axis],0f)
                assertEquals(-f[4],rim.vertices[i*10+7],0f)
                // First triangle on each side reaches the next exact rim vertex,
                // including the final segment back to the first one.
                assertEquals(((i+1)%count)*2,rim.indices[i*6+2].toInt())
            }
        }
    }

    @Test fun planarTrianglesHaveConsistentWindingValidUvsAndNoDegenerates() {
        for(f in formats) {
            val mesh=BookMeshGeometry(f[0],f[1],f[2],f[3].toInt())
            assertTrue(mesh.vertices.all { it.isFinite() })
            for(i in mesh.vertices.indices step 5) {
                assertTrue(mesh.vertices[i+3] in -0.00001f..1.00001f)
                assertTrue(mesh.vertices[i+4] in -0.00001f..1.00001f)
            }
            for(t in mesh.indices.indices step 3) {
                val a=mesh.indices[t].toInt()*5;val b=mesh.indices[t+1].toInt()*5;val c=mesh.indices[t+2].toInt()*5
                assertTrue(listOf(a,b,c).all { it>=0 && it+4<mesh.vertices.size })
                val twiceArea=(mesh.vertices[b]-mesh.vertices[a])*(mesh.vertices[c+1]-mesh.vertices[a+1])-
                    (mesh.vertices[b+1]-mesh.vertices[a+1])*(mesh.vertices[c]-mesh.vertices[a])
                assertTrue("Reversed or collapsed triangle $t",twiceArea>0f)
            }
        }
    }

    @Test fun liningFaceAndSideWallRemainInFrontOfRearBoardThroughoutOpening() {
        for(i in 0..1000) {
            val open=i/1000f
            val front=BookBoardMaterial.rearLinerZ(open)
            val back=front-.12f*BookBoardMaterial.blockDepthScale(open)
            assertTrue("Lining intersects board at $open",back>=-0.000001f)
            assertTrue("Coplanar visible faces at $open",front>0f)
        }
        // The thin fully-open lining must keep its complete wall above the board,
        // not merely offset the visible front while leaving the wall intersecting.
        assertTrue(BookBoardMaterial.rearLinerZ(1f)-.12f*BookBoardMaterial.blockDepthScale(1f)>.0005f)
    }
    @Test fun thickerPaperCannotIntersectTheFrontCoverUnderside() {
        val underside=BookBoardMaterial.frontOffset-BookBoardMaterial.Thickness
        assertEquals(BookBoardMaterial.FrontPaperGap,underside-PaperMaterial.Thickness/2,.000001f)
        assertTrue(underside>PaperMaterial.Thickness/2)
    }
}
