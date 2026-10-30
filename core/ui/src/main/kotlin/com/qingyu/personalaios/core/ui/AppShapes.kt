package com.qingyu.personalaios.core.ui

import androidx.compose.ui.graphics.Shape
import com.qingyu.personalaios.core.shape.ContinuousCapsuleShape
import com.qingyu.personalaios.core.shape.IOSContinuousCornerShape
import com.qingyu.personalaios.core.shape.SquircleShape

object AppShapes {
    val Small: Shape = IOSContinuousCornerShape(AppCorners.Small)
    val Medium: Shape = IOSContinuousCornerShape(AppCorners.Medium)
    val Large: Shape = IOSContinuousCornerShape(AppCorners.Large)
    val ExtraLarge: Shape = IOSContinuousCornerShape(AppCorners.ExtraLarge)
    val Paper: Shape = IOSContinuousCornerShape(AppCorners.Paper)
    val Capsule: Shape = ContinuousCapsuleShape()
    val Squircle: Shape = SquircleShape()
}
