package com.qingyu.personalaios.navigation

internal enum class PageMotionAxis { HORIZONTAL, CHILD, NONE }
internal data class PageMotionPlan(val axis: PageMotionAxis, val direction: Int = 0)

/** Route relationships, rather than navigation operation, determine the visual direction.
 * A restored tab may be a saved Me subpage; it still moves in Me's tab direction.
 */
internal fun pageMotionPlan(from: String?, to: String?): PageMotionPlan {
    fun position(route: String?): Pair<Int, Int>? = when (route) {
        "today" -> 0 to 0
        "calendar" -> 1 to 0
        "diary" -> 2 to 0
        "me" -> 3 to 0
        "settings" -> 3 to 1
        "glass-settings", "shape-preview" -> 3 to 2
        "glass-preview" -> 3 to 3
        else -> if(route?.startsWith("glass-settings/")==true) 3 to 3 else null
    }
    val start = position(from) ?: return PageMotionPlan(PageMotionAxis.NONE)
    val end = position(to) ?: return PageMotionPlan(PageMotionAxis.NONE)
    return when {
        start.first != end.first -> PageMotionPlan(PageMotionAxis.HORIZONTAL, end.first.compareTo(start.first))
        start.second != end.second -> PageMotionPlan(PageMotionAxis.CHILD, end.second.compareTo(start.second))
        else -> PageMotionPlan(PageMotionAxis.NONE)
    }
}

