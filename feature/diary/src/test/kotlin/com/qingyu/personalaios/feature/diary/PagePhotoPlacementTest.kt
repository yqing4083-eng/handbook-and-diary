package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class PagePhotoPlacementTest {
    @Test fun editBoundsIncludeHiddenEdgesAndRemainStableAfterSave() {
        val stored=DiarySticker(path="original.jpg",x=0f,y=0f,width=1f,height=1f,fillPage=true)
        for(aspect in listOf(.1f,.25f,1f,4f,10f)) {
            val fitted=StickerPlacement.fitPageImage(stored,aspect,NewBook.FaceAspect)
            assertEquals(aspect,fitted.width*NewBook.FaceAspect/fitted.height,.00001f)
            assertTrue(fitted.width>=1f && fitted.height>=1f)
            assertEquals(stored.path,fitted.path);assertTrue(fitted.crop.isEmpty())
            val again=StickerPlacement.fitPageImage(fitted,aspect,NewBook.FaceAspect)
            assertEquals(fitted.width,again.width,.00001f);assertEquals(fitted.height,again.height,.00001f)
            val small=StickerPlacement.move(fitted,0f,0f,.1f,0f,.5f,.5f,NewBook.FaceAspect)
            assertEquals(aspect,small.width*NewBook.FaceAspect/small.height,.00001f)
        }
    }
    @Test fun ordinaryPicturesAndStickersKeepTheirExistingTransforms() {
        val original=DiarySticker(path="sticker.png",width=.4f,height=.6f)
        assertEquals(original,StickerPlacement.fitPageImage(original,4f,.675f))
        val sticker=original.copy(kind=StickerKind.STICKER,fillPage=true)
        assertEquals(sticker,StickerPlacement.fitPageImage(sticker,4f,.675f))
    }
}
