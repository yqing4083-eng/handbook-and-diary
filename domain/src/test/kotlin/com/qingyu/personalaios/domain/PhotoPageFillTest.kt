package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class PhotoPageFillTest {
    @Test fun coverUsesOneUniformScaleForPortraitLandscapeSquareAndPanorama() {
        for(image in listOf(.1f,.25f,.5625f,1f,1.7778f,4f,10f)) for(paper in listOf(NewBook.FaceAspect,NewBook.SpreadAspect)) {
            val frame=PhotoPageFill.cover(image,paper)
            assertTrue(frame.width>=1f && frame.height>=1f)
            assertEquals(image,frame.width*paper/frame.height,.00001f)
            assertEquals(.5f,frame.left+frame.width/2,.00001f)
            assertEquals(.5f,frame.top+frame.height/2,.00001f)
            assertTrue(frame.width==1f || frame.height==1f)
        }
    }
    @Test fun leftFaceContentPreventsAVisuallyWrittenSheetBeingReusedAsBlank() {
        val page=DiaryPage(id="written-left",order=0,leftFace=DiaryFaceContent(body="preserve me"))
        val plan=PhotoPageFill.plan(listOf(page),"main",page.id,listOf("photo.png"))
        assertEquals(page,plan.pages.first())
        assertEquals(2,plan.pages.size)
        assertNotEquals(page.id,plan.firstId)
    }
    @Test fun oneBlankPaperExpandsToTenPhotosWithoutDuplicatingTheBlank() {
        val original=DiaryPage(id="blank",order=0,style=PaperStyle.LINED)
        val paths=(1..10).map { "$it.png" }
        val plan=PhotoPageFill.plan(listOf(original),"main",original.id,paths)
        assertEquals(10,plan.pages.size);assertEquals("blank",plan.firstId)
        assertEquals(paths,plan.pages.map { it.stickers.single().path })
        assertEquals((0..9).toList(),plan.pages.map { it.order })
        plan.pages.forEach { page ->
            assertEquals(PaperStyle.PLAIN,page.style)
            val photo=page.stickers.single()
            assertEquals(0f,photo.x,0f);assertEquals(0f,photo.y,0f)
            assertEquals(1f,photo.width,0f);assertEquals(1f,photo.height,0f);assertTrue(photo.fillPage)
        }
    }
    @Test fun thirtyImagesExpandOneBlankAndKeepOrder() {
        val paths=(1..30).map { "$it.png" }
        val plan=PhotoPageFill.plan(listOf(DiaryPage(id="blank",order=0)),"main","blank",paths)
        assertEquals(30,plan.pages.size)
        assertEquals(paths,plan.pages.map { it.stickers.single().path })
        assertEquals((0..29).toList(),plan.pages.map { it.order })
    }
    @Test fun occupiedPagesAndOtherBooksAreNeverOverwritten() {
        val written=DiaryPage(id="written",order=0,body="keep")
        val blank=DiaryPage(id="blank",order=1)
        val ink=DiaryPage(id="ink",order=2,strokes=listOf(InkStroke(points=listOf(InkPoint(.3f,.2f)))))
        val other=DiaryPage(id="other",order=0,journalId="other")
        val plan=PhotoPageFill.plan(listOf(written,blank,ink,other),"main","written",listOf("a.png","b.png","c.png"))
        assertEquals(written,plan.pages.first())
        assertEquals("blank",plan.firstId)
        assertEquals(5,plan.pages.size)
        assertEquals(ink.strokes,plan.pages.last().strokes)
        assertEquals(ink.id,plan.pages.last().id)
        assertFalse(plan.pages.any { it.id==other.id })
    }
    @Test fun landscapePortraitAndSquareSourcesCoverTheWholePaperWithoutStretching() {
        for((w,h) in listOf(2400 to 600,600 to 2400,1000 to 1000,1 to 5)) {
            val crop=PhotoPageFill.crop(w,h,1.35f)
            assertTrue(crop.left>=0 && crop.top>=0 && crop.right<=w && crop.bottom<=h)
            assertTrue(crop.right>crop.left && crop.bottom>crop.top)
            assertTrue(crop.left==0 || crop.top==0)
            if(w>1) assertEquals(1.35f,(crop.right-crop.left).toFloat()/(crop.bottom-crop.top),.004f)
        }
    }
    @Test(expected=IllegalArgumentException::class) fun moreThanThirtyImagesIsRejected() {
        PhotoPageFill.plan(emptyList(),"main",null,(1..31).map { "$it.png" })
    }
}
