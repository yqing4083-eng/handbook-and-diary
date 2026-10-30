package com.qingyu.personalaios.navigation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.*

internal data class WallpaperImage(val path:String?,val image:ImageBitmap?=null)
private object WallpaperCache {
    @Volatile var current=WallpaperImage(null)
    fun load(path:String?):WallpaperImage {
        if(current.path==path) return current
        val bitmap=path?.let { BitmapFactory.decodeFile(it) }
        return WallpaperImage(path,bitmap?.asImageBitmap()).also { current=it }
    }
}

@Composable internal fun rememberWallpaper(path:String?,settingsReady:Boolean):WallpaperImage {
    val image by produceState(WallpaperCache.current,path,settingsReady) {
        if(settingsReady) value=withContext(Dispatchers.IO) { WallpaperCache.load(path) }
    }
    return image
}

@Composable internal fun CustomBackground(wallpaper:WallpaperImage,nightMode:Boolean=true) {
    wallpaper.image?.let {
        Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        if(nightMode) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(WallpaperAppearance.shades.map { alpha -> Color.Black.copy(alpha) })))
    }
}
