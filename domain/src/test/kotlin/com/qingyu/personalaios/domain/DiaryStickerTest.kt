package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class DiaryStickerTest {
    @Test fun imagesCanCoverPaperWithoutChangingAspect() {
        val original=DiarySticker(path="image.png",width=.4f,height=.6f)
        val result=original.transformed(dx=10f,dy=-10f,zoom=50f,rotationDelta=405f)
        assertEquals(original.width/original.height,result.width/result.height,.0001f)
        assertEquals(0f,result.x,.0001f);assertEquals(1f-result.height,result.y,.0001f)
        assertEquals(45f,result.rotation,.0001f)
        assertEquals(6f,result.height,.0001f)
        assertTrue(result.width>=1f && result.height>=1f)
    }
    @Test fun zoomKeepsFingerAnchorWhileCrossingPaperBounds() {
        val s=DiarySticker(path="image.png",x=.2f,y=.2f,width=.4f,height=.4f)
        val r=s.transformed(zoom=2f,pivotX=.25f,pivotY=.25f)
        assertEquals(s.x+s.width*.25f,r.x+r.width*.25f,.0001f)
        assertEquals(s.y+s.height*.25f,r.y+r.height*.25f,.0001f)
    }
    @Test fun textAndTapeRetainTheirExistingPageBounds() {
        val s=DiarySticker(path="",kind=StickerKind.TAPE,width=.4f,height=.6f)
        val r=s.transformed(zoom=20f,dx=10f)
        assertEquals(1f,r.height,.0001f);assertTrue(r.x>=0 && r.x+r.width<=1)
    }
    @Test fun shrinkRetainsCentreAndSmallestEdgeRemainsTouchable() {
        val s=DiarySticker(path="image.png",width=.4f,height=.6f)
        val r=s.transformed(zoom=.0001f)
        assertEquals(.08f,r.width,.0001f)
        assertEquals(s.x+s.width/2,r.x+r.width/2,.0001f)
        assertEquals(s.y+s.height/2,r.y+r.height/2,.0001f)
    }
    @Test fun editingTextRetainsImagePlacementInPageCopy() {
        val sticker=DiarySticker(path="image.png").transformed(.1f,.1f,1.2f,15f)
        val page=DiaryPage(order=0,stickers=listOf(sticker))
        assertEquals(listOf(sticker),page.copy(body="words",strokes=listOf(InkStroke(points=listOf(InkPoint(.2f,.2f))))).stickers)
    }
}
