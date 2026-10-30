package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class PaperLayersTest {
    @Test fun laterPhotoCannotCoverExistingSticker() {
        val sticker=DiarySticker(id="sticker",path="sticker.png",kind=StickerKind.STICKER)
        val photo=DiarySticker(id="photo",path="photo.png")
        val source=listOf(sticker,photo)
        assertEquals(listOf(photo,sticker),PaperLayers.ordered(source))
        assertEquals(listOf(sticker,photo),source)
    }
    @Test fun relativeOrderWithinBothGroupsIsStable() {
        val a=DiarySticker(id="a",path="a.png");val b=a.copy(id="b")
        val c=a.copy(id="c",kind=StickerKind.STICKER);val d=c.copy(id="d",kind=StickerKind.TEXT,path="",text="♡")
        assertEquals(listOf(a,b,c,d),PaperLayers.ordered(listOf(a,c,b,d)))
        assertEquals(listOf(c,d),PaperLayers.top(listOf(a,c,b,d)))
    }
}
