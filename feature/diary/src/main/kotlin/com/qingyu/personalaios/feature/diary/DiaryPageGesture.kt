package com.qingyu.personalaios.feature.diary

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observe taps before the curl renderer's stacked/hidden page nodes; leave drags to PageCurl. */
@Composable
internal fun Modifier.diaryPageTap(enabled: Boolean, onTap: () -> Unit): Modifier {
    val current = rememberUpdatedState(onTap)
    return if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1 ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) {
                    if (!moved) current.value()
                    break
                }
            }
        }
    }
}
