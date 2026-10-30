package com.qingyu.personalaios.feature.diary

/** Navigation waits for the active paper and cover to return to their resting geometry. */
class DiaryExitGate {
    internal var handler: (suspend () -> Boolean)? = null
    suspend fun leave(): Boolean = handler?.invoke() ?: true
}
