package com.qingyu.personalaios.feature.diary

/** A roughly 5 px projected rim at 720 px width, 50% thicker than 0.19.9.
 * Video compression does not reveal the source model's physical thickness.
 * This is geometry, not a screen-space stroke: it foreshortens with the paper.
 */
internal object PaperMaterial {
    const val Thickness = .024f
    // Two thin panels close back-to-back at rest. Their combined envelope keeps
    // the existing rim thickness; the inner gap is not a thick paper side wall.
    const val FoldGap = .002f
    const val PanelThickness = (Thickness - FoldGap) / 2f
}
