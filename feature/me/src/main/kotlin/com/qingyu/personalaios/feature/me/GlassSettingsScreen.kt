package com.qingyu.personalaios.feature.me

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.personalaios.core.glass.*
import com.qingyu.personalaios.core.ui.*
import com.qingyu.personalaios.domain.GlassParameters
import com.qingyu.personalaios.domain.GlassTarget
import com.qingyu.personalaios.domain.GlassGroups
import java.util.Locale

@Composable
fun GlassSettingsScreen(
    parameters: GlassParameters,
    target:GlassTarget,
    onChange: (GlassParameters) -> Unit,
    onBack: () -> Unit,
    onLeave: () -> Unit,
    onOpenLab: (() -> Unit)? = null,
) {
    DisposableEffect(Unit) { onDispose { onLeave() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = AppSpacing.Page, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(onBack, Modifier.size(AppControls.Circle)
                .semantics { contentDescription = "返回玻璃分类" }, GlassGeometry.Circle) {
                GlassChevron()
            }
            Spacer(Modifier.width(14.dp))
            SpaceText(target.title(), 30.sp, weight = FontWeight.Light)
        }
        Spacer(Modifier.height(16.dp))
        SpaceText("让光线穿过，按你的喜好折射。", 14.sp, alpha = .7f)
        Spacer(Modifier.height(18.dp))
        GlassTuningPreview(Modifier.fillMaxWidth().semantics { contentDescription = "点击体验当前玻璃强度" },target)
        Spacer(Modifier.height(12.dp))
        SpaceText("实时生效 · 自动保存 · 仅调整当前分类", 12.sp, alpha = .65f)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("清透" to GlassParameters.Clear, "强烈" to GlassParameters.Strong,
                "恢复默认" to GlassGroups.defaultFor(target)).forEach { (label, preset) ->
                GlassButton({ onChange(preset) }, Modifier.weight(1f).heightIn(min = 48.dp),
                    GlassGeometry.Corner(AppCorners.Small)) {
                    SpaceText(label, 14.sp, Modifier.align(Alignment.Center).padding(vertical = 14.dp))
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        GlassSlider("折射", "边缘的光线弯折", parameters.refraction, 0f..3f, "%.2f ×") {
            onChange(parameters.copy(refraction = it))
        }
        GlassSlider("模糊", "玻璃内的柔化程度", parameters.blur, 0f..16f, "%.1f") {
            onChange(parameters.copy(blur = it))
        }
        GlassSlider("放大", "透镜中的局部放大", parameters.magnification, 1f..1.3f, "%.2f ×") {
            onChange(parameters.copy(magnification = it))
        }
        GlassSlider("高光", "玻璃边缘的亮度", parameters.highlight, 0f..1f, "%.0f%%", 100f) {
            onChange(parameters.copy(highlight = it))
        }
        GlassSlider("染色", "玻璃表面的着色程度", parameters.tint, 0f.. .25f, "%.0f%%", 100f) {
            onChange(parameters.copy(tint = it))
        }
        if(target!=GlassTarget.NUMBERS) GlassSlider("液态交互", "触摸时的弹性形变", parameters.interaction, 0f..1.5f, "%.0f%%", 100f) {
            onChange(parameters.copy(interaction = it))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SpaceText("色散", 16.sp)
                Spacer(Modifier.height(6.dp))
                SpaceText("边缘微弱的彩色分光", 12.sp, alpha = .6f)
            }
            GlassButton({ onChange(parameters.copy(chromaticAberration = !parameters.chromaticAberration)) },
                Modifier.width(84.dp).heightIn(min = 48.dp)
                .semantics { contentDescription = "色散"; stateDescription = if (parameters.chromaticAberration) "开启" else "关闭" }
                .semantics { toggleableState = androidx.compose.ui.state.ToggleableState(parameters.chromaticAberration) },
                GlassGeometry.Capsule, role = Role.Switch) {
                SpaceText(if (parameters.chromaticAberration) "开启" else "关闭", 14.sp,
                    Modifier.align(Alignment.Center).padding(vertical = 14.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        GlassSlider("色散强度",if(parameters.chromaticAberration) "彩色分光的幅度 · 100% 为默认强度" else "当前色散已关闭，开启后生效",
            parameters.chromaticStrength,0f..3f,"%.0f%%",100f) {
            onChange(parameters.copy(chromaticStrength=it))
        }
        Spacer(Modifier.height(16.dp))
        SpaceText("各分类独立保存，恢复默认只影响当前分类。", 12.sp, alpha = .6f)
        if (onOpenLab != null) {
            Spacer(Modifier.height(24.dp))
            GlassButton(onOpenLab, Modifier.fillMaxWidth(),
                GlassGeometry.Corner(AppCorners.Medium)) {
                SpaceText("采样对照", 14.sp, Modifier.padding(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp + LocalPageBottomInset.current))
    }
}

/** Foundation gestures and accessibility; the control uses our continuous glass geometry. */
@Composable
private fun GlassSlider(label: String, description: String, value: Float, range: ClosedFloatingPointRange<Float>,
    format: String, displayScale: Float = 1f, onChange: (Float) -> Unit) {
    val text = String.format(Locale.ROOT, format, value * displayScale)
    val foreground = LocalSpaceColors.current.foreground
    var width by remember { mutableIntStateOf(1) }
    val currentChange = rememberUpdatedState(onChange)
    val horizontalGesture = remember { booleanArrayOf(false) }
    val interaction = rememberGlassInteraction()
    val optics = checkNotNull(LocalGlassEnvironment.current).optics
    val density = LocalDensity.current
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val update: (Float) -> Unit = { x ->
        val raw = (x / width).coerceIn(0f, 1f)
        val fractionAtEdge = when { raw <= .005f -> 0f; raw >= .995f -> 1f; else -> raw }
        currentChange.value(range.start + fractionAtEdge * (range.endInclusive - range.start))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SpaceText(label, 16.sp)
            SpaceText(text, 14.sp, alpha = .8f)
        }
        Spacer(Modifier.height(6.dp))
        SpaceText(description, 12.sp, alpha = .6f)
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                contentDescription = label
                stateDescription = text
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { proposed -> currentChange.value(proposed.coerceIn(range)); true }
            }
            .pointerInput(range, width, optics.interaction > 0f) {
                detectTapGestures(onPress = {
                    if (optics.interaction > 0f) interaction.press(Offset(12.dp.toPx(), 12.dp.toPx()))
                    val released = tryAwaitRelease()
                    if (released || !horizontalGesture[0]) interaction.release()
                }) { update(it.x) }
            }
            .pointerInput(range, width) {
                detectHorizontalDragGestures(onDragStart = {
                    horizontalGesture[0] = true; update(it.x)
                    interaction.press(Offset(12.dp.toPx(), 12.dp.toPx()))
                },
                    onDragEnd = { horizontalGesture[0] = false; interaction.release() },
                    onDragCancel = { horizontalGesture[0] = false; interaction.release() }) { change, _ ->
                    change.consume(); update(change.position.x)
                }
            }
            .pointerInput(range, width) {
                // Android may coalesce the last move into UP. Include that final position
                // before the drag detector ends the gesture; vertical scrolling is untouched.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Release && horizontalGesture[0]) {
                            event.changes.firstOrNull()?.let { update(it.position.x) }
                        }
                    }
                }
            }, contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(GlassGeometry.Capsule.shape())
                .background(foreground.copy(alpha = .18f)))
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(3.dp)
                .clip(GlassGeometry.Capsule.shape()).background(foreground.copy(alpha = .75f)))
            GlassSurface(Modifier.offset(x = with(density) { (width * fraction).toDp() } - 12.dp)
                .size(24.dp), GlassGeometry.Capsule, interaction = interaction)
        }
        Spacer(Modifier.height(18.dp))
    }
}
