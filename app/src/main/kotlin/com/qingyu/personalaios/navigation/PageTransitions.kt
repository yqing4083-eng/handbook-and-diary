package com.qingyu.personalaios.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry

private val PageEase = CubicBezierEasing(.25f,.65f,.25f,1f)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.spacePageEnter(enabled:Boolean,layoutDirection:LayoutDirection):EnterTransition {
    if(!enabled) return EnterTransition.None
    val plan=pageMotionPlan(initialState.destination.route,targetState.destination.route)
    if(plan.axis==PageMotionAxis.NONE) return EnterTransition.None
    val sign=plan.direction*if(layoutDirection==LayoutDirection.Rtl) -1 else 1
    // Peer tabs move edge to edge; a pushed child reveals its parent on return.
    val slide=slideInHorizontally(tween(360,easing=PageEase)) { width ->
        (width*sign*if(plan.axis==PageMotionAxis.CHILD && plan.direction<0) .28f else 1f).toInt()
    }
    return if(plan.axis==PageMotionAxis.CHILD && plan.direction<0) slide+fadeIn(tween(300,easing=PageEase)) else slide
}
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.spacePageExit(enabled:Boolean,layoutDirection:LayoutDirection):ExitTransition {
    if(!enabled) return ExitTransition.None
    val plan=pageMotionPlan(initialState.destination.route,targetState.destination.route)
    if(plan.axis==PageMotionAxis.NONE) return ExitTransition.None
    val sign=plan.direction*if(layoutDirection==LayoutDirection.Rtl) -1 else 1
    val slide=slideOutHorizontally(tween(360,easing=PageEase)) { width ->
        (-width*sign*if(plan.axis==PageMotionAxis.CHILD && plan.direction>0) .28f else 1f).toInt()
    }
    return if(plan.axis==PageMotionAxis.CHILD && plan.direction>0) slide+fadeOut(tween(220,easing=PageEase)) else slide
}
