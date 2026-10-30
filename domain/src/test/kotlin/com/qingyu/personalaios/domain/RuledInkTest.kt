package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class RuledInkTest {
    private fun ink(id:String="letter")=listOf(InkStroke(id,0xAA1266DD,.018f,listOf(InkPoint(.3f,.2f,.4f),InkPoint(.6f,.8f,1.4f)),InkTool.WATERCOLOR))
    private fun doc(text:String="字")=RuledDocument(text,listOf(RuledAnchor(0,.065f,"body")))
    @Test fun storesExactPointsPressureColourToolAndWidth() {
        val input=listOf(RuledGlyph(2,ink()+ink("drawing").map { it.copy(tool=InkTool.MARKER) }))
        assertEquals(input,RuledInkCodec.decode(RuledInkCodec.encode(input)))
    }
    @Test fun adjacentLettersKeepIdentityOnInsertAndDelete() {
        val a=doc("").insertInk(0,ink("first")).insertInk(1,ink("third")).insertInk(1,ink("second"))
        assertEquals(listOf("first","second","third"),a.glyphs.map { it.strokes.single().id })
        assertEquals(listOf(0,1,2),a.glyphs.map { it.offset })
        val b=a.edit("\uFFFC\uFFFC",1,1)
        assertEquals(listOf("first","third"),b.glyphs.map { it.strokes.single().id })
        assertEquals(listOf(0,1),b.glyphs.map { it.offset })
    }
    @Test fun keyboardTextAndNewlinesMoveInkWithoutChangingVectors() {
        val a=doc().insertInk(1,ink()).edit("字春天\n\uFFFC",1,0)
        assertEquals(4,a.glyphs.single().offset);assertEquals(ink(),a.glyphs.single().strokes)
        assertEquals(listOf(0,4),a.anchors.map { it.start })
    }
    @Test fun richTextRoundTripAndLegacyStayCompatible() {
        val a=doc().insertInk(1,ink())
        val html=RuledText.encode(a.blocks { 2 },document=a,titleInk=listOf(RuledGlyph(0,ink("title"))))
        assertEquals(a,RuledText.document(html))
        assertEquals("title",RuledText.ink(html,"title").single().strokes.single().id)
        assertTrue(RuledText.ink("<div></div>","flow").isEmpty())
        assertEquals("旧日记",RuledText.decode(RuledText.encode(listOf(RuledParagraph("old",2,.065f,"旧日记"))))!!.single().text)
    }
    @Test fun malformedAndTruncatedInkCannotCrashOrAllocateUnboundedLists() {
        assertTrue(RuledInkCodec.decode("broken").isEmpty())
        val encoded=RuledInkCodec.encode(listOf(RuledGlyph(0,ink())))
        assertTrue(RuledInkCodec.decode(encoded.dropLast(8)).isEmpty())
        assertTrue(RuledInkCodec.decode("AAAAAX////8=").isEmpty())
    }
}
