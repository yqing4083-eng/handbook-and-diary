package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import com.qingyu.personalaios.domain.NewBook

class BookPaperLayoutTest {
    @Test fun flatEditorKeepsPhysicalPaperAspectWithoutViewportWhiteMargins() {
        for ((w,h) in listOf(411f to 815f,915f to 359f)) for (ratio in listOf(NewBook.FaceAspect,NewBook.SpreadAspect)) {
            val source=PaperRect(w*.2f,h*.35f,w*.6f,h*.32f)
            val flat=BookPaperLayout.flattened(source,w,h,ratio)
            assertEquals(ratio,flat.width/flat.height,.00001f)
            assertTrue(flat.height<h && flat.width<w)
            assertTrue(flat.width>=w-32.001f || flat.height>=h-32.001f)
            assertTrue(flat.left>=15.999f && flat.top>=15.999f)
            assertTrue(flat.left+flat.width<=w-15.999f && flat.top+flat.height<=h-15.999f)
            if(source.centreY>=16+flat.height/2 && source.centreY<=h-16-flat.height/2)
                assertEquals(source.centreY,flat.centreY,.00001f)
        }
    }
    @Test fun smallViewportsFitPaperByOneUniformScale() {
        for ((w,h) in listOf(320f to 200f,200f to 320f)) for (ratio in listOf(NewBook.FaceAspect,NewBook.SpreadAspect)) {
            val flat=BookPaperLayout.flattened(PaperRect(0f,0f,600f,800f),w,h,ratio)
            assertEquals(ratio,flat.width/flat.height,.00001f)
            assertTrue(flat.left>=15.999f && flat.top>=15.999f)
            assertTrue(flat.left+flat.width<=w-15.999f && flat.top+flat.height<=h-15.999f)
        }
    }
    @Test fun transitionHasExactSourceAndDestinationWithoutOvershootingPaperBounds() {
        val source=PaperRect(205f,280f,150f,220f)
        val target=BookPaperLayout.flattened(source,411f,815f,NewBook.FaceAspect)
        assertEquals(source,BookPaperLayout.interpolate(source,target,0f))
        val end=BookPaperLayout.interpolate(source,target,1f)
        assertEquals(target.left,end.left,.00001f);assertEquals(target.width,end.width,.00001f)
        for (step in 0..1000) {
            val p=BookPaperLayout.interpolate(source,target,step/1000f)
            assertTrue(p.height>=source.height-.00001f && p.height<=target.height+.00001f)
            assertEquals(source.centreY,p.centreY,.0001f)
        }
    }
}
