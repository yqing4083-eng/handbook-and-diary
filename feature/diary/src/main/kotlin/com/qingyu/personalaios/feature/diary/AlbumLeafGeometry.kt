package com.qingyu.personalaios.feature.diary

import kotlin.math.*

internal data class LeafPose(val hinge: PagePose, val angle: Float, val split: Float)
internal data class FoldPanelPose(val hinge: PagePose, val angle: Float)

/** A fold link joins spread k's right half to spread k+1's left half.
 * The two rigid panels share an outer fold edge, not one coplanar front/back.
 * Their inner attachments separate during a turn and carry the neighbouring
 * stacks with them. This reconstruction follows the visible reference faces;
 * it does not assume the source app's physical binding or mesh topology. */
internal object AlbumLeafGeometry {
    private const val MidTurnHalfAngle = 8f
    fun frontPage(leaf: Int, count: Int): Int? = leaf.takeIf { it in 0 until count }
    fun backPage(leaf: Int, count: Int): Int? = (leaf + 1).takeIf { it in 0 until count }
    fun visibleLeaves(current: Int, count: Int): IntRange =
        if (count <= 0) IntRange.EMPTY else max(-1, current - 7)..min(count - 1, current + 6)

    fun pose(leaf: Int, current: Int, progress: Float, direction: Int, selection: Float = 0f,
             opening:Float=1f,reveal:Float=1f,closing:Float=-1f): LeafPose {
        val travel = progress.coerceIn(0f, 1f)
        // A turn never changes every resting leaf's angle. Feeding the release
        // spring's overshoot into this rail raised the neighbours and made them jitter.
        val restAngle=if(closing>=0f) BookGeometry.RestAngle else BookOpeningGeometry.restAngle(opening)
        val advance=BookOpeningGeometry.railAdvance(restAngle)
        val cursor = current + direction * travel
        val base = floor(cursor).toInt()
        val phase = cursor - base
        val angle = -restAngle - (180f - 2f * restAngle) * phase
        val split = MidTurnHalfAngle * sin(PI.toFloat() * phase).pow(2)
        val frontAttachment = attachmentOffset(angle, split)
        val backAttachment = attachmentOffset(angle, -split)
        val edge = cos(angle * PI.toFloat() / 180f) / cos(restAngle * PI.toFloat() / 180f)
        val leftTravel = min(edge, 0f).pow(2)
        val rightTravel = max(edge, 0f).pow(2)
        // The incoming support stays behind the fold until it crosses centre.
        // Previously rightTravel already spent the whole layer-depth advance
        // during the first half of a forward turn, visibly lifting that stack.
        // Retreat the common connected rail by the depth it would gain early.
        // The outgoing side is pulled back first; the incoming side takes over
        // after centre. Reverse drags retrace exactly the same physical path.
        val softness=.004f*sin(PI.toFloat()*phase).pow(2)
        val difference=frontAttachment.z-backAttachment.z
        // Smooth the attachment handoff without an abs() velocity corner at 50%.
        val quietAttachment=(frontAttachment.z+backAttachment.z-
            (sqrt(difference*difference+softness*softness)-softness))/2f
        val railRetreat=AccordionGeometry.depth(1f)-
            AccordionGeometry.depth(leftTravel+rightTravel)+quietAttachment
        val relative = when {
            leaf < base -> leaf + 1 - base - leftTravel
            leaf > base -> leaf - base - 1 + rightTravel
            else -> 0f
        }
        // During insertion old right layers retain their previous slot, while
        // the new fold emerges from behind the current face. No mesh scaling.
        val insertion=if(leaf>current) 1f-reveal.coerceIn(0f,1f) else 0f
        val rest = AccordionGeometry.resting(relative-insertion)
        // Selection only arms the second tap to expand. Lifting the connected
        // rail here made deselect/turn/reselect move the whole book down and up.
        val attachment = when {
            leaf < base -> frontAttachment
            leaf > base -> backAttachment
            else -> PagePose(0f, 0f, 0f)
        }
        val hinge = rest.copy(x = rest.x + attachment.x,
            y = 0f, z = rest.z + attachment.z - railRetreat+advance-.002f*insertion)
        val yaw = when {
            leaf < base -> -180f + restAngle
            leaf > base -> -restAngle
            else -> angle
        }
        return LeafPose(hinge, yaw, if (leaf == base) split else 0f)
    }

    /** Equal-length panels meet at the same free edge throughout both directions.
     * Only rigid transforms change; there is no X/Y scaling or paper bending. */
    fun panel(pose: LeafPose, back: Boolean): FoldPanelPose {
        val delta = if (back) -pose.split else pose.split
        val offset = attachmentOffset(pose.angle, delta)
        return FoldPanelPose(PagePose(pose.hinge.x + offset.x, pose.hinge.y,
            pose.hinge.z + offset.z), pose.angle + delta)
    }

    fun foldEdge(panel: FoldPanelPose): PagePose {
        val radians = panel.angle * PI.toFloat() / 180f
        return PagePose(panel.hinge.x + BookGeometry.HalfWidth * cos(radians),
            panel.hinge.y, panel.hinge.z - BookGeometry.HalfWidth * sin(radians))
    }

    private fun attachmentOffset(angle: Float, delta: Float): PagePose {
        val a = angle * PI.toFloat() / 180f
        val b = (angle + delta) * PI.toFloat() / 180f
        return PagePose(BookGeometry.HalfWidth * (cos(a) - cos(b)), 0f,
            BookGeometry.HalfWidth * (sin(b) - sin(a)))
    }
}
