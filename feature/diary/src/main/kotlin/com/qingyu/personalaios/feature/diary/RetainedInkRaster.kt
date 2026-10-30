package com.qingyu.personalaios.feature.diary

import android.graphics.*
import com.qingyu.personalaios.domain.*
import androidx.compose.ui.graphics.asImageBitmap

/** Two persistent buffers. A touch appends only new pigment; neither past
 * strokes nor the entire growing gesture are reconstructed on every frame. */
internal class RetainedInkRaster(val width:Int,val height:Int) {
    var inkWidthScale=1f
        set(value) {
            require(value.isFinite() && value>0)
            if(field!=value) { field=value;saved.eraseColor(Color.TRANSPARENT)
                PageArtwork.inkIsolated(savedCanvas,strokes,width.toFloat(),height.toFloat(),value);cancel() }
        }
    val saved=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    val live=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    private val savedCanvas=Canvas(saved);private val liveCanvas=Canvas(live)
    private val savedImage=saved.asImageBitmap();private val liveImage=live.asImageBitmap()
    var strokes:List<InkStroke> = emptyList();private set
    var active=false;private set
    private var beforeCommit:List<InkStroke>?=null
    private var awaiting=false
    private val painters=ArrayList<BrushStrokeRaster>()
    private val processed=ArrayList<Int>()
    private val restorePaint=Paint().apply { blendMode=BlendMode.SRC }
    private val fountainDirty=Rect()
    private var tool=InkTool.FELT;private var color=0L;private var brushWidth=.008f
    var appendedPoints=0L;private set
    var rebuilds=0;private set
    val bitmap get()=if(active) live else saved
    val image get()=if(active) liveImage else savedImage
    fun accept(value:List<InkStroke>) {
        if(awaiting && value===beforeCommit) return
        if(value===strokes || value==strokes) { awaiting=false;return }
        sync(value);awaiting=false
    }
    private fun sync(value:List<InkStroke>) {
        val append=value.size>=strokes.size && strokes.indices.all { strokes[it]===value[it] || strokes[it]==value[it] } &&
            value.drop(strokes.size).none { it.tool==InkTool.FILL }
        if(append) for(i in strokes.size until value.size) PageArtwork.stroke(savedCanvas,value[i],width.toFloat(),height.toFloat(),inkWidthScale)
        else { saved.eraseColor(Color.TRANSPARENT);PageArtwork.inkIsolated(savedCanvas,value,width.toFloat(),height.toFloat(),inkWidthScale);rebuilds++ }
        strokes=value
    }
    fun begin(tool:InkTool,color:Long,width:Float) {
        this.tool=tool;this.color=color;brushWidth=width
        live.eraseColor(Color.TRANSPARENT);liveCanvas.drawBitmap(saved,0f,0f,null)
        painters.clear();processed.clear();active=true
        fountainDirty.setEmpty()
    }
    fun append(paths:List<List<InkPoint>>,replay:Boolean=false) {
        if(replay || tool==InkTool.FOUNTAIN || tool==InkTool.DIAGRAM || tool==InkTool.FILL) {
            if(tool==InkTool.FOUNTAIN) {
                // Restore only the active ribbon's dirty bounds. Historic strokes
                // remain cached; a small handwritten letter never copies a full page.
                val margin=brushWidth*width*inkWidthScale*2+2f
                for(path in paths) for(p in path) fountainDirty.union(
                    kotlin.math.floor(p.x*width-margin).toInt().coerceAtLeast(0),
                    kotlin.math.floor(p.y*height-margin).toInt().coerceAtLeast(0),
                    kotlin.math.ceil(p.x*width+margin).toInt().coerceAtMost(width),
                    kotlin.math.ceil(p.y*height+margin).toInt().coerceAtMost(height))
                if(!fountainDirty.isEmpty) liveCanvas.drawBitmap(saved,fountainDirty,fountainDirty,restorePaint)
            } else { live.eraseColor(Color.TRANSPARENT);liveCanvas.drawBitmap(saved,0f,0f,null) }
            paths.forEach { if(it.isNotEmpty()) PageArtwork.stroke(liveCanvas,InkStroke(color=color,width=brushWidth,points=it,tool=tool),width.toFloat(),height.toFloat(),inkWidthScale) }
            return
        }
        for(i in paths.indices) {
            if(i>=painters.size) { painters+=BrushStrokeRaster(liveCanvas,tool,color,brushWidth*inkWidthScale,width.toFloat(),height.toFloat());processed+=0 }
            val path=paths[i]
            while(processed[i]<path.size) { painters[i].add(path[processed[i]]);processed[i]++;appendedPoints++ }
        }
    }
    fun commit(value:List<InkStroke>) {
        beforeCommit=strokes;sync(value);awaiting=true;active=false
    }
    fun cancel() { active=false;painters.clear();processed.clear() }
    fun release() { saved.recycle();live.recycle() }
}
