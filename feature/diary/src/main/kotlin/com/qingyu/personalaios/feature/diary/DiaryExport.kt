package com.qingyu.personalaios.feature.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.qingyu.personalaios.domain.DiaryPage
import android.text.StaticLayout
import android.text.TextPaint

internal object DiaryExport {
    fun png(context:Context,uri:Uri,page:DiaryPage) {
        val bitmap=PageArtwork.sheetBitmap(page,1200,notes=true)
        try { checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
    }
    fun pdf(context:Context,uri:Uri,pages:List<DiaryPage>) {
        require(pages.isNotEmpty())
        val document=PdfDocument()
        try {
            var number=1
            pages.flatMap { if(it.singleFace) com.qingyu.personalaios.domain.PaperFace.entries.map { face -> com.qingyu.personalaios.domain.DiaryFaces.page(it,face) } else listOf(it) }.forEach { page ->
                val hasText=page.title.isNotBlank() || page.body.isNotBlank()
                val composedPaper=page.singleFace || page.wallpaper.isNotBlank()
                if(composedPaper || page.strokes.isNotEmpty() || page.stickers.isNotEmpty() || !hasText) {
                val bitmap=PageArtwork.bitmap(page,900,notes=composedPaper)
                try {
                    val output=document.startPage(PdfDocument.PageInfo.Builder(bitmap.width,bitmap.height,number++).create())
                    output.canvas.drawBitmap(bitmap,null,RectF(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(output)
                } finally { bitmap.recycle() }
                }
                if(hasText && !composedPaper) {
                    val value:CharSequence=android.text.SpannableStringBuilder().apply {
                        if(page.title.isNotBlank()) append(page.title).append("\n\n")
                        append(if(page.bodyHtml.isNotBlank()) android.text.Html.fromHtml(page.bodyHtml,android.text.Html.FROM_HTML_MODE_COMPACT) else page.body)
                    }
                    val text=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF293844.toInt();textSize=30f }
                    val layout=StaticLayout.Builder.obtain(value,0,value.length,text,836).setLineSpacing(10f,1f).build()
                    var offset=0
                    while(offset<layout.height) {
                        val endLine=layout.getLineForVertical(offset+1088).coerceAtMost(layout.lineCount-1)
                        val next=if(offset+1088>=layout.height) layout.height else layout.getLineTop(endLine).takeIf { it>offset } ?: minOf(layout.height,offset+1088)
                        val output=document.startPage(PdfDocument.PageInfo.Builder(900,1200,number++).create())
                        output.canvas.drawColor(PageArtwork.Paper);output.canvas.save();output.canvas.clipRect(32,56,868,56+next-offset)
                        output.canvas.translate(32f,56f-offset);layout.draw(output.canvas);output.canvas.restore();document.finishPage(output)
                        offset=next
                    }
                }
            }
            checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { document.writeTo(it) }
        } finally { document.close() }
    }
}
