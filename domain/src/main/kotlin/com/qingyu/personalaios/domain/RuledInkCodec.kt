package com.qingyu.personalaios.domain

import java.io.*
import java.util.Base64

/** Versioned vectors in the existing rich-text field; old pages and backups need no migration. */
object RuledInkCodec {
    fun encode(glyphs:List<RuledGlyph>):String {
        if(glyphs.isEmpty()) return ""
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            val version=if(glyphs.any { it.paperWidthInk }) 3 else 1
            out.writeInt(version);out.writeInt(glyphs.size)
            glyphs.forEach { g ->
                out.writeInt(g.offset);if(version>=2) out.writeBoolean(g.paperWidthInk)
                if(version>=3) out.writeFloat(g.sourceWidthScale)
                out.writeInt(g.strokes.size)
                g.strokes.forEach { s ->
                    out.writeUTF(s.id);out.writeUTF(s.tool.name);out.writeLong(s.color);out.writeFloat(s.width);out.writeInt(s.points.size)
                    s.points.forEach { p -> out.writeFloat(p.x);out.writeFloat(p.y);out.writeFloat(p.pressure) }
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }
    fun decode(value:String):List<RuledGlyph> = runCatching {
        require(value.length<=16_000_000)
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use { input ->
            val version=input.readInt();require(version in 1..3)
            var points=0
            val result=List(input.readInt().also { require(it in 0..100000) }) {
                val offset=input.readInt().also { require(it in 0..100000) }
                val paperWidthInk=if(version>=2) input.readBoolean() else false
                val sourceWidthScale=if(version>=3) input.readFloat().also { require(it.isFinite() && it>0f && it<=32f) } else 1f/.56f
                RuledGlyph(offset,List(input.readInt().also { require(it in 1..500) }) {
                    val id=input.readUTF();val tool=InkTool.valueOf(input.readUTF());val color=input.readLong();val width=input.readFloat()
                    val count=input.readInt().also { require(it in 1..8000);points+=it;require(points<=750000) }
                    InkStroke(id,color,width,List(count) { InkPoint(input.readFloat(),input.readFloat(),input.readFloat()) },tool)
                },paperWidthInk,sourceWidthScale)
            }
            require(input.available()==0)
            require(result.zipWithNext().all { (a,b)->a.offset<b.offset })
            result
        }
    }.getOrDefault(emptyList())
}
