package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test

class CoverCropTest {
    @Test fun wideAndTallImagesNeverRevealEmptyCoverAfterPanOrPinch() {
        for(aspect in listOf(.2f,2f/3,1f,4f)) for(zoom in listOf(.2f,1f,2f,8f)) {
            val crop=CoverCrop(zoom,10f,-10f).constrained(aspect)
            val w=maxOf(1f,aspect/(2f/3))*crop.zoom
            val h=maxOf(1f,(2f/3)/aspect)*crop.zoom
            assertTrue(crop.zoom in 1f..5f)
            assertTrue(crop.x+w/2>=.5f);assertTrue(crop.x-w/2<=-.5f)
            assertTrue(crop.y+h/2>=.5f);assertTrue(crop.y-h/2<=-.5f)
        }
    }
}
