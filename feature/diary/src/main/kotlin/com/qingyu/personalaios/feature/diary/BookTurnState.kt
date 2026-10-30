package com.qingyu.personalaios.feature.diary

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot

internal class BookTurnState(private val index: MutableState<Int>) {
    var current: Int get() = index.value; private set(value) { index.value = value }
    val animation = Animatable(0f)
    var adding by mutableStateOf(false); private set
    private var holdingCommit by mutableStateOf(false)
    private var tracked by mutableStateOf<Float?>(null)
    var settling by mutableStateOf(false);private set
    private var settlement=0L
    val progress get() = tracked ?: if(holdingCommit) 1f else if(adding) BookAdditionGeometry.turn(animation.value) else animation.value
    val reveal get() = if(holdingCommit) 1f else if(adding) BookAdditionGeometry.reveal(animation.value) else 1f
    var direction by mutableIntStateOf(1)
    suspend fun snapTo(value: Int) { tracked=null;current = value; animation.snapTo(0f) }
    fun beginDrag() { tracked=progress.coerceIn(0f,.999f) }
    fun dragTo(value:Float) { tracked=value.coerceIn(0f,1f) }
    /** Prepare a concealed layer before exposing and turning it on one timeline.
     * Existing manual turns still settle from the user's release velocity. */
    suspend fun addedPage(motion:Boolean,onPrepared:()->Unit={}) {
        direction=1
        adding=true
        try {
            animation.snapTo(0f);onPrepared()
            if(motion) animation.animateTo(1f,tween(BookAdditionGeometry.DurationMillis,easing=LinearEasing))
            else animation.snapTo(1f)
            commit()
        } finally { adding=false }
    }
    private suspend fun commit() {
        // Animatable reset may suspend. Retain the landed pose until cursor and
        // progress can be rebased together, including the insertion reveal.
        holdingCommit=true
        animation.snapTo(0f)
        Snapshot.withMutableSnapshot { current+=direction;adding=false;holdingCommit=false }
    }
    suspend fun finish(commit: Boolean, motion: Boolean,duration:Int=BookGestureMotion.ReleaseMillis,velocity:Float=0f) {
        val ticket=++settlement
        settling=true
        try {
            // Keep the last finger pose visible while Animatable is seeded.
            tracked?.let { animation.snapTo(it);tracked=null }
            if (motion) animation.animateTo(if(commit) 1f else 0f,
                spring(1f,480f*(360f/duration)*(360f/duration),.002f),
                initialVelocity=velocity.coerceIn(-8f,8f))
            else animation.snapTo(if(commit) 1f else 0f)
            if (commit) commit() else animation.snapTo(0f)
        } finally { if(settlement==ticket) settling=false }
    }
    suspend fun next() { direction=1;finish(true,true) }
    suspend fun prev() { direction=-1;finish(true,true) }
}
@Composable internal fun rememberBookTurnState(): BookTurnState {
    val index = rememberSaveable { mutableStateOf(0) }
    return remember { BookTurnState(index) }
}
