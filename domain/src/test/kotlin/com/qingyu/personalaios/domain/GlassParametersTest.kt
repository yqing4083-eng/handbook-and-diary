package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class GlassParametersTest {
    @Test fun unifiedStyleMatchesRequestedOpticsExactly() {
        assertEquals(GlassOptics(1f,0f,1f,1.04f,0f,true,.7f),GlassParameters.Unified.resolve(1f,true,true))
    }
    @Test fun corruptedPreferencesNeverReachRenderer() {
        val parameters = GlassParameters(Float.NaN, Float.POSITIVE_INFINITY, -5f, 99f, Float.NEGATIVE_INFINITY, false).normalized()
        assertEquals(1.8f, parameters.refraction, 0f)
        assertEquals(4f, parameters.blur, 0f)
        assertEquals(0f, parameters.highlight, 0f)
        assertEquals(1.3f, parameters.magnification, 0f)
        assertEquals(.07f, parameters.tint, 0f)
        assertFalse(parameters.chromaticAberration)
    }
    @Test fun zeroOpticsAndPresetExtremesRemainSelectable() {
        val zero = GlassParameters(0f, 0f, 0f, 1f, 0f, false)
        assertEquals(zero, zero.normalized())
        assertEquals(GlassParameters.Strong, GlassParameters.Strong.normalized())
        assertEquals(GlassParameters.Clear, GlassParameters.Clear.normalized())
        assertTrue(GlassParameters.Strong.refraction > GlassParameters().refraction)
        assertTrue(GlassParameters().refraction > GlassParameters.Clear.refraction)
    }
    @Test fun qualityAndStaticModePreservePreferencesButReduceResolvedWork() {
        val saved = GlassParameters.Strong
        val low = saved.resolve(.26f, false, false)
        assertEquals(saved.refraction * .26f, low.refraction, .0001f)
        assertEquals(saved.blur * .26f, low.blur, .0001f)
        assertEquals(1f + (saved.magnification - 1f) * .26f, low.magnification, .0001f)
        assertEquals(saved.highlight, low.highlight, 0f)
        assertEquals(saved.tint, low.tint, 0f)
        assertEquals(0f, low.interaction, 0f)
        assertFalse(low.chromatic)
        assertEquals(1.3f, saved.interaction, 0f)
    }
    @Test fun zeroAndCorruptedInteractionNeverProduceUnexpectedDeformation() {
        assertEquals(0f, GlassParameters(interaction = 0f).resolve(1f, true, true).interaction, 0f)
        assertEquals(1f, GlassParameters(interaction = Float.NaN).normalized().interaction, 0f)
        assertEquals(1.5f, GlassParameters(interaction = 10f).normalized().interaction, 0f)
        val plain = GlassParameters(0f, 0f, 0f, 1f, 0f, false, 0f).resolve(1f, true, true)
        assertEquals(GlassOptics(0f, 0f, 0f, 1f, 0f, false, 0f), plain)
    }
    @Test fun dispersionDefaultPreservesExistingRenderingAndBoundsCorruptValues() {
        assertEquals(1f,GlassParameters.Unified.chromaticStrength,0f)
        assertEquals(1f,GlassParameters(chromaticStrength=Float.NaN).normalized().chromaticStrength,0f)
        assertEquals(0f,GlassParameters(chromaticStrength=-1f).normalized().chromaticStrength,0f)
        assertEquals(3f,GlassParameters(chromaticStrength=20f).normalized().chromaticStrength,0f)
    }
    @Test fun dispersionSwitchAndZeroIntensityDisableSamplingWithoutLosingSavedStrength() {
        val saved=GlassParameters.Unified.copy(chromaticStrength=2.4f)
        assertEquals(2.4f,saved.resolve(1f,true,true).dispersion,0f)
        val disabled=saved.copy(chromaticAberration=false)
        assertEquals(2.4f,disabled.chromaticStrength,0f)
        assertFalse(disabled.resolve(1f,true,true).chromatic)
        assertEquals(0f,disabled.resolve(1f,true,true).dispersion,0f)
        assertFalse(saved.copy(chromaticStrength=0f).resolve(1f,true,true).chromatic)
        assertEquals(0f,saved.resolve(1f,true,false).dispersion,0f)
    }
    @Test fun qualityScalesDispersionButMotionPolicyDoesNotChangeItsStrength() {
        val saved=GlassParameters.Unified.copy(chromaticStrength=2.4f)
        assertEquals(.6f,saved.resolve(.25f,true,true).dispersion,.0001f)
        assertEquals(saved.resolve(1f,true,true).dispersion,saved.resolve(1f,false,true).dispersion,0f)
    }
}
