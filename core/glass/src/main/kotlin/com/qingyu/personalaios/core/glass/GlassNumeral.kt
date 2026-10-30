package com.qingyu.personalaios.core.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect

private class Glyph(val width: Int, val height: Int, val mask: BitmapShader,val coverage:BitmapShader) : Shape {
    // Glyph coverage is applied inside the refraction shader. A rectangular
    // outline avoids re-stencilling a large, concave font path every frame.
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f,0f,size.width,size.height))
}

/** Actual weather sampling clipped to glyphs; normals come from the soft glyph mask. */
@Composable fun GlassNumeral(value: String, size: TextUnit, modifier: Modifier = Modifier, liquidDigits:Boolean=true) {
    val light = LocalGlassLight.current
    val environment = checkNotNull(LocalGlassEnvironment.current)
    val density = LocalDensity.current
    val pixels = with(density) { size.toPx() }
    val glyph = remember(value, pixels, density.density) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = pixels; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val native = android.graphics.Path(); paint.getTextPath(value, 0, value.length, 0f, 0f, native)
        val bounds = android.graphics.RectF(); native.computeBounds(bounds, true)
        val inset = with(density) { 4.dp.toPx() }
        native.offset(inset - bounds.left, inset - bounds.top)
        val width = (bounds.width() + inset * 2).toInt().coerceAtLeast(1)
        val height = (bounds.height() + inset * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        paint.color = android.graphics.Color.WHITE
        paint.maskFilter = BlurMaskFilter(inset * .65f, BlurMaskFilter.Blur.NORMAL)
        android.graphics.Canvas(bitmap).drawPath(native, paint)
        val attributes=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(attributes)
        paint.maskFilter=null;paint.color=android.graphics.Color.RED
        canvas.drawPath(native,paint)
        paint.color=android.graphics.Color.GREEN;paint.style=Paint.Style.STROKE
        paint.strokeWidth=with(density) { .75.dp.toPx() }
        paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
        canvas.drawPath(native,paint)
        Glyph(width,height,BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),BitmapShader(attributes,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP))
    }
    val optics = environment.numeralOptics
    Box(modifier.size(with(density) { glyph.width.toDp() }, with(density) { glyph.height.toDp() })
        .semantics { text = AnnotatedString(value) }.drawBackdrop(environment.backdrop.upstream, shape = { glyph },
            effects = {
                if(optics.blur>0f) blur(optics.blur.dp.toPx())
                val shader = obtainRuntimeShader("GlyphRefraction", GLYPH_SHADER)
                shader.setInputShader("glyph", glyph.mask)
                shader.setInputShader("coverage",glyph.coverage)
                shader.setFloatUniform("offset", -padding, -padding)
                shader.setFloatUniform("extent", this.size.width, this.size.height)
                shader.setFloatUniform("depth", optics.refraction * 8.dp.toPx())
                shader.setFloatUniform("mag", optics.magnification)
                // Neutral clear glass in both modes: no wallpaper-color or ink-darkening policy.
                shader.setFloatUniform("shine", optics.highlight)
                val radians=light.value*kotlin.math.PI.toFloat()/180f
                shader.setFloatUniform("light", -kotlin.math.cos(radians), -kotlin.math.sin(radians))
                shader.setFloatUniform("dispersion", optics.dispersion*1.dp.toPx())
                val tint=Color(environment.theme.glassTint)
                shader.setFloatUniform("tintRGB",tint.red,tint.green,tint.blue)
                shader.setFloatUniform("tintAlpha",optics.tint)
                effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
            }, highlight = { null }, shadow = null))
}

/** Kept for existing call sites; headings now use ordinary typography. */
@Composable fun GlassText(value: String, size: TextUnit, modifier: Modifier = Modifier) =
    com.qingyu.personalaios.core.ui.SpaceText(value,size,modifier,weight=androidx.compose.ui.text.font.FontWeight.Medium)

private const val GLYPH_SHADER = """
uniform shader content;
uniform shader glyph;
uniform shader coverage;
uniform float2 offset;
uniform float2 extent;
uniform float depth;
uniform float mag;
uniform float shine;
uniform float dispersion;
uniform float2 light;
uniform float3 tintRGB;
uniform float tintAlpha;
half4 main(float2 xy) {
    float2 p = xy + offset;
    half4 attributes = coverage.eval(p);
    float alpha = attributes.r;
    if (alpha <= 0.) return half4(0);
    float a = glyph.eval(p).a;
    float2 g = float2(glyph.eval(p + float2(2,0)).a - glyph.eval(p - float2(2,0)).a,
                     glyph.eval(p + float2(0,2)).a - glyph.eval(p - float2(0,2)).a);
    float len = length(g);
    float2 n = g / max(len, .001);
    float2 q = (p - extent * .5) / mag + extent * .5 - offset + n * depth * (1-a);
    half4 c = content.eval(q);
    if (dispersion > 0.) {
        c.r = content.eval(q + n * dispersion).r;
        c.b = content.eval(q - n * dispersion).b;
    }
    float specular = max(0., dot(n, normalize(light))) * min(1., len * 3.);
    c.rgb = mix(c.rgb, half3(1), half(shine * specular * .65));
    c.rgb = mix(c.rgb, half3(tintRGB), half(tintAlpha));
    float fillT = clamp(dot(p, extent) / max(dot(extent, extent), 1.), 0., 1.);
    float fill = fillT < .5 ? mix(.28, .06, fillT * 2.) : mix(.06, .16, (fillT - .5) * 2.);
    c.rgb = mix(c.rgb, half3(1), half(fill));
    float edgeT = clamp(.5 + dot(p - extent * .5, normalize(light)) / (2. * max(extent.x, extent.y)), 0., 1.);
    float edge = (edgeT < .5 ? mix(.95, .05, edgeT * 2.) : mix(.05, .45, (edgeT - .5) * 2.)) * shine * attributes.g;
    c.rgb = mix(c.rgb, half3(1), half(edge));
    return half4(c.rgb * half(alpha), half(alpha));
}
"""
