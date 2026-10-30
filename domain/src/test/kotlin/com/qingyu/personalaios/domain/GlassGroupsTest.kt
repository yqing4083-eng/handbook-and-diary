package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class GlassGroupsTest {
    @Test fun tuningOneGroupLeavesOtherGroupsAndDefaultMaterialIntact() {
        val defaults=GlassGroups()
        GlassTarget.entries.forEach { selected ->
            val tuned=defaults.updated(selected,GlassParameters.Strong)
            GlassTarget.entries.forEach { target ->
                assertEquals(if(target==selected) GlassParameters.Strong else defaults[target],tuned[target])
            }
        }
    }
    @Test fun buttonAndNumberPresetsMatchRequestedValuesAndLayersKeepDispersion() {
        val groups=GlassGroups()
        assertEquals(GlassParameters(1f,0f,1f,1.05f,0f,false,1f),groups.buttons)
        assertEquals(GlassParameters(3f,0f,1f,1f,0f,false,.7f),groups.numbers)
        assertFalse(groups.buttons.chromaticAberration)
        assertTrue(groups.layered.chromaticAberration)
        assertEquals(groups.buttons.copy(chromaticAberration=true),groups.layered)
        assertEquals(GlassParameters.Unified,groups.bottomBar)
    }
    @Test fun presetUpgradeOnlyTouchesRequestedGroupsAndRetainsTheirDispersionStrength() {
        val old=GlassGroups(GlassParameters.Strong,GlassParameters.Clear.copy(chromaticStrength=2.3f),GlassParameters.Strong.copy(chromaticStrength=.6f))
        val changes=GlassGroups.presetUpdate(old)
        assertEquals(setOf(GlassTarget.BUTTONS,GlassTarget.NUMBERS),changes.keys)
        assertEquals(GlassParameters.ButtonDefault.copy(chromaticStrength=2.3f),changes[GlassTarget.BUTTONS])
        assertEquals(GlassParameters.NumberDefault.copy(chromaticStrength=.6f),changes[GlassTarget.NUMBERS])
    }
    @Test fun sequentialEditsKeepEarlierTargetsAndNormalizeInvalidValues() {
        val tuned=GlassGroups().updated(GlassTarget.BOTTOM_BAR,GlassParameters.Clear)
            .updated(GlassTarget.NUMBERS,GlassParameters.Strong)
            .updated(GlassTarget.BUTTONS,GlassParameters(refraction=99f,blur=Float.NaN))
        assertEquals(GlassParameters.Clear,tuned.bottomBar)
        assertEquals(GlassParameters.Strong,tuned.numbers)
        assertEquals(3f,tuned.buttons.refraction,0f)
        assertTrue(tuned.buttons.blur.isFinite())
    }
}
