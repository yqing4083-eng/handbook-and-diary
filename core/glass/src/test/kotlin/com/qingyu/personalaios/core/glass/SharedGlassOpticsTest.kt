package com.qingyu.personalaios.core.glass

import com.qingyu.personalaios.domain.GlassParameters
import com.qingyu.personalaios.domain.resolve
import org.junit.Assert.*
import org.junit.Test

class SharedGlassOpticsTest {
    @Test fun restoredGlassIsTransparentAndSharp() {
        val optics=sharedGlassOptics(true)
        assertEquals(GlassParameters.Unified.resolve(1f,true,true),optics)
        assertEquals(0f,optics.blur,0f)
        assertEquals(0f,optics.tint,0f)
    }

    @Test fun reducedMotionPreservesTheSameBlurAndOpticalPolicy() {
        val animated=sharedGlassOptics(true)
        assertEquals(animated.copy(interaction=0f),sharedGlassOptics(false))
    }
}
