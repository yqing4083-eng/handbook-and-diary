package com.qingyu.personalaios.feature.diary

/** The paper lining is bonded to the board, with a distinct visible surface.
 * Its previous zero offset made the cover and white lining fight for depth. */
internal object BookBoardMaterial {
    const val Thickness=.045f
    const val LinerClearance=.0025f
    const val FrontPaperGap=.002f
    const val ClosedRearFace=.065f
    const val ClosedBlockFace=.185f
    val closedPaperBase get()=ClosedBlockFace+PaperMaterial.Thickness/2+FrontPaperGap
    val closedFrontFace get()=closedPaperBase+frontOffset
    val frontOffset get()=PaperMaterial.Thickness/2+Thickness+FrontPaperGap
    fun paperBase(open:Float)=closedPaperBase*(1-open)
    fun rearOffset(open:Float)=(ClosedRearFace-closedPaperBase)*(1-open)-.018f*open
    fun rearLinerZ(open:Float)=.12f*(1-open)+LinerClearance*open
    fun blockDepthScale(open:Float)=(1-open).coerceAtLeast(.015f)
}
