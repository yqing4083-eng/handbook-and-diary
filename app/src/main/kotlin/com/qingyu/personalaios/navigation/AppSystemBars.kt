package com.qingyu.personalaios.navigation

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.*
import java.util.WeakHashMap

/** One window policy across navigation, picker return and configuration changes.
 * Background pixels are provided by the page; only its controls consume insets. */
internal object AppSystemBars {
    private data class Appearance(val darkIcons:Boolean,val immersive:Boolean)
    private val appearances=WeakHashMap<Window,Appearance>()
    fun update(activity:Activity,darkIcons:Boolean,immersive:Boolean) {
        val next=Appearance(darkIcons,immersive)
        if(appearances[activity.window]==next) return
        appearances[activity.window]=next;apply(activity.window,next)
    }
    fun reapply(activity:Activity) { appearances[activity.window]?.let { if(!matches(activity.window,it)) apply(activity.window,it) } }
    @Suppress("DEPRECATION") private fun matches(window:Window,appearance:Appearance):Boolean {
        val controller=WindowCompat.getInsetsController(window,window.decorView)
        val insets=ViewCompat.getRootWindowInsets(window.decorView) ?: return false
        val status=insets.isVisible(WindowInsetsCompat.Type.statusBars())
        val navigation=insets.isVisible(WindowInsetsCompat.Type.navigationBars())
        return controller.isAppearanceLightStatusBars==appearance.darkIcons &&
            controller.isAppearanceLightNavigationBars==appearance.darkIcons &&
            !window.isStatusBarContrastEnforced && !window.isNavigationBarContrastEnforced &&
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN!=0)==appearance.immersive &&
            window.attributes.rotationAnimation==WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS &&
            (Build.VERSION.SDK_INT>=35 || (window.statusBarColor==Color.TRANSPARENT && window.navigationBarColor==Color.TRANSPARENT)) &&
            window.attributes.flags and (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)==0 &&
            controller.systemBarsBehavior==WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE &&
            !navigation && status==!appearance.immersive
    }
    @Suppress("DEPRECATION") private fun apply(window:Window,appearance:Appearance) {
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        // The editor animates its own paper. Avoid adding a second whole-window
        // rotation; Android requires fullscreen on both sides for seamless mode.
        window.attributes=window.attributes.apply { rotationAnimation=WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS }
        if(appearance.immersive) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN) else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // Android 15+ draws the app under the bars; the pre-35 colours remain
        // necessary on supported Android 13/14 devices.
        if(Build.VERSION.SDK_INT<35) { window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Color.TRANSPARENT }
        window.isStatusBarContrastEnforced=false;window.isNavigationBarContrastEnforced=false
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=appearance.darkIcons
            isAppearanceLightNavigationBars=appearance.darkIcons
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Keep navigation transient throughout the app, including portrait
            // pages. Showing all bars on editor exit restored the detached pill.
            if(appearance.immersive) hide(WindowInsetsCompat.Type.statusBars())
            else show(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        ViewCompat.requestApplyInsets(window.decorView)
    }
}
