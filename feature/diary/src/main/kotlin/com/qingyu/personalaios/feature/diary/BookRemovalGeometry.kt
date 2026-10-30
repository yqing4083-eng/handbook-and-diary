package com.qingyu.personalaios.feature.diary

/** Close only the deleted slot: the successor's side follows the normal shelf
 * curve, while the opposite side remains on the desk. Thus every survivor has
 * the same pose before and after the final repository/list handoff. */
internal object BookRemovalGeometry {
    const val ExitMillis=190
    const val ReplacementMillis=330
    fun direction(index:Int,count:Int):Int = when {
        index !in 0 until count || count<=1 -> 0
        index<count-1 -> 1
        else -> -1
    }
    fun offset(delta:Int,scene:BookScene):Float {
        if(scene.removingId!=scene.journal.id) return scene.libraryOffset
        val index=scene.journals.indexOfFirst { it.id==scene.removingId }
        val direction=direction(index,scene.journals.size)
        return if(delta*direction>0) scene.libraryOffset else 0f
    }
}
