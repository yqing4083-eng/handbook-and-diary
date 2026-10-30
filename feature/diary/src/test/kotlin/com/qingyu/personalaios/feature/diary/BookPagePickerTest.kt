package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class BookPagePickerTest {
    @Test fun bothFacesReturnTheirOwnSideWithoutSplittingTheSheet() {
        for(side in listOf(-1,1)) {
            val point=BookGeometry.project(side*BookGeometry.HalfWidth*.5f,0f,side,0,false,1080f,950f,true,475f)
            val hit=BookPagePicker.pickFace(point.first,point.second,1080f,950f,3,9,false,true,475f)
            assertEquals(3,hit?.index);assertEquals(side,hit?.side)
        }
    }
    private fun pick(x:Float,y:Float=475f,current:Int=3,selected:Boolean=false)=BookPagePicker.pick(x,y,1080f,950f,current,9,selected,true)
    @Test fun frontSheetWinsOverOverlappingFan() { assertEquals(3,pick(540f));assertEquals(3,pick(540f,selected=true)) }
    @Test fun ExposedAdjacentSheetsSelectTheirOwnPage() { assertEquals(2,pick(140f));assertEquals(4,pick(940f)) }
    @Test fun weatherAndMissingPreviousPageAreNotSelectable() { assertNull(pick(540f,50f));assertNull(pick(140f,current=0)) }
    @Test fun hitProjectionScalesWithTheViewport() { assertEquals(pick(940f),BookPagePicker.pick(470f,237.5f,540f,475f,3,9,false,true)) }
}
