package com.qingyu.personalaios.domain

/** One selected photo per complete paper spread. Existing written pages are never replaced. */
object PhotoPageFill {
    const val MaxImages=30
    data class Plan(val pages:List<DiaryPage>,val firstId:String)
    data class Crop(val left:Int,val top:Int,val right:Int,val bottom:Int)
    data class Frame(val left:Float,val top:Float,val width:Float,val height:Float)
    /** Original image bounds normalized to its viewport. One uniform scale
     * covers the viewport; the viewport hides overflow, never changes the file. */
    fun cover(imageAspect:Float,viewportAspect:Float):Frame {
        require(imageAspect.isFinite() && imageAspect>0 && viewportAspect.isFinite() && viewportAspect>0)
        val w=maxOf(1f,imageAspect/viewportAspect)
        val h=maxOf(1f,viewportAspect/imageAspect)
        return Frame((1-w)/2,(1-h)/2,w,h)
    }
    private fun empty(page:DiaryPage)=page.title.isBlank() && page.body.isBlank() && page.bodyHtml.isBlank() &&
        page.strokes.isEmpty() && page.stickers.isEmpty() && (page.leftFace?.let {
            it.title.isBlank() && it.body.isBlank() && it.bodyHtml.isBlank() && it.strokes.isEmpty() && it.stickers.isEmpty()
        } ?: true)
    fun plan(pages:List<DiaryPage>,journalId:String,startId:String?,paths:List<String>,kind:BookKind=BookKind.LEGACY):Plan {
        require(paths.size in 1..MaxImages && paths.all { it.isNotBlank() })
        val ordered=pages.filter { it.journalId==journalId && it.deletedAt==0L }.sortedBy { it.order }
        val current=ordered.indexOfFirst { it.id==startId }
        val at=if(current<0) ordered.size else current+if(empty(ordered[current])) 0 else 1
        val reusable=ordered.drop(at).takeWhile(::empty).take(paths.size)
        val photos=paths.mapIndexed { index,path ->
            val base=reusable.getOrNull(index) ?: DiaryPage(order=at+index,journalId=journalId)
            base.copy(style=if(kind==BookKind.DIARY) PaperStyle.LINED else PaperStyle.PLAIN,
                singleFace=kind==BookKind.DIARY,canvasAspect=if(kind==BookKind.DIARY) NewBook.FaceAspect else NewBook.SpreadAspect,
                example=false,revision=base.revision+1,
                stickers=listOf(DiarySticker(path=path,x=0f,y=0f,width=1f,height=1f,fillPage=true)))
        }
        val result=(ordered.take(at)+photos+ordered.drop(at+reusable.size)).mapIndexed { index,page ->
            if(page.order==index) page else page.copy(order=index,revision=page.revision+1)
        }
        return Plan(result,photos.first().id)
    }
    /** Pixel source crop matches Compose ContentScale.Crop; no stretching or margins. */
    fun crop(width:Int,height:Int,aspect:Float):Crop {
        require(width>0 && height>0 && aspect.isFinite() && aspect>0)
        val w=minOf(width,(height*aspect).toInt().coerceAtLeast(1))
        val h=minOf(height,(width/aspect).toInt().coerceAtLeast(1))
        val left=(width-w)/2;val top=(height-h)/2
        return Crop(left,top,left+w,top+h)
    }
}
