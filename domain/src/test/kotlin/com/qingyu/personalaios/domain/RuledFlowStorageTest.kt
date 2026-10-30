package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class RuledFlowStorageTest {
    @Test fun rawFlowTextAndParagraphOriginsSurviveSaveWithoutSyntheticGaps() {
        val raw=RuledDocument("中文😀一行绕过贴纸\n第二段",listOf(RuledAnchor(0,.2f,"a"),RuledAnchor(12,.4f,"b")))
        // Use real UTF-16 paragraph boundaries rather than glyph counts.
        val doc=raw.copy(anchors=listOf(raw.anchors[0],raw.anchors[1].copy(start=raw.text.indexOf('\n')+1)))
        val blocks=doc.blocks { if(it==0) 2 else 5 }
        val html=RuledText.encode(blocks,.3f,doc)
        assertEquals(doc,RuledText.document(html));assertEquals(blocks,RuledText.decode(html))
        assertEquals(.3f,RuledText.titleX(html),0f)
    }
    @Test fun oldHtmlStillLoadsAndCorruptFlowMetadataDoesNotDiscardFallbackParagraphs() {
        val blocks=listOf(RuledParagraph("old",2,.065f,"原有文字"))
        val html=RuledText.encode(blocks)
        assertNull(RuledText.document(html));assertEquals(blocks,RuledText.decode(html))
        val broken=html.replace("data-title-x", "data-flow-text=\"%%%\" data-title-x")
        assertNull(RuledText.document(broken));assertEquals(blocks,RuledText.decode(broken))
    }
}
