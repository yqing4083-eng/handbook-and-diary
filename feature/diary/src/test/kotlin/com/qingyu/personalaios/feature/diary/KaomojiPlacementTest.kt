package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class KaomojiPlacementTest {
    private val legacy=DiarySticker(kind=StickerKind.TEXT,path="",text="(｡･ω･｡)ﾉ♡",x=.08f,y=.4f,width=.84f,height=.18f)
    @Test fun shortTextCanReachRightEdgeAndReturnLeft() {
        val fit=StickerPlacement.fitText(legacy,.33f,.09f,.675f)
        val right=StickerPlacement.move(fit,.7f,0f,1f,0f,.2f,.45f,.675f)
        assertEquals(.67f,right.x,.0001f)
        assertEquals(1f,right.x+right.width,.0001f)
        val left=StickerPlacement.move(right,-1f,0f,1f,0f,.8f,.45f,.675f)
        assertEquals(0f,left.x,.0001f)
        assertEquals(legacy.text,right.text)
    }
    @Test fun fittingPreservesRotatedVisibleOrigin() {
        val aspect=.675f;val original=legacy.copy(x=.3f,y=.35f,width=.4f,rotation=35f)
        fun origin(s:DiarySticker):Pair<Float,Float> {
            val a=s.rotation*PI.toFloat()/180;val x=-s.width/2;val y=-s.height/2/aspect
            return (s.x+s.width/2+x*cos(a)-y*sin(a)) to (s.y+s.height/2+(x*sin(a)+y*cos(a))*aspect)
        }
        val before=origin(original);val after=origin(StickerPlacement.fitText(original,.2f,.09f,aspect))
        assertEquals(before.first,after.first,.0001f);assertEquals(before.second,after.second,.0001f)
    }
    @Test fun imageAndEmptyEditableTextKeepTheirFrames() {
        assertEquals(legacy.copy(text=""),StickerPlacement.fitText(legacy.copy(text=""),.2f,.1f,.675f))
        val image=legacy.copy(kind=StickerKind.IMAGE,path="fixture.png")
        assertEquals(image,StickerPlacement.fitText(image,.2f,.1f,.675f))
    }
}
