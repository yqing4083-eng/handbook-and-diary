package com.qingyu.personalaios.domain
import org.junit.Assert.*
import org.junit.Test

class InkEraserTest {
    private val strokes=listOf(
        InkStroke(points=listOf(InkPoint(.05f,.05f),InkPoint(.2f,.08f))),
        InkStroke(points=listOf(InkPoint(.5f,.5f))),
        InkStroke(width=.03f,points=listOf(InkPoint(.8f,.1f),InkPoint(.95f,.4f),InkPoint(.7f,.6f))),
    )

    /** The bounds are only a rejection filter: the exact segment test still decides every hit. */
    @Test fun theSweepRemovesExactlyTheStrokesTheExactTestRemoves() {
        for(aspect in listOf(1.1f,.5f,2f)) for(radius in listOf(.005f,.035f,.12f)) {
            val index=InkEraser(strokes,aspect)
            for(x in 0..20) for(y in 0..20) {
                val p=InkPoint(x/20f,y/20f)
                val exact=strokes.filter { it.hits(p,radius,aspect) }.map { it.id }.toSet()
                assertEquals("aspect=$aspect radius=$radius at $p",exact,index.removedAt(p,radius))
            }
        }
    }

    @Test fun strokesAcrossThePageAreAllReachable() {
        val index=InkEraser(strokes)
        assertEquals(setOf(strokes[1].id),index.removedAt(InkPoint(.5f,.5f)))
        assertTrue(index.removedAt(InkPoint(.5f,.9f)).isEmpty())
        assertTrue(InkEraser(emptyList()).removedAt(InkPoint(.5f,.5f)).isEmpty())
    }

    @Test fun boundsSpanEveryPointOfTheStroke() {
        val bounds=strokes[2].bounds()
        assertEquals(.7f,bounds.minX,.0001f);assertEquals(.95f,bounds.maxX,.0001f)
        assertEquals(.1f,bounds.minY,.0001f);assertEquals(.6f,bounds.maxY,.0001f)
        assertTrue(bounds.reaches(InkPoint(.68f,.12f),.035f,1.1f))
        assertFalse(bounds.reaches(InkPoint(.2f,.9f),.035f,1.1f))
        // A wider aspect ratio must never shrink the box: that is what keeps the sweep exact.
        assertTrue(bounds.reaches(InkPoint(.68f,.63f),.035f,1.1f))
        assertFalse(bounds.reaches(InkPoint(.68f,.66f),.035f,1.1f))
        assertTrue(bounds.reaches(InkPoint(.68f,.66f),.035f,4f))
        assertEquals(InkBounds(.5f,.5f,.5f,.5f),strokes[1].bounds())
    }
}
