package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class RuledFlowGeometryTest {
    @Test fun realContourLeavesTransparentMarginsWritableAndMovesWithSticker() {
        val s=DiarySticker(path="alpha",x=.2f,y=.2f,width=.6f,height=.4f)
        val shape=mapOf(s.id to listOf(RuledFlowGeometry.Cell(.4f,0f,.6f,.4f),RuledFlowGeometry.Cell(0f,.4f,1f,1f)))
        val slots=RuledFlowGeometry.slots(1000f,1500f,320f,370f,listOf(s),contours=shape)
        assertEquals(2,slots.size);assertEquals(438.5f,slots.first().right,.01f);assertEquals(561.5f,slots.last().left,.01f)
        val lower=RuledFlowGeometry.slots(1000f,1500f,650f,700f,listOf(s),contours=shape)
        assertTrue(lower.first().right<201f)
        val moved=RuledFlowGeometry.slots(1000f,1500f,320f,370f,listOf(s.copy(y=.6f)),contours=shape)
        assertEquals(listOf(RuledFlowGeometry.Interval(65f,935f)),moved)
    }
    @Test fun centeredStickerLeavesTwoWritableRunsAndMovingItRestoresOldSpace() {
        val s=DiarySticker(path="test",x=.4f,y=.2f,width=.2f,height=.2f)
        val slots=RuledFlowGeometry.slots(1000f,1500f,320f,380f,listOf(s))
        assertEquals(2,slots.size);assertTrue(slots[0].right<400f);assertTrue(slots[1].left>600f)
        val clear=RuledFlowGeometry.slots(1000f,1500f,320f,380f,listOf(s.copy(y=.6f)))
        assertEquals(listOf(RuledFlowGeometry.Interval(65f,935f)),clear)
    }
    @Test fun multipleAndRotatedStickersMergeIntoOrderedNonOverlappingSpaces() {
        val a=DiarySticker(path="test",x=.2f,y=.2f,width=.25f,height=.15f,rotation=30f)
        val b=a.copy(id="b",x=.4f,rotation=-25f)
        val slots=RuledFlowGeometry.slots(1080f,1600f,400f,440f,listOf(a,b))
        assertTrue(slots.all { it.width>0f && it.left>=1080*.065f && it.right<=1080*.935f })
        assertTrue(slots.zipWithNext().all { (l,r)->l.right<r.left })
    }
    @Test fun lockedStickerCannotTranslateScaleOrRotateUntilUnlocked() {
        val s=DiarySticker(path="test",locked=true)
        assertEquals(s,StickerPlacement.move(s,.2f,.2f,1.8f,80f,.5f,.5f,.675f))
        assertNotEquals(s.copy(locked=false),StickerPlacement.move(s.copy(locked=false),.1f,.1f,1f,0f,.5f,.5f,.675f))
    }
    @Test fun paperFitsBetweenToolbarAndCompactPenTrayWithNavigationInsets() {
        for((w,h) in listOf(320f to 568f,393f to 850f,450f to 1000f)) for(nav in listOf(16f,48f)) {
            val paper=BookPaperLayout.fixedWriting(w,h,NewBook.FaceAspect,66f,104f+nav)
            assertTrue("Top boundary $paper",paper.top>=66f-.001f);assertTrue(paper.top+paper.height<=h-104f-nav+.001f)
            assertEquals(w/2,paper.centreX,.001f);assertEquals(NewBook.FaceAspect,paper.width/paper.height,.001f)
        }
    }
    @Test fun fontOccupiesTwoThirdsOfPhysicalRuleSpacing() {
        for(w in listOf(720f,1080f,1260f)) assertEquals(2f/3f,PaperRules.font(w)/PaperRules.gap(w),.00001f)
    }
}
