package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class VulkanDrawPacketTest {
    private val matrix=FloatArray(16) { it.toFloat() }
    private fun add(p:VulkanDrawPacket,alpha:Float=1f,depth:Boolean=true,cull:Int=0) =
        p.add(17,23,matrix,matrix,.8f,.6f,.4f,alpha,.5f,1f,.02f,1f,1f,depth,cull)
    @Test fun std140OffsetsMatchNativeShaderAndMeshHeader() {
        val p=VulkanDrawPacket();add(p)
        assertTrue(p.buffer.isDirect);assertEquals(192,p.buffer.position())
        assertEquals(15f,p.buffer.getFloat(60),0f);assertEquals(15f,p.buffer.getFloat(124),0f)
        assertEquals(.8f,p.buffer.getFloat(128),0f);assertEquals(.5f,p.buffer.getFloat(144),0f)
        assertEquals(.02f,p.buffer.getFloat(152),0f);assertEquals(1f,p.buffer.getFloat(160),0f)
        assertEquals(17,p.buffer.getInt(176));assertEquals(23,p.buffer.getInt(180));assertEquals(3,p.buffer.getInt(184))
    }
    @Test fun transparentMaterialIsPremultipliedAndDoesNotWriteDepth() {
        val p=VulkanDrawPacket();add(p,.5f)
        assertEquals(.4f,p.buffer.getFloat(128),0f);assertEquals(.5f,p.buffer.getFloat(140),0f)
        assertEquals(1,p.buffer.getInt(184))
    }
    @Test fun shadowAndBothCullDirectionsHaveIndependentState() {
        val p=VulkanDrawPacket()
        add(p,depth=false);add(p,cull=1);add(p,cull=2)
        assertEquals(0,p.buffer.getInt(184));assertEquals(7,p.buffer.getInt(192+184));assertEquals(11,p.buffer.getInt(384+184))
    }
    @Test fun restingAndFinalFramesReuseBufferWithoutOldDraws() {
        val p=VulkanDrawPacket();add(p);add(p);p.clear()
        assertEquals(0,p.count);assertEquals(0,p.buffer.position());add(p)
        assertEquals(1,p.count);assertEquals(192,p.buffer.position())
    }
    @Test fun overflowFailsBeforeCorruptingNativeCommandMemory() {
        val p=VulkanDrawPacket(1);add(p)
        assertThrows(IllegalStateException::class.java) {add(p)}
        assertEquals(192,p.buffer.position());assertEquals(1,p.count)
    }
}
