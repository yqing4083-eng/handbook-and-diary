package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Distance-spaced deposition, independent of touch sampling frequency. Live
 * ink and saved artwork use this identical painter and deterministic brush tips. */
internal class BrushStrokeRaster(private val canvas:Canvas,private val tool:InkTool,color:Long,
    private val width:Float,private val pageWidth:Float,private val pageHeight:Float,sampleScale:Float=1f,grainScale:Float=sampleScale) {
    private val alpha=Color.alpha(color.toInt())/255f
    private val baseRadius=width*pageWidth*when(tool) {
        InkTool.WATERCOLOR,InkTool.BLEND->1.6f;InkTool.MARKER->1.1f;InkTool.PENCIL->.4f;InkTool.CRAYON->.7f;InkTool.ERASER->1f;else->.5f
    }
    private val spacing=(baseRadius*when(tool) { InkTool.MARKER->.18f;InkTool.WATERCOLOR,InkTool.BLEND->.24f;else->.3f }).coerceAtLeast(.35f*sampleScale)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        if(tool==InkTool.ERASER) { blendMode=BlendMode.CLEAR;colorFilter=null;this.color=Color.BLACK }
        else colorFilter=PorterDuffColorFilter(color.toInt() or 0xFF000000.toInt(),PorterDuff.Mode.SRC_IN)
    }
    private val target=RectF()
    private val tip=if(tool==InkTool.ERASER || tool==InkTool.CRAYON) null else BrushTips[tool]
    private val wax=if(tool==InkTool.CRAYON) Array(8) { WaxCrayonGrain.shader(color,it,grainScale) } else null
    private var previous:InkPoint?=null
    private var remaining=0f
    var deposits=0;private set
    fun add(point:InkPoint) {
        val a=previous
        if(a==null) { dab(point);remaining=spacing;previous=point;return }
        val dx=(point.x-a.x)*pageWidth;val dy=(point.y-a.y)*pageHeight;val length=hypot(dx,dy)
        if(length>.0001f) {
            var distance=remaining
            while(distance<=length) {
                val t=distance/length
                dab(InkPoint(a.x+(point.x-a.x)*t,a.y+(point.y-a.y)*t,a.pressure+(point.pressure-a.pressure)*t))
                distance+=spacing
            }
            remaining=distance-length
        }
        previous=point
    }
    private fun dab(point:InkPoint) {
        val pressure=point.pressure.coerceIn(.15f,1.8f)
        val radius=baseRadius*when(tool) {
            InkTool.FOUNTAIN,InkTool.INK->.30f+.72f*pressure
            InkTool.WATERCOLOR,InkTool.BLEND->.70f+.32f*pressure
            else->1f
        }
        val x=point.x*pageWidth;val y=point.y*pageHeight
        deposits++
        if(tool==InkTool.ERASER) { canvas.drawCircle(x,y,radius,paint);return }
        if(tool==InkTool.CRAYON) {
            paint.colorFilter=null;paint.shader=checkNotNull(wax)[((pressure-.15f)/1.65f*7).roundToInt().coerceIn(0,7)]
            paint.alpha=(255*alpha*.36f*pressure).roundToInt().coerceIn(1,255)
            canvas.drawCircle(x,y,radius,paint);return
        }
        val flow=when(tool) {
            InkTool.WATERCOLOR->.09f;InkTool.BLEND->.035f;InkTool.MARKER->.085f
            InkTool.PENCIL->.18f;InkTool.INK->.75f;InkTool.TECHNICAL,InkTool.FELT->1f;InkTool.FOUNTAIN->.26f;else->.36f
        }
        paint.alpha=(255*alpha*flow*(if(tool==InkTool.PENCIL) pressure else 1f)).roundToInt().coerceIn(1,255)
        target.set(x-radius,y-radius,x+radius,y+radius)
        canvas.drawBitmap(checkNotNull(tip),null,target,paint)
    }
}

/** A small shared alpha atlas, never a new shader/bitmap for every dab. */
private object BrushTips {
    private val masks=HashMap<InkTool,Bitmap>()
    @Synchronized operator fun get(tool:InkTool)=masks.getOrPut(tool) {
        val n=96;val pixels=IntArray(n*n)
        for(y in 0 until n) for(x in 0 until n) {
            val u=(x+.5f)/n*2-1;val v=(y+.5f)/n*2-1;val r=hypot(u,v)
            val noise=((x*73856093 xor y*19349663) ushr 1)%1000/1000f
            val coverage=when(tool) {
                InkTool.WATERCOLOR,InkTool.BLEND -> {
                    // Wet feathering with pigment/grain at the outer drying edge.
                    val edge=(1-r).coerceIn(0f,1f)
                    val core=(1-r*r).coerceIn(0f,1f)*.62f
                    val rim=exp(-((r-.78f)/.12f).pow(2))*.42f
                    (core+rim)*min(1f,edge*12)*(.68f+.32f*noise)
                }
                InkTool.MARKER -> {
                    val a=u*.866f+v*.5f;val b=-u*.5f+v*.866f
                    min((1-abs(a))*20,(.32f-abs(b))*35).coerceIn(0f,1f)*(.90f+.10f*noise)
                }
                InkTool.PENCIL -> (1-r).coerceIn(0f,1f).pow(.35f)*if(noise>.23f) (.25f+.75f*noise) else .025f
                else -> ((1-r)*18).coerceIn(0f,1f)
            }
            pixels[y*n+x]=Color.argb((coverage*255).roundToInt().coerceIn(0,255),255,255,255)
        }
        Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888)
    }
}
