package com.qingyu.personalaios.feature.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.core.glass.*

@Composable fun SettingsScreen(background:String?,working:Boolean,error:String?,onBackground:(String?)->Unit,onBack:()->Unit,onGlass:()->Unit) {
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onBackground(it.toString()) } }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=AppSpacing.Page,vertical=24.dp)) {
  GlassButton(onBack,Modifier.size(AppControls.Circle).semantics { contentDescription="返回空间" },GlassGeometry.Circle) { GlassChevron() }
  Spacer(Modifier.height(24.dp));GlassText("设置",32.sp)
  Spacer(Modifier.height(36.dp));SpaceText("背景",22.sp)
  Spacer(Modifier.height(12.dp));SpaceText(if(background==null) "随天气变化" else "你的照片",14.sp,alpha=.7f)
  Spacer(Modifier.height(22.dp))
  GlassButton({ if(!working) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },Modifier.fillMaxWidth().semantics { contentDescription="选择背景图片" }) {
   SpaceText(if(working) "正在导入…" else "选择背景图片",17.sp,Modifier.padding(18.dp))
  }
  if(background!=null) {
   Spacer(Modifier.height(14.dp))
   GlassButton({ if(!working) onBackground(null) },Modifier.fillMaxWidth().semantics { contentDescription="恢复天气背景" }) { SpaceText("恢复天气背景",17.sp,Modifier.padding(18.dp)) }
  }
  error?.let { Spacer(Modifier.height(16.dp));SpaceText(it,14.sp,alpha=.8f) }
  Spacer(Modifier.height(30.dp))
  GlassButton(onGlass,Modifier.fillMaxWidth().semantics { contentDescription="液态玻璃调节" }) {
   SpaceText("液态玻璃",17.sp,Modifier.padding(18.dp))
  }
  Spacer(Modifier.height(40.dp));SpaceText("Doki",24.sp)
  Spacer(Modifier.height(10.dp));SpaceText("Weather · Journal · Calendar · Space",14.sp,alpha=.65f)
  Spacer(Modifier.height(LocalPageBottomInset.current))
 }
}
