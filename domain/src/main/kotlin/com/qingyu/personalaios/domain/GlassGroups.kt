package com.qingyu.personalaios.domain

enum class GlassTarget { BOTTOM_BAR, BUTTONS, NUMBERS }

/** Independent optical preferences. Defaults preserve the previously fixed clear material. */
data class GlassGroups(
    val bottomBar:GlassParameters=GlassParameters.Unified,
    val buttons:GlassParameters=GlassParameters.ButtonDefault,
    val numbers:GlassParameters=GlassParameters.NumberDefault,
) {
    val layered:GlassParameters get()=buttons.copy(chromaticAberration=true)
    companion object {
        fun defaultFor(target:GlassTarget)=GlassGroups()[target]
        fun presetUpdate(existing:GlassGroups)=listOf(GlassTarget.BUTTONS,GlassTarget.NUMBERS).associateWith { target ->
            defaultFor(target).copy(chromaticStrength=existing[target].chromaticStrength)
        }
    }
    operator fun get(target:GlassTarget):GlassParameters=when(target) {
        GlassTarget.BOTTOM_BAR -> bottomBar
        GlassTarget.BUTTONS -> buttons
        GlassTarget.NUMBERS -> numbers
    }
    fun updated(target:GlassTarget,value:GlassParameters):GlassGroups=when(target) {
        GlassTarget.BOTTOM_BAR -> copy(bottomBar=value.normalized())
        GlassTarget.BUTTONS -> copy(buttons=value.normalized())
        GlassTarget.NUMBERS -> copy(numbers=value.normalized())
    }
}
