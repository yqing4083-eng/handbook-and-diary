package com.qingyu.personalaios.feature.diary

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared ABI: std140 mat4/mat4/vec4/vec4/vec4 followed by four int32 values. */
internal class VulkanDrawPacket(private val capacity:Int=512) {
    val buffer:ByteBuffer=ByteBuffer.allocateDirect(capacity*Stride).order(ByteOrder.nativeOrder())
    private val floats=buffer.asFloatBuffer()
    private val integers=buffer.asIntBuffer()
    var count=0;private set
    fun clear() {buffer.clear();count=0}
    fun add(mesh:Int,texture:Int,mvp:FloatArray,model:FloatArray,r:Float,g:Float,b:Float,alpha:Float,
            start:Float,end:Float,bend:Float,side:Float,edge:Float,depth:Boolean,cull:Int) {
        require(mvp.size==16 && model.size==16 && cull in 0..2)
        check(count<capacity) {"Book draw budget exceeded"}
        floats.position(count*48)
        floats.put(mvp).put(model)
        floats.put(r*alpha).put(g*alpha).put(b*alpha).put(alpha)
        floats.put(start).put(end).put(bend).put(side)
        floats.put(edge).put(0f).put(0f).put(0f)
        val flags=(if(depth) 1 else 0) or (if(depth && alpha>=1f) 2 else 0) or (cull shl 2)
        integers.position(count*48+44);integers.put(mesh).put(texture).put(flags).put(0)
        count++;buffer.position(count*Stride)
    }
    companion object {const val Stride=192}
}
