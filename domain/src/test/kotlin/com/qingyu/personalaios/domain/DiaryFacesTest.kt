package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class DiaryFacesTest {
    @Test fun wallpapersBelongToOneFaceWithoutChangingInkOrOtherFace() {
        val original=NewBook.page(NewBook.journal(BookKind.DIARY),0).copy(wallpaper="right.png")
        val edited=DiaryFaces.replace(original,PaperFace.LEFT,DiaryFaces.page(original,PaperFace.LEFT).copy(wallpaper="left.png"))
        assertEquals("right.png",edited.wallpaper)
        assertEquals("left.png",DiaryFaces.page(edited,PaperFace.LEFT).wallpaper)
        val reset=DiaryFaces.replace(edited,PaperFace.RIGHT,edited.copy(wallpaper=""))
        assertEquals("left.png",reset.leftFace?.wallpaper);assertEquals("",reset.wallpaper)
        assertEquals(original.strokes,reset.strokes)
    }
    private val sheet=NewBook.page(NewBook.journal(BookKind.DIARY),0).copy(title="right",body="old right",
        strokes=listOf(InkStroke(points=listOf(InkPoint(.1f,.2f)))))
    @Test fun existingContentRemainsOnRightAndLeftStartsAsLinedPortrait() {
        val left=DiaryFaces.page(sheet,PaperFace.LEFT)
        assertEquals("",left.body);assertEquals(sheet.id,left.id)
        assertEquals(PaperStyle.LINED,left.style);assertEquals(NewBook.FaceAspect,left.canvasAspect,0f)
        assertEquals(sheet,DiaryFaces.page(sheet,PaperFace.RIGHT))
    }
    @Test fun editingEachFacePreservesTheOppositeArtworkAndPhysicalSheet() {
        val left=DiaryFaces.page(sheet,PaperFace.LEFT).copy(title="left",body="left words",
            stickers=listOf(DiarySticker(path="sticker.png",kind=StickerKind.STICKER)))
        val edited=DiaryFaces.replace(sheet,PaperFace.LEFT,left)
        assertEquals(sheet.body,edited.body);assertEquals(sheet.strokes,edited.strokes)
        assertEquals(sheet.id,edited.id);assertEquals(sheet.order,edited.order)
        assertEquals(left.body,DiaryFaces.page(edited,PaperFace.LEFT).body)
        val right=DiaryFaces.replace(edited,PaperFace.RIGHT,edited.copy(body="right updated"))
        assertEquals("right updated",right.body);assertEquals(edited.leftFace,right.leftFace)
    }
}
