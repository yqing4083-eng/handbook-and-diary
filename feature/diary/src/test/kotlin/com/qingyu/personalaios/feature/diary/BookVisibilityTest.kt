package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class BookVisibilityTest {
    @Test fun perspectiveFacingKeepsExactlyTheGpuFacingFlatSurface() {
        val model=FloatArray(16);model[10]=1f;model[14]=-5f
        assertTrue(BookRenderPolicy.faceVisible(model,2));assertFalse(BookRenderPolicy.faceVisible(model,1))
        model[10]=-1f
        assertFalse(BookRenderPolicy.faceVisible(model,2));assertTrue(BookRenderPolicy.faceVisible(model,1))
        assertTrue(BookRenderPolicy.faceVisible(model,0))
        model[10]=0f;assertTrue(BookRenderPolicy.faceVisible(model,1));assertTrue(BookRenderPolicy.faceVisible(model,2))
    }
    @Test fun offscreenCullingRetainsShadowGuardAndIncomingBooks() {
        assertFalse(BookRenderPolicy.shelfVisible(ClosedBookRect(2000f,200f,500f,800f),720f,1600f))
        assertTrue(BookRenderPolicy.shelfVisible(ClosedBookRect(-580f,200f,500f,800f),720f,1600f))
        assertTrue(BookRenderPolicy.shelfVisible(ClosedBookRect(30f,-700f,500f,800f),720f,1600f))
    }
}
