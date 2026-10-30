package com.qingyu.personalaios.feature.today
import org.junit.Assert.*
import org.junit.Test
import com.qingyu.personalaios.core.ui.HelloLettering

class MonthLetteringTest {
    @Test fun everyEnglishMonthHasCompleteGlyphCoverageAndFiniteDimensions() {
        val names=listOf("January","February","March","April","May","June","July","August","September","October","November","December")
        names.forEachIndexed { index,name ->
            assertEquals(name,HelloLettering.name(index+1))
            assertTrue(HelloLettering.advance(name).isFinite())
            assertTrue(HelloLettering.advance(name)>140f)
        }
        assertTrue(HelloLettering.advance("September")>HelloLettering.advance("May"))
        assertTrue(HelloLettering.advance("Today").isFinite())
    }
}
