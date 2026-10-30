package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class RuledPhysicalInkTest {
    private val stroke=InkStroke(points=listOf(InkPoint(.2f,.5f),InkPoint(.8f,.5f)))
    @Test fun oldVectorFormatStillDecodesAndNewWidthUnitSurvivesStorage() {
        val legacy=listOf(RuledGlyph(0,listOf(stroke)))
        assertEquals(legacy,RuledInkCodec.decode(RuledInkCodec.encode(legacy)))
        val mixed=legacy+RuledGlyph(1,listOf(stroke),true,2.35f)
        assertEquals(mixed,RuledInkCodec.decode(RuledInkCodec.encode(mixed)))
    }
    @Test fun editingKeepsPhysicalWidthMetadata() {
        val doc=RuledDocument("",listOf(RuledAnchor(0,.065f,"body"))).insertInk(0,listOf(stroke),true,2.35f)
        val edited=doc.edit("a"+doc.text,0,0)
        assertTrue(edited.glyphs.single().paperWidthInk)
        assertEquals(2.35f,edited.glyphs.single().sourceWidthScale,0f)
        assertEquals(1,edited.glyphs.single().offset)
        assertEquals(stroke,edited.glyphs.single().strokes.single())
    }
    @Test fun previousVersionTwoInkStillLoadsWithItsOriginalVectors() {
        val bytes=java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(2);out.writeInt(1);out.writeInt(0);out.writeBoolean(true);out.writeInt(1)
            out.writeUTF(stroke.id);out.writeUTF(stroke.tool.name);out.writeLong(stroke.color);out.writeFloat(stroke.width);out.writeInt(stroke.points.size)
            stroke.points.forEach { p->out.writeFloat(p.x);out.writeFloat(p.y);out.writeFloat(p.pressure) }
        }
        val loaded=RuledInkCodec.decode(java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())).single()
        assertEquals(stroke,loaded.strokes.single());assertTrue(loaded.paperWidthInk)
        assertEquals(1f/.56f,loaded.sourceWidthScale,0f)
    }
}
