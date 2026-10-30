package com.qingyu.personalaios.core.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class UpstreamShapeContractTest {
    @Test fun continuousShapeExposesDensityCorrectMetricsToUpstreamLens() {
        val shape = GlassGeometry.Corner(32.dp).shape() as CornerBasedShape
        assertEquals(84f, shape.topStart.toPx(Size(954f, 430.5f), Density(2.625f)), .001f)
        assertTrue(shape.copy() is ContinuousGlassShape)
    }
    @Test fun capsuleUsesHalfTheShortSideInEitherOrientation() {
        val shape = GlassGeometry.Capsule.shape() as com.kyant.shapes.RoundedRectangularShape
        assertEquals(com.kyant.shapes.Capsule(),shape)
        for (size in listOf(Size(954f,178.5f),Size(178.5f,954f))) {
            assertEquals(89.25f, shape.corners(size,LayoutDirection.Ltr,Density(2.625f)).topLeft, .001f)
        }
    }
}
