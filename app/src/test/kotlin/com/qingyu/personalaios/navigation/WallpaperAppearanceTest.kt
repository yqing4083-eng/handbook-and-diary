package com.qingyu.personalaios.navigation
import org.junit.Assert.*
import org.junit.Test
class WallpaperAppearanceTest {
    @Test fun originalReadableOverlayIsPreserved() {
        assertEquals(listOf(.42f,.30f,.50f),WallpaperAppearance.shades)
    }
}
