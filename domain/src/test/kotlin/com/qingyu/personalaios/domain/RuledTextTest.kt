package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class RuledTextTest {
    @Test fun everyUserStickerFitsPortraitAndLandscapeWithoutStretching() {
        assertEquals(40,StickerArt.entries.size)
        for(art in StickerArt.entries) for(aspect in listOf(.675f,1.35f,1.8f)) {
            val (w,h)=stickerSize(art,aspect)
            val sticker=DiarySticker(path="fixture.png",kind=StickerKind.STICKER,width=w,height=h,x=(1-w)/2,y=(1-h)/2)
            assertEquals((art.right-art.left).toFloat()/(art.bottom-art.top),sticker.width/(sticker.height/aspect),.00001f)
            assertTrue(h<=.361f)
        }
    }
    @Test fun positionedTextRoundTripsWithoutPaddingSpaces() {
        val runs=listOf(RuledParagraph("middle",6,.5f,"居中起点\n下一行"),RuledParagraph("right",3,.8f,"<&>♡"))
        assertEquals(runs,RuledText.decode(RuledText.encode(runs)))
        assertEquals("<&>♡\n居中起点\n下一行",RuledText.plain(runs))
        assertFalse(RuledText.plain(runs).contains("  "))
        assertEquals(.5f,RuledText.titleX(RuledText.encode(runs,.5f)),0f)
    }
    @Test fun existingPlainTextStartsOnItsOriginalBodyRule() {
        val page=DiaryPage(order=0,body="以前的日记\n仍在")
        val block=RuledText.initial(page).single()
        assertEquals(2,block.row);assertEquals(.065f,block.x,0f);assertEquals(page.body,block.text)
        assertNull(RuledText.decode("<p>previous rich text</p>"))
    }
    @Test fun bothFacesKeepIndependentAnchors() {
        val sheet=DiaryPage(order=0,singleFace=true)
        val left=listOf(RuledParagraph("left",4,.6f,"左面"))
        val edited=DiaryFaces.page(sheet,PaperFace.LEFT).copy(body=RuledText.plain(left),bodyHtml=RuledText.encode(left))
        val result=DiaryFaces.replace(sheet,PaperFace.LEFT,edited)
        assertEquals("",result.body);assertEquals(left,RuledText.initial(DiaryFaces.page(result,PaperFace.LEFT)))
    }
}
