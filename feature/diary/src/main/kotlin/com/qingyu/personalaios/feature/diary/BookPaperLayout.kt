package com.qingyu.personalaios.feature.diary

internal data class PaperRect(val left:Float,val top:Float,val width:Float,val height:Float) {
    val centreX get()=left+width/2
    val centreY get()=top+height/2
}

/** Expand the selected sheet to the available width at its physical aspect,
 * without adding white viewport margins. Units may be dp or px. */
internal object BookPaperLayout {
    fun fixedWriting(width:Float,height:Float,aspect:Float,top:Float=66f,bottom:Float=104f):PaperRect {
        val ratio=aspect.takeIf { it.isFinite() && it>0f } ?: 1f
        val room=(height-top-bottom).coerceAtLeast(1f)
        val w=minOf(width,room*ratio).coerceAtLeast(1f);val h=w/ratio
        return PaperRect((width-w)/2,top+(room-h)/2,w,h)
    }
    fun portraitEditor(width:Float,height:Float,aspect:Float,centreY:Float=height/2):PaperRect {
        val ratio=aspect.takeIf { it.isFinite() && it>0f } ?: 1f
        val w=width.coerceAtLeast(1f)
        val h=w/ratio
        return PaperRect(0f,centreY-h/2,w,h)
    }
    /** Logical paper bounds before rotation. Uniform scale fills the landscape
     * height, leaving the two side gutters for controls; portrait sheets turn 90°. */
    fun editor(width:Float,height:Float,aspect:Float,sideInset:Float=100f):PaperRect {
        val ratio=aspect.takeIf { it.isFinite() && it>0f } ?: 1f
        val turned=ratio<1f
        val physicalRatio=if(turned) 1f/ratio else ratio
        val h=minOf(height.coerceAtLeast(1f),(width-2*sideInset).coerceAtLeast(1f)/physicalRatio)
        val logicalW=if(turned) h else h*ratio
        val logicalH=logicalW/ratio
        return PaperRect((width-logicalW)/2,(height-logicalH)/2,logicalW,logicalH)
    }
    fun editorRotation(aspect:Float,progress:Float)=if(aspect<1f) 90f*progress.coerceIn(0f,1f) else 0f
    fun flattened(source:PaperRect,width:Float,height:Float,aspect:Float,inset:Float=16f):PaperRect {
        val ratio=aspect.takeIf { it.isFinite() && it>0f } ?: 1f
        val availableW=(width-2*inset).coerceAtLeast(1f)
        val availableH=(height-2*inset).coerceAtLeast(1f)
        val h=minOf(availableH,availableW/ratio)
        val w=h*ratio
        val cy=source.centreY.coerceIn((height-availableH+h)/2,(height+availableH-h)/2)
        return PaperRect((width-w)/2,cy-h/2,w,h)
    }
    fun interpolate(source:PaperRect,target:PaperRect,progress:Float):PaperRect {
        val p=progress.coerceIn(0f,1f)
        return PaperRect(source.left+(target.left-source.left)*p,source.top+(target.top-source.top)*p,
            source.width+(target.width-source.width)*p,source.height+(target.height-source.height)*p)
    }
}
