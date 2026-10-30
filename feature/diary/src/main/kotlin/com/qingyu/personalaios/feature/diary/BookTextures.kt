package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*
import kotlin.math.roundToInt

internal object BookTextures {
    fun cover(journal:DiaryJournal):Bitmap {
        val bitmap=Bitmap.createBitmap(768,1152,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(2.4f,2.4f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Preserve customized journals; the previous untouched default adopts the reference finish.
        val base=(if(journal.coverColor==0xFF73858AL) 0xFFB5A17E else journal.coverColor).toInt()
        fun shade(f:Float)=Color.rgb((Color.red(base)*f).toInt().coerceIn(0,255),(Color.green(base)*f).toInt().coerceIn(0,255),(Color.blue(base)*f).toInt().coerceIn(0,255))
        paint.shader=LinearGradient(0f,0f,320f,480f,intArrayOf(shade(1.08f),base,shade(.96f)),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,320f,480f,paint);paint.shader=null
        // Cached fine cloth grain; no per-frame bitmap generation.
        paint.color=0x07FFFFFF;paint.strokeWidth=.45f
        for(y in 0..480 step 2) canvas.drawLine(0f,y.toFloat(),320f,y+.4f,paint)
        paint.color=0x06000000
        for(x in 0..320 step 2) canvas.drawLine(x.toFloat(),0f,x+.4f,480f,paint)
        paint.color=Color.WHITE // Grain opacity must not leak into photos or the spine shader.
        if(journal.coverPath.isNotBlank()) DiaryImages.load(journal.coverPath)?.let { image ->
            val ratio=maxOf(320f/image.width,480f/image.height);val w=image.width*ratio;val h=image.height*ratio
            canvas.drawBitmap(image,null,RectF((320-w)/2,(480-h)/2,(320+w)/2,(480+h)/2),paint)
        }
        val band=(if(journal.bandColor==0xFF34434BL) 0xFFFFBA00 else journal.bandColor).toInt()
        fun bandShade(f:Float)=Color.rgb((Color.red(band)*f).toInt().coerceIn(0,255),(Color.green(band)*f).toInt().coerceIn(0,255),(Color.blue(band)*f).toInt().coerceIn(0,255))
        paint.shader=LinearGradient(0f,0f,38f,0f,intArrayOf(bandShade(.88f),bandShade(1.16f),bandShade(.82f),bandShade(1.08f),band),floatArrayOf(0f,.15f,.3f,.48f,1f),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,38f,480f,paint);paint.shader=null
        paint.color=0x4CFFFFFF;paint.strokeWidth=.8f;canvas.drawLine(38f,0f,38f,480f,paint)
        paint.color=0x24FFFFFF;canvas.drawLine(7f,0f,7f,480f,paint)
        return bitmap // Journal names belong above the book; plain covers stay clean.
    }
    fun page(page:DiaryPage,small:Boolean=false):Bitmap {
        val artwork=PageArtwork.bitmap(page,if(page.singleFace) { if(small) 240 else 480 } else { if(small) 480 else 960 },notes=true)
        val physicalHeight=(1280f*BookGeometry.HalfHeight/BookGeometry.HalfWidth).roundToInt()
        val result=Bitmap.createBitmap(if(small) 640 else 1280,if(small) physicalHeight/2 else physicalHeight,Bitmap.Config.ARGB_8888)
        val target=Canvas(result);target.drawColor(PageArtwork.Paper)
        if(small) target.scale(.5f,.5f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Page contents stay upright in portrait browsing and landscape immersion.
        // Fit the stored aspect; rotating a portrait drawing makes its notes unreadable.
        val w=minOf(1280f,physicalHeight*page.canvasAspect);val h=w/page.canvasAspect
        val photo=page.stickers.any { it.fillPage }
        // Each diary face retains portrait coordinates. Existing entries remain
        // on the right and the left face has its own ruled artwork and content.
        val destination=if(page.singleFace) RectF(640f,0f,1280f,physicalHeight.toFloat())
            else if(photo) RectF(0f,0f,1280f,physicalHeight.toFloat())
            else RectF((1280-w)/2,(physicalHeight-h)/2,(1280+w)/2,(physicalHeight+h)/2)
        target.drawBitmap(artwork,null,destination,paint)
        if(page.singleFace) {
            val left=PageArtwork.bitmap(DiaryFaces.page(page,PaperFace.LEFT),if(small) 240 else 480,notes=true)
            target.drawBitmap(left,null,RectF(0f,0f,640f,physicalHeight.toFloat()),paint)
            left.recycle()
        }
        artwork.recycle();return result
    }
}
