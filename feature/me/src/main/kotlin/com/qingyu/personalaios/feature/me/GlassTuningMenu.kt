package com.qingyu.personalaios.feature.me

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.GlassTarget

internal fun GlassTarget.title()=when(this) {
    GlassTarget.BOTTOM_BAR -> "底栏调节"
    GlassTarget.BUTTONS -> "按钮调节"
    GlassTarget.NUMBERS -> "数字调节"
}

@Composable fun GlassTuningMenu(onBack:()->Unit,onOpen:(GlassTarget)->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=AppSpacing.Page,vertical=24.dp)) {
        GlassButton(onBack,Modifier.size(AppControls.Circle).semantics { contentDescription="返回设置" },GlassGeometry.Circle) { GlassChevron() }
        Spacer(Modifier.height(24.dp))
        SpaceText("液态玻璃",30.sp)
        Spacer(Modifier.height(12.dp))
        SpaceText("三组材质，独立调节。",14.sp,alpha=.7f)
        Spacer(Modifier.height(30.dp))
        GlassTarget.entries.forEach { target ->
            GlassButton({ onOpen(target) },Modifier.fillMaxWidth().semantics { contentDescription=target.title() }) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    SpaceText(target.title(),18.sp)
                    SpaceText(when(target) {
                        GlassTarget.BOTTOM_BAR -> "底部导航与移动透镜"
                        GlassTarget.BUTTONS -> "功能按钮、浮层与玻璃区域"
                        GlassTarget.NUMBERS -> "日期、时间、温度与星期"
                    },12.sp,Modifier.padding(top=8.dp),alpha=.65f)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
