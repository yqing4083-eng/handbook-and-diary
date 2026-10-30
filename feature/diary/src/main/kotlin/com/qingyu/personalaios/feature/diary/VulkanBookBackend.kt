package com.qingyu.personalaios.feature.diary

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface
import java.nio.ByteBuffer

/** Render-thread-only Vulkan bridge. Geometry, picking and artwork remain shared Kotlin code. */
internal class VulkanBookBackend {
    private var handle = 0L
    private val packet = VulkanDrawPacket()
    var depthTest = true
    var cull = 0 // 0: double sided, 1: front, 2: back
    fun create(surface: Surface, assets: AssetManager, width: Int, height: Int,cachePath:String="") {
        check(handle == 0L)
        handle = nativeCreate(surface, assets, width, height,cachePath)
        check(handle != 0L) { "Cannot initialize Vulkan book renderer" }
    }
    fun resize(width: Int, height: Int) { if (handle != 0L) nativeResize(handle, width, height) }
    fun destroy() {
        if (handle != 0L) nativeDestroy(handle)
        handle = 0L
    }
    fun mesh(vertices: FloatArray, indices: ShortArray): Int = nativeMesh(handle, vertices, indices)
    fun texture(bitmap: Bitmap): Int = try { nativeTexture(handle, bitmap) } finally { bitmap.recycle() }
    fun deleteTexture(id: Int) = nativeDeleteTexture(handle, id)
    fun begin() { packet.clear(); depthTest = true; cull = 0 }
    fun draw(mesh: Int, texture: Int, mvp: FloatArray, model: FloatArray,
             r: Float, g: Float, b: Float, alpha: Float, start: Float, end: Float,
             bend: Float, side: Float, edge: Float) {
        packet.add(mesh,texture,mvp,model,r,g,b,alpha,start,end,bend,side,edge,depthTest,cull)
    }
    fun present(): Boolean = nativeFrame(handle, packet.buffer, packet.count)
    fun stats():LongArray=nativeStats(handle)
    private external fun nativeStats(handle:Long):LongArray
    private external fun nativeCreate(surface: Surface, assets: AssetManager, width: Int, height: Int,cachePath:String): Long
    private external fun nativeResize(handle: Long, width: Int, height: Int)
    private external fun nativeDestroy(handle: Long)
    private external fun nativeMesh(handle: Long, vertices: FloatArray, indices: ShortArray): Int
    private external fun nativeTexture(handle: Long, bitmap: Bitmap): Int
    private external fun nativeDeleteTexture(handle: Long, id: Int)
    private external fun nativeFrame(handle: Long, commands: ByteBuffer, count: Int): Boolean
    companion object {
        init { System.loadLibrary("diary_vulkan") }
    }
}
