package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class RuledPlacementTest {
    @Test fun positionedWritingRetainsRowsPastTheFirstScreenAfterPartialScroll() {
        val width=1080f;val height=1600f;val gap=PaperRules.gap(width)
        for(scroll in listOf(0,45,91,930,1800)) for(row in 2..40) {
            val y=row*gap-scroll
            if(y>=PaperRules.bodyViewport(width) && y<height-gap*.5f) assertEquals(row,PaperRules.writingRow(width,height,y,scroll))
        }
        assertEquals(1,PaperRules.writingRow(width,height,80f,1800))
    }
    @Test fun visibleRulesResolveToTheSameParagraphAfterPartialOrMultipleRowScroll() {
        val width=1080f;val gap=PaperRules.gap(width)
        for(scroll in listOf(0,20,45,71,72,97,144,413)) for(line in 0..30) {
            val y=PaperRules.body(width)+line*gap-scroll
            if(y>=PaperRules.bodyViewport(width)) {
                assertEquals(line,PaperRules.bodyLine(width,y,scroll))
                assertEquals(line,PaperRules.bodyLine(width,y-PaperRules.clearance(width),scroll))
            }
        }
    }
    @Test fun blankPhysicalRowsBecomeAddressableWithoutChangingExistingParagraphs() {
        assertEquals("\n\n\n\n",RuledCursor.materialize("",1,4))
        assertEquals("hello\n\n",RuledCursor.materialize("hello",1,2))
        assertEquals("hello\nworld",RuledCursor.materialize("hello\nworld",2,1))
        // Wrapped visual lines are already supplied by the text layout.
        assertEquals("a long wrapped sentence",RuledCursor.materialize("a long wrapped sentence",3,2))
    }
    @Test fun tapsSnapToOneGridAcrossPhoneWidthsIncludingTheLastPhysicalRow() {
        for(w in listOf(720f,1080.0002f,1260f,1440f)) {
            val h=w*1.48f;val gap=PaperRules.gap(w)
            for(row in 1..PaperRules.lastRow(w,h)) {
                assertEquals(row,PaperRules.row(w,h,row*gap-.2f*gap))
                assertEquals(row,PaperRules.row(w,h,row*gap+.2f*gap))
            }
            assertEquals(1,PaperRules.row(w,h,-100f))
            assertEquals(PaperRules.lastRow(w,h),PaperRules.row(w,h,h+100f))
        }
    }
    @Test fun draggingUsesPaperAxesAtAnyRotationWithoutFeedbackOrDirectionChanges() {
        for(rotation in listOf(0f,45f,90f,-150f)) {
            val sticker=DiarySticker(path="sticker.png",x=.2f,y=.2f,width=.3f,height=.2f,rotation=rotation)
            val moved=StickerPlacement.move(sticker,.1f,.12f,1f,0f,.35f,.3f,.675f)
            assertEquals(.3f,moved.x,.00001f);assertEquals(.32f,moved.y,.00001f)
            assertEquals(sticker.width,moved.width,0f);assertEquals(sticker.rotation,moved.rotation,0f)
            assertTrue(StickerPlacement.hit(sticker,.35f,.3f,.675f))
            assertFalse(StickerPlacement.hit(sticker,-1f,-1f,.675f))
        }
    }
    @Test fun centrePinchPreservesTheSameCentreAndAspectRatio() {
        val sticker=DiarySticker(path="sticker.png",x=.2f,y=.2f,width=.3f,height=.2f,rotation=45f)
        val moved=StickerPlacement.move(sticker,0f,0f,1.5f,10f,.35f,.3f,.675f)
        assertEquals(.35f,moved.x+moved.width/2,.00001f)
        assertEquals(.3f,moved.y+moved.height/2,.00001f)
        assertEquals(sticker.width/sticker.height,moved.width/moved.height,.00001f)
    }
}
