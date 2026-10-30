package com.qingyu.personalaios.feature.diary

/** Bounds work by visible content, not the size of the user's library. */
internal object BookRenderPolicy {
    /** Reject the same planar face as Vulkan before serializing its draw.
     * Model includes the camera translation; n dot -position is perspective-correct. */
    fun faceVisible(model:FloatArray,cull:Int):Boolean {
        if(cull==0) return true
        val facing=-(model[8]*model[12]+model[9]*model[13]+model[10]*model[14])
        if(kotlin.math.abs(facing)<.00001f) return true
        return if(cull==2) facing>0f else facing<0f
    }
    fun shelfVisible(rect:ClosedBookRect,width:Float,height:Float):Boolean {
        // Include soft ground shadows and the shallow pitch's corner projection.
        val guard=maxOf(width*.22f,48f)
        return rect.right>=-guard && rect.left<=width+guard && rect.bottom>=-guard && rect.top<=height+guard
    }
    fun displayRate(actual:Float)=actual.takeIf { it.isFinite() && it>0f } ?: 60f
    fun preloadPages(scene:BookScene,warmOpening:Boolean)=scene.open>0f || scene.preparingOpen || scene.overview>0f || warmOpening
    fun hasContent(scene:BookScene)=scene.journals.any { it.id==scene.journal.id && it.deletedAt==0L } ||
        scene.thumbnail && scene.pages.isNotEmpty()
    fun uploadCount(scene:BookScene)=if(scene.libraryOffset!=0f || scene.libraryRecoil!=0f ||
        scene.turn!=0f || scene.open>0f && scene.open<1f || scene.overview>0f ||
        scene.creationProgress<1f || scene.removingId!=null) 1 else 2
    fun shelf(scene:BookScene):List<Pair<Int,com.qingyu.personalaios.domain.DiaryJournal>> {
        val current=scene.journals.indexOfFirst { it.id==scene.journal.id && it.deletedAt==0L }
        if(current<0) return emptyList()
        return listOf(-2,2,-1,1,0).mapNotNull { delta ->
            scene.journals.getOrNull(current+delta)?.takeIf { it.deletedAt==0L }?.let { delta to it }
        }
    }
}
