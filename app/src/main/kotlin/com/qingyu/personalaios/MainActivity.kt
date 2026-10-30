package com.qingyu.personalaios

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qingyu.personalaios.navigation.PersonalSpaceApp
import com.qingyu.personalaios.navigation.DokiLoadingPage
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.qingyu.personalaios.navigation.AppSystemBars.reapply(this)
        androidx.core.view.ViewCompat.requestApplyInsets(window.decorView)
    }
    override fun onResume() { super.onResume();com.qingyu.personalaios.navigation.AppSystemBars.reapply(this) }
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) com.qingyu.personalaios.navigation.AppSystemBars.reapply(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Apply before the first loading frame; focus/picker return uses the
        // same policy so navigation never reserves a separate bottom strip.
        window.isNavigationBarContrastEnforced=false
        window.isStatusBarContrastEnforced=false
        com.qingyu.personalaios.navigation.AppSystemBars.update(this,darkIcons=false,immersive=false)
        var ready=false
        // Hold the platform launch surface until the saved wallpaper is decoded.
        val decor=window.decorView
        val drawGate=object:android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
                if(ready) decor.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        }
        decor.viewTreeObserver.addOnPreDrawListener(drawGate)
        setContent {
            var loading by remember { mutableStateOf(true) }
            var contentReady by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if(loading) Modifier.clearAndSetSemantics {} else Modifier)) {
                    PersonalSpaceApp(onReady={ contentReady=true },onSurfaceReady={ ready=true },loading=loading)
                }
                if(loading) DokiLoadingPage(contentReady) { loading=false }
            }
        }
    }
}
