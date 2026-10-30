package com.qingyu.personalaios.feature.diary

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.ui.SpaceText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PaperWallpapers {
    val names=listOf("鸢尾","晨星","晴空","青芽","樱桃","云朵","粉蝶","碎花","软糖","小猫","花语","莓果","心动","月光")
    fun file(index:Int)="paper_wallpaper_${(index+1).toString().padStart(2,'0')}.png"
    fun asset(index:Int)="paper-wallpapers/paper_${(index+1).toString().padStart(2,'0')}.png"
    fun index(path:String)=names.indices.firstOrNull { path.endsWith("/"+file(it)) } ?: -1
}

@Composable internal fun PaperWallpaperPicker(visible:Boolean,path:String,working:Boolean,onCustom:()->Unit,onOriginal:()->Unit,onPreset:(Int)->Unit,modifier:Modifier=Modifier) {
    AnimatedVisibility(visible,modifier,enter=slideInHorizontally(tween(320,easing=CubicBezierEasing(.22f,.85f,.28f,1f))) { it }+fadeIn(tween(200)),
        exit=slideOutHorizontally(tween(320,easing=CubicBezierEasing(.22f,.85f,.28f,1f))) { it }+fadeOut(tween(200))) {
        LazyRow(Modifier.fillMaxWidth().semantics { contentDescription="纸页壁纸列表" },contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            item("custom") { WallpaperMini("自定义",null,path.isNotBlank() && PaperWallpapers.index(path)<0,!working,onCustom) }
            item("original") { WallpaperMini("原纸",null,path.isBlank(),!working,onOriginal) }
            items(PaperWallpapers.names.indices.toList(),key={ it }) { index ->
                val assets=LocalContext.current.assets
                val image by produceState<ImageBitmap?>(null,index) { value=withContext(Dispatchers.IO) {
                    assets.open(PaperWallpapers.asset(index)).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=4 })?.asImageBitmap() }
                } }
                WallpaperMini(PaperWallpapers.names[index],image,PaperWallpapers.index(path)==index,!working) { onPreset(index) }
            }
        }
    }
}
@Composable private fun WallpaperMini(name:String,image:ImageBitmap?,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    Column(Modifier.width(76.dp).semantics { contentDescription="纸页壁纸 $name";this.selected=selected }
        .clickable(enabled=enabled,interactionSource=null,indication=null,onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(76.dp,108.dp).shadow(4.dp,RectangleShape).background(Color.White).border(if(selected) 2.dp else .5.dp,if(selected) Color.Black else Color(0xFFD4D4D5),RectangleShape).padding(if(selected) 3.dp else 1.dp)) {
            image?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
        }
        SpaceText(name,12.sp)
    }
}
