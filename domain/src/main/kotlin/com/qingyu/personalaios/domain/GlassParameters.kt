package com.qingyu.personalaios.domain

/** User optical preferences, independent of weather and device quality. */
data class GlassParameters(
    val refraction: Float = 1.8f,
    val blur: Float = 4f,
    val highlight: Float = .85f,
    val magnification: Float = 1.12f,
    val tint: Float = .07f,
    val chromaticAberration: Boolean = true,
    val interaction: Float = 1f,
    val chromaticStrength:Float=1f,
) {
    fun normalized() = copy(
        refraction = refraction.bounded(0f, 3f, 1.8f),
        blur = blur.bounded(0f, 16f, 4f),
        highlight = highlight.bounded(0f, 1f, .85f),
        magnification = magnification.bounded(1f, 1.3f, 1.12f),
        tint = tint.bounded(0f, .25f, .07f),
        interaction = interaction.bounded(0f, 1.5f, 1f),
        chromaticStrength=chromaticStrength.bounded(0f,3f,1f),
    )

    companion object {
        val Unified = GlassParameters(1f, 0f, 1f, 1.04f, 0f, true, .7f)
        val ButtonDefault=GlassParameters(1f,0f,1f,1.05f,0f,false,1f)
        val NumberDefault=GlassParameters(3f,0f,1f,1f,0f,false,.7f)
        val Clear = GlassParameters(.8f, 1f, .55f, 1.04f, .025f, false, .65f)
        val Strong = GlassParameters(2.6f, 5f, 1f, 1.22f, .09f, true, 1.3f)
    }
}

/** One resolved policy for surfaces, buttons, slider thumbs and navigation. */
data class GlassOptics(val refraction: Float, val blur: Float, val highlight: Float,
    val magnification: Float, val tint: Float, val chromatic: Boolean, val interaction: Float,
    val dispersion:Float=if(chromatic) 1f else 0f)

fun GlassParameters.resolve(qualityScale: Float, motionEnabled: Boolean, supportsChromatic: Boolean): GlassOptics {
    val safe = normalized()
    val quality = qualityScale.bounded(0f, 1f, 1f)
    val chromatic=safe.chromaticAberration && supportsChromatic && safe.chromaticStrength>0f
    return GlassOptics(safe.refraction * quality, safe.blur * quality, safe.highlight,
        1f + (safe.magnification - 1f) * quality, safe.tint,
        chromatic, if (motionEnabled) safe.interaction else 0f,if(chromatic) safe.chromaticStrength*quality else 0f)
}

private fun Float.bounded(min: Float, max: Float, fallback: Float) =
    if (isFinite()) coerceIn(min, max) else fallback
