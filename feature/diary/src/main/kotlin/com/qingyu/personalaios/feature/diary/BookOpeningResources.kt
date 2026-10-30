package com.qingyu.personalaios.feature.diary

import kotlin.math.abs

internal data class OpeningPageResource(val index:Int,val small:Boolean)

/** Exactly the faces used by the complete opening fan, central artwork first. */
internal object BookOpeningResources {
    fun canWarmWhileClosed(scene:BookScene)=scene.pages.isNotEmpty() && scene.open==0f &&
        scene.turn==0f && scene.overview==0f && scene.libraryOffset==0f && scene.libraryRecoil==0f &&
        scene.creationProgress>=1f && scene.removingId==null && scene.closing<0f && !scene.preparingOpen
    fun sameArtwork(a:BookScene?,b:BookScene)=a!=null && a.journal==b.journal && a.pages===b.pages &&
        a.journals===b.journals && a.current==b.current && a.pendingJournal==b.pendingJournal && a.pendingPage==b.pendingPage
    fun pages(current:Int,count:Int,direction:Int=1):List<OpeningPageResource> {
        if(count<=0)return emptyList()
        val centre=current.coerceIn(0,count-1)
        val indices=AlbumLeafGeometry.visibleLeaves(centre,count).flatMap { leaf ->
            listOfNotNull(AlbumLeafGeometry.frontPage(leaf,count),AlbumLeafGeometry.backPage(leaf,count))
        }.distinct()
        return indices.sortedWith(compareBy<Int> { abs(it-centre) }.thenBy { if((it-centre)*direction>=0) 0 else 1 })
            .map { OpeningPageResource(it,abs(it-centre)>1) }
    }
}
