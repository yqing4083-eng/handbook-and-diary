package com.qingyu.personalaios.feature.diary

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pixel-aligned baselines shared by live text, paper guides and saved artwork. */
internal object PaperRules {
    fun gap(width:Float)=ceil(width.roundToInt()/12f).coerceAtLeast(1f)
    fun title(width:Float)=gap(width)
    fun body(width:Float)=gap(width)*2
    fun font(width:Float)=gap(width)*2f/3f
    fun clearance(width:Float)=gap(width)*.08f
    fun baseline(width:Float,row:Int)=row*gap(width)-clearance(width)
    fun bodyViewport(width:Float)=gap(width)*1.25f
    fun bodyLine(width:Float,y:Float,scroll:Int)=((y+scroll-body(width))/gap(width)).roundToInt().coerceAtLeast(0)
    fun inset(width:Float)=width*.015f
    fun textX(width:Float,x:Float)=(width*x).roundToInt()
    fun textRight(width:Float)=textX(width,.935f)
    fun textWidth(width:Float,x:Float)=(textRight(width)-textX(width,x)).coerceAtLeast(1)
    fun lastRow(width:Float,height:Float)=kotlin.math.floor((height-gap(width)*.5f)/gap(width)).toInt().coerceAtLeast(1)
    fun row(width:Float,height:Float,y:Float)= (y/gap(width)).roundToInt().coerceIn(1,lastRow(width,height))
    fun writingRow(width:Float,height:Float,y:Float,scroll:Int):Int {
        if(y<bodyViewport(width)) return 1
        val last=(lastRow(width,height)+ceil(scroll/gap(width)).toInt()).coerceIn(2,10000)
        return ((y+scroll)/gap(width)).roundToInt().coerceIn(2,last)
    }
}

/** Blank physical rows need paragraph breaks before an IME can address them.
 * Wrapped visual rows already supplied by TextLayoutResult never add newlines. */
internal object RuledCursor {
    fun materialize(text:String,visualLines:Int,target:Int)=text+"\n".repeat((target-visualLines+1).coerceAtLeast(0))
}
