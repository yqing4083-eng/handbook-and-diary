package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class RuledDocumentTest {
    private fun document(text:String)=RuledDocument(text,listOf(RuledAnchor(0,.4f,"body")))
    @Test fun returnInheritsOriginAndBackspaceJoinsParagraphs() {
        val split=document("你好世界").edit("你好\n世界")
        assertEquals(listOf(0,3),split.anchors.map { it.start })
        assertTrue(split.anchors.all { it.x==.4f })
        val shifted=split.indent(3,.7f).edit("你好世界")
        assertEquals(1,shifted.anchors.size);assertEquals(.4f,shifted.anchorAt(3).x,0f)
    }
    @Test fun chineseCandidatesAndEmojiUseUtf16Offsets() {
        val doc=document("你\n😀日记").edit("你好\n😀日记")
        assertEquals(listOf(0,3),doc.anchors.map { it.start })
        val selected=doc.indent(5,.7f).edit("你好\n😀今天")
        assertEquals(.7f,selected.anchorAt(5).x,0f)
        assertEquals("😀今天",selected.blocks { if(it==0) 2 else 3 }[1].text)
    }
    @Test fun deletingSeveralParagraphsKeepsLaterOriginAndIds() {
        val doc=RuledDocument("a\nb\nc\nd",listOf(RuledAnchor(0,.1f,"a"),RuledAnchor(2,.2f,"b"),RuledAnchor(4,.3f,"c"),RuledAnchor(6,.6f,"d")))
        val cut=doc.edit("a\nd")
        assertEquals("d",cut.anchors.last().id);assertEquals(2,cut.anchors.last().start);assertEquals(.6f,cut.anchors.last().x,0f)
    }
    @Test fun blankRuleOriginsDoNotNeedPaddingSpaces() {
        val doc=document("").edit("\n\n\n\n").indent(4,.55f).edit("\n\n\n\n这里")
        val saved=doc.blocks { offset -> 2+doc.text.take(offset).count { it=='\n' } }
        assertEquals(listOf(RuledParagraph(doc.anchors.last().id,6,.55f,"这里")),saved)
        assertFalse(doc.text.contains(' '))
    }
    @Test fun legacyWrappedBlocksKeepTheirPhysicalRows() {
        val blocks=listOf(RuledParagraph("a",2,.2f,"wrap"),RuledParagraph("b",6,.5f,"右边\n下一行"))
        val doc=RuledDocument.from(blocks) { text,_ -> if(text=="wrap") 3 else 1 }
        assertEquals("wrap\n\n右边\n下一行",doc.text)
        var row=2
        val rowByOffset=mutableMapOf<Int,Int>()
        doc.anchors.forEach { a -> rowByOffset[a.start]=row;val end=doc.text.indexOf('\n',a.start).takeIf { it>=0 } ?: doc.text.length;row+=if(doc.text.substring(a.start,end)=="wrap") 3 else 1 }
        assertEquals(listOf(2,6,7),doc.blocks { rowByOffset.getValue(it) }.map { it.row })
    }
    @Test fun multilinePasteInMiddleRetainsSuffixIndent() {
        val source=RuledDocument("first\nlast",listOf(RuledAnchor(0,.2f,"first"),RuledAnchor(6,.7f,"last")))
        val pasted=source.edit("first新\n粘贴\nlast")
        assertEquals(.7f,pasted.anchors.last().x,0f);assertEquals("last",pasted.anchors.last().id)
        assertEquals(.2f,pasted.anchors[1].x,0f)
    }
}
