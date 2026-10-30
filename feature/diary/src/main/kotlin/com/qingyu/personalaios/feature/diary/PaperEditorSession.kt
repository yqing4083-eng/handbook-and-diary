package com.qingyu.personalaios.feature.diary

import androidx.compose.runtime.staticCompositionLocalOf

internal data class PaperEditorSession(val wallpapers:Boolean=false,val toolTravel:Float=0f,val changed:Boolean=false,
    val onWallpapers:()->Unit={},val onConfirm:()->Unit={},val onCancel:()->Unit={},val onInkActivity:()->Unit={})
internal val LocalPaperEditorSession=staticCompositionLocalOf { PaperEditorSession() }
