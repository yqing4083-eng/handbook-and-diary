package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.InkTool

internal object PaperBrushPresets {
    val widths=listOf(.002f,.006f,.014f,.022f,.04f)
    fun initialWidth(tool:InkTool)=when(tool) {
        InkTool.ERASER,InkTool.GLYPH_ERASER->.035f
        else->widths[3]
    }
}
