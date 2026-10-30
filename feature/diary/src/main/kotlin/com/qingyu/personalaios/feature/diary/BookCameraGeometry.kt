package com.qingyu.personalaios.feature.diary

/** Off-axis perspective keeps the open accordion's horizontal rail on its
 * layout centre at every depth. Translating a centred camera instead made
 * depth travel also move the book vertically. Paper remains rigid; only the
 * lens centre changes. The closed shelf keeps its original camera exactly. */
internal object BookCameraGeometry {
    fun centreY(height: Float, scene: BookScene): Float {
        val spread = BookOpeningGeometry.spread(scene.open)
        val closed = scene.closedCentreY.takeIf { it.isFinite() }
            ?: BookLibraryGeometry.centreY(height, scene.portrait)
        val opened = scene.openCentreY.takeIf { it.isFinite() }
            ?: BookLibraryGeometry.centreY(height, scene.portrait)
        return closed + (opened - closed) * spread
    }

    fun principalY(height: Float, scene: BookScene): Float =
        height / 2 + (centreY(height, scene) - height / 2) * BookOpeningGeometry.spread(scene.open)

    fun baseY(width: Float, height: Float, scene: BookScene): Float {
        val spread = BookOpeningGeometry.spread(scene.open)
        val referenceZ = (BookLibraryGeometry.HoverDepth + BookBoardMaterial.closedFrontFace) * (1 - spread) +
            BookGeometry.restingOuterZ() * spread
        return -(centreY(height, scene) - principalY(height, scene)) *
            (BookGeometry.cameraDistance(scene.open) - referenceZ) /
            BookGeometry.focal(width, height, scene.portrait, scene.open) -
            BookLibraryGeometry.HoverHeight * (1 - spread)
    }

    /** Same projection as the renderer; also used for paper picking/editor bounds. */
    fun projectY(y: Float, z: Float, width: Float, height: Float, scene: BookScene): Float =
        principalY(height, scene) - (y + baseY(width, height, scene)) *
            BookGeometry.focal(width, height, scene.portrait, scene.open) /
            (BookGeometry.cameraDistance(scene.open) - z)
}
