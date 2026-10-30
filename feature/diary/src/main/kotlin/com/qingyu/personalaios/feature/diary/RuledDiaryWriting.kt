package com.qingyu.personalaios.feature.diary

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.qingyu.personalaios.domain.*
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A fixed document shared by the IME and original vector handwriting. The
 * platform input connection never owns the paper's visible layout. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun RuledDiaryWriting(page:DiaryPage,face:PaperFace,model:DiaryViewModel?,modifier:Modifier,
    stickers:List<DiarySticker> = page.stickers,handwriting:RuledWritingBridge?=null) {
    val density=LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds().magnifier(sourceCenter={
        if(handwriting?.dragging==true) handwriting.caret?.let { Offset(it.x,it.y-handwriting.fontHeight/2) } ?: Offset.Unspecified else Offset.Unspecified
    },zoom=1.6f,size=DpSize(112.dp,56.dp),cornerRadius=22.dp,elevation=6.dp)) {
        val width=with(density) { maxWidth.toPx() };val height=with(density) { maxHeight.toPx() }
        var document by remember(page.id,face,width) { mutableStateOf(RuledPageDocument.load(page,width,height)) }
        var acknowledgedHtml by remember(page.id,face) { mutableStateOf(page.bodyHtml) }
        var acknowledgedTitle by remember(page.id,face) { mutableStateOf(page.title) }
        var composingStart by remember(page.id,face) { mutableIntStateOf(-1) }
        var composingEnd by remember(page.id,face) { mutableIntStateOf(-1) }
        val assetKeys=stickers.map { it.id to StickerFlowMask.key(it) }
        val contours by produceState<Map<String,List<RuledFlowGeometry.Cell>>>(emptyMap(),assetKeys) {
            value=withContext(Dispatchers.IO) { stickers.associate { it.id to StickerFlowMask.load(it) } }
        }
        // A moving sticker reflows text off the UI thread. Document edits still
        // get an immediate layout so insertion/caret coordinates stay exact.
        val immediateFlow=remember(document,width,height) { RuledFlow(document,width,height,stickers,contours) }
        val reflow by produceState(immediateFlow,document,width,height,stickers,contours) {
            value=withContext(Dispatchers.Default) { RuledFlow(document,width,height,stickers,contours) }
        }
        val layoutFlow=if(reflow.document==document && reflow.width==width && reflow.height==height) reflow else immediateFlow
        val previews=remember(page.id,face) { mutableMapOf<Int,android.graphics.Bitmap>() }
        layoutFlow.erasurePreviews=previews
        val erasure=remember(page.id,face) { arrayOfNulls<GlyphEraseSession>(1) }
        var inkRevision by remember(page.id,face) { mutableIntStateOf(0) }
        DisposableEffect(page.id,face) { onDispose { erasure[0]?.clear() } }
        fun persist(doc:RuledDocument) {
            val layout=RuledFlow(doc,width,height,stickers)
            val body=RuledPageDocument.body(doc)
            val prefix=doc.text.length-body.text.length
            val blocks=body.blocks { layout.rowAtOffset(it+prefix).coerceAtLeast(2) }
            model?.ruledText(page.id,blocks,face,doc.anchors.first().x,body,doc.text.substringBefore('\n'),doc.glyphs.filter { it.offset<prefix })
            model?.state?.value?.pages?.find { it.id==page.id }?.let { latest ->
                val saved=DiaryFaces.page(latest,face);acknowledgedHtml=saved.bodyHtml;acknowledgedTitle=saved.title
            }
        }
        val actualCaret=layoutFlow.caret(handwriting?.cursor ?: document.text.length)
        val currentCaret=handwriting?.freeCaret?.let { RuledFlow.Caret(it.x,it.y,PaperRules.row(width,height,it.y)) } ?: actualCaret
        fun placeCursor(x:Float,y:Float) {
            val row=PaperRules.row(width,height,y+layoutFlow.gap*.28f)
            var measured=if(document==layoutFlow.document) layoutFlow else RuledFlow(document,width,height,stickers,contours)
            if(measured.emptyRow(row)) {
                val finalRow=measured.caret(document.text.length).row
                if(row>finalRow) {
                    document=document.edit(document.text+"\n".repeat(row-finalRow))
                    measured=RuledFlow(document,width,height,stickers,contours)
                }
                val offset=measured.hit(x,PaperRules.baseline(width,row))
                val fraction=(x/width).coerceIn(.065f,.87f)
                document=document.indent(offset,fraction)
                handwriting?.cursor=offset;handwriting?.freeCaret=Offset(fraction*width,PaperRules.baseline(width,row))
            } else { handwriting?.cursor=measured.hit(x,y);handwriting?.freeCaret=null }
        }
        SideEffect {
            handwriting?.erase={ paths,radius,commit ->
                val session=erasure[0] ?: GlyphEraseSession(if(document==layoutFlow.document) layoutFlow else RuledFlow(document,width,height,stickers,contours),previews).also { erasure[0]=it }
                if(commit) {
                    val erased=session.finish(paths,radius);session.clear();erasure[0]=null;inkRevision++
                    if(document!=erased) { document=erased;persist(erased) }
                } else if(session.preview(paths,radius)) inkRevision++
            }
            handwriting?.cancelErase={ if(erasure[0]!=null) { erasure[0]?.clear();erasure[0]=null;inkRevision++ } }
            handwriting?.let { bridge ->
                bridge.caret=Offset(currentCaret.x,currentCaret.baseline);bridge.fontHeight=layoutFlow.paint.textSize
                bridge.moveCursor={ x,y -> placeCursor(x,y) }
            }
            handwriting?.insert={ strokes ->
                val position=(handwriting.cursor ?: document.text.length).coerceIn(0,document.text.length)
                val proposed=document.insertInk(position,strokes,paperWidthInk=true,sourceWidthScale=handwriting.sourceWidthScale)
                document=proposed;handwriting.cursor=position+1;handwriting.freeCaret=null;persist(proposed);true
            }
            handwriting?.place={ x,y -> placeCursor(x,y);handwriting.openKeyboard(document.text,handwriting.cursor ?: document.text.length) }
        }
        LaunchedEffect(page.bodyHtml,page.title) {
            if(page.bodyHtml!=acknowledgedHtml || page.title!=acknowledgedTitle) {
                document=RuledPageDocument.load(page,width,height)
                handwriting?.cursor=handwriting.cursor?.coerceAtMost(document.text.length)
                acknowledgedHtml=page.bodyHtml;acknowledgedTitle=page.title
            }
        }
        var caretVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { while(isActive) { delay(500);caretVisible=!caretVisible } }
        RuledKeyboardInput(document.text,handwriting?.cursor ?: document.text.length,handwriting,Modifier.size(1.dp),
            onChange={ next,start,removed ->
                val edited=document.edit(next,start,removed)
                if(edited!=document) { document=edited;handwriting?.freeCaret=null;persist(edited) }
            },onSelection={ _,end,a,b -> handwriting?.cursor=end.coerceIn(0,document.text.length);composingStart=a;composingEnd=b })
        Canvas(Modifier.fillMaxSize()) { val revision=inkRevision;if(revision>=0) drawIntoCanvas { layoutFlow.draw(it.nativeCanvas,composingStart=composingStart,composingEnd=composingEnd) } }
        Canvas(Modifier.fillMaxSize()) { drawIntoCanvas {
            handwriting?.let { bridge ->
                val caret=currentCaret
                it.nativeCanvas.drawLine(caret.x,caret.baseline-layoutFlow.paint.textSize,caret.x,caret.baseline,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF007AFF.toInt();alpha=if(bridge.dragging || caretVisible) 255 else 0
                        strokeWidth=max(3.5f,width/200);strokeCap=Paint.Cap.ROUND })
            }
        } }
    }
}
