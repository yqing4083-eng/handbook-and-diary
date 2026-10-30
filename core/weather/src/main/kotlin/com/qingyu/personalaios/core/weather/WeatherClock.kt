package com.qingyu.personalaios.core.weather

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/** Shared draw-phase time for particles and optical breathing, paused outside STARTED. */
@Composable
fun rememberWeatherClock(motionEnabled: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle,motionEnabled) {
        if (motionEnabled) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (isActive) {
              withFrameNanos { frame ->
                if (previous != 0L) seconds.floatValue += ((frame-previous)/1_000_000_000f).coerceIn(0f,.05f)
                previous = frame
              }
              // Weather drift/optical breathing is deliberately slow. Do not
              // wake and re-record all glass backdrops at the panel's 120/144Hz.
              // Gesture, book, navigation and editor animations own other clocks.
              delay(30)
            }
        }
    }
    return seconds
}
