package com.qingyu.personalaios.feature.diary

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class AlbumLeafGeometryTest {
    @Test fun restingNeighboursNeverAdvancePastTheirNearestSettledDepthDuringEitherTurn() {
        // This fails on the old shared-attachment rail: quarter-turn old-left
        // and three-quarter-turn new-right are closer than either settled pose.
        for(direction in listOf(-1,1)) for(current in listOf(1,4,8)) {
            val turning=if(direction>0) current else current-1
            for(leaf in -1..10) {
                if(leaf==turning) continue
                val start=AlbumLeafGeometry.pose(leaf,current,0f,direction)
                val end=AlbumLeafGeometry.pose(leaf,current,1f,direction)
                val limit=maxOf(start.hinge.z,end.hinge.z)
                for(step in 0..1000) {
                    val now=AlbumLeafGeometry.pose(leaf,current,step/1000f,direction)
                    assertTrue("resting leaf $leaf raised at $current/$direction/$step: ${now.hinge.z}>$limit",
                        now.hinge.z<=limit+.00001f)
                    assertEquals(0f,now.hinge.y,.00001f)
                    assertEquals(start.angle,now.angle,.0001f)
                }
            }
        }
    }

    @Test fun incomingStacksDoNotAdvanceUntilTheFoldPassesTheMiddle() {
        for(direction in listOf(-1,1)) for(current in listOf(1,4,8)) {
            val turning=if(direction>0) current else current-1
            val incoming=if(direction>0) (turning+1..turning+6) else (turning-6 until turning)
            for(leaf in incoming) {
                val initial=AlbumLeafGeometry.pose(leaf,current,0f,direction).hinge.z
                for(step in 0..500) {
                    val pose=AlbumLeafGeometry.pose(leaf,current,step/1000f,direction)
                    assertTrue("incoming $leaf lifted before handoff at $direction/$step",
                        pose.hinge.z<=initial+.00001f)
                    assertEquals(0f,pose.hinge.y,0f)
                }
            }
        }
    }

    @Test fun connectedSupportIsHeldAtItsBackDepthUntilHandoff() {
        // The adjacent support used to complete its entire .62 world-unit
        // approach before the active fold arrived at the centre.
        for(direction in listOf(-1,1)) {
            val incoming=if(direction>0) 5 else 2
            val initial=AlbumLeafGeometry.pose(incoming,4,0f,direction).hinge.z
            for(step in 0..500) {
                val z=AlbumLeafGeometry.pose(incoming,4,step/1000f,direction).hinge.z
                assertEquals(initial,z,.00201f)
            }
        }
    }

    @Test fun incomingStacksAdvanceMonotonicallyAfterHandoff() {
        for(direction in listOf(-1,1)) for(leaf in -1..10) {
            val turning=if(direction>0) 4 else 3
            if(if(direction>0) leaf<=turning else leaf>=turning) continue
            val start=AlbumLeafGeometry.pose(leaf,4,.5f,direction).hinge.z
            val end=AlbumLeafGeometry.pose(leaf,4,1f,direction).hinge.z
            var previous=start
            for(step in 501..1000) {
                val z=AlbumLeafGeometry.pose(leaf,4,step/1000f,direction).hinge.z
                assertTrue("depth reversed after handoff for $leaf/$direction/$step",z-previous>=-.00001f)
                previous=z
            }
            assertTrue("neighbour should follow the turn",kotlin.math.abs(end-start)>.3f)
        }
    }

    private fun assertSamePose(expected: LeafPose, actual: LeafPose) {
        assertEquals(expected.hinge.x, actual.hinge.x, .00001f)
        assertEquals(expected.hinge.y, actual.hinge.y, .00001f)
        assertEquals(expected.hinge.z, actual.hinge.z, .00001f)
        assertEquals(expected.angle, actual.angle, .0001f)
        assertEquals(expected.split, actual.split, .00001f)
        for(back in listOf(false,true)) {
            val a=AlbumLeafGeometry.panel(expected,back)
            val b=AlbumLeafGeometry.panel(actual,back)
            assertEquals(a.hinge.x,b.hinge.x,.00001f)
            assertEquals(a.hinge.z,b.hinge.z,.00001f)
            assertEquals(a.angle,b.angle,.0001f)
        }
    }

    @Test fun releaseOvershootCannotRaiseOrRotateAnyNeighbour() {
        for(direction in listOf(-1,1)) for(leaf in -1..9) {
            assertSamePose(AlbumLeafGeometry.pose(leaf,4,1f,direction),AlbumLeafGeometry.pose(leaf,4,1.04f,direction))
            assertSamePose(AlbumLeafGeometry.pose(leaf,4,0f,direction),AlbumLeafGeometry.pose(leaf,4,-.04f,direction))
        }
    }
    @Test fun everyPhysicalLeafKeepsItsPoseWhenEitherTurnCommits() {
        val count = 9
        for (current in 1 until count - 1) for (direction in listOf(-1, 1)) {
            for (selection in listOf(0f, 1f)) for (leaf in -1 until count) {
                val before = AlbumLeafGeometry.pose(leaf, current, 1f, direction, selection)
                val after = AlbumLeafGeometry.pose(leaf, current + direction, 0f, direction, selection)
                assertSamePose(before, after)
            }
        }
    }

    @Test fun reversingTheSameDragRetracesEveryLeafWithoutChangingArtwork() {
        val count = 7
        for (current in 0 until count - 1) for (step in 0..20) {
            val progress = step / 20f
            for (leaf in -1 until count) {
                val forward = AlbumLeafGeometry.pose(leaf, current, progress, 1)
                val backward = AlbumLeafGeometry.pose(leaf, current + 1, 1f - progress, -1)
                assertSamePose(forward, backward)
                assertEquals(leaf.takeIf { it in 0 until count }, AlbumLeafGeometry.frontPage(leaf, count))
                assertEquals((leaf + 1).takeIf { it in 0 until count }, AlbumLeafGeometry.backPage(leaf, count))
            }
        }
    }

    @Test fun physicalFacesBelongToAdjacentStoredPagesIncludingTheTwoEndLeaves() {
        val count = 5
        for (leaf in -1 until count) {
            assertEquals(leaf.takeIf { it in 0 until count }, AlbumLeafGeometry.frontPage(leaf, count))
            assertEquals((leaf + 1).takeIf { it in 0 until count }, AlbumLeafGeometry.backPage(leaf, count))
        }
        // Both visible halves of one resting spread must lead to the same editor.
        for (current in 0 until count) {
            assertEquals(current, AlbumLeafGeometry.frontPage(current, count))
            assertEquals(current, AlbumLeafGeometry.backPage(current - 1, count))
        }
        assertNull(AlbumLeafGeometry.frontPage(-1, count))
        assertNull(AlbumLeafGeometry.backPage(count - 1, count))
    }

    @Test fun visibleLeavesNeverInventAPageBeforeOrAfterTheJournal() {
        for (count in listOf(1, 2, 9, 60)) for (current in 0 until count) {
            val visible = AlbumLeafGeometry.visibleLeaves(current, count)
            assertTrue(current in visible)
            assertTrue(current - 1 in visible)
            for (leaf in visible) {
                assertTrue(leaf in -1 until count)
                assertTrue(AlbumLeafGeometry.frontPage(leaf, count) != null ||
                    AlbumLeafGeometry.backPage(leaf, count) != null)
            }
        }
    }

    @Test fun middleOfTurnShowsTwoFacesWithSeparateInnerAttachmentsAndOneOuterFold() {
        for (current in 2..5) for (direction in listOf(-1, 1)) {
            val turning = if (direction > 0) current else current - 1
            for (selection in listOf(0f, 1f)) {
                val centre = AlbumLeafGeometry.pose(turning, current, .5f, direction, selection)
                assertEquals(-90f, centre.angle, .0001f)
                val front=AlbumLeafGeometry.panel(centre,false)
                val back=AlbumLeafGeometry.panel(centre,true)
                assertTrue(front.hinge.x<centre.hinge.x)
                assertTrue(back.hinge.x>centre.hinge.x)
                assertTrue(cos(front.angle*PI.toFloat()/180)>0f)
                assertTrue(-cos(back.angle*PI.toFloat()/180)>0f)
                for ((neighbour,attachment) in listOf(turning-1 to front.hinge,turning+1 to back.hinge)) {
                    val hinge = AlbumLeafGeometry.pose(neighbour, current, .5f, direction, selection).hinge
                    assertEquals(attachment.x, hinge.x, .00001f)
                    assertEquals(attachment.y, hinge.y, .00001f)
                    assertEquals(attachment.z, hinge.z, .00001f)
                }
            }
        }
    }

    @Test fun selectionAndDeselectionNeverMoveTheBookDuringEitherTurn() {
        // Click-neighbour used to clear selection then restore it, displacing
        // the entire rail down and back up. Swipe also cleared it on first MOVE.
        for(direction in listOf(-1,1)) for(step in 0..100) for(leaf in -1..7) {
            val progress=step/100f
            val rest=AlbumLeafGeometry.pose(leaf,3,progress,direction)
            for(selection in listOf(.25f,.5f,1f))
                assertSamePose(rest,AlbumLeafGeometry.pose(leaf,3,progress,direction,selection))
        }
    }

    @Test fun selectionDoesNotMoveTheProjectedPaperOrItsHitTarget() {
        for(portrait in listOf(false,true)) for(delta in -3..3)
            for(x in listOf(0f,BookGeometry.HalfWidth)) for(y in listOf(-BookGeometry.HalfHeight,BookGeometry.HalfHeight)) {
                val a=BookGeometry.project(x,y,1,delta,false,1080f,2138f,portrait)
                val b=BookGeometry.project(x,y,1,delta,true,1080f,2138f,portrait)
                assertEquals(a.first,b.first,0f);assertEquals(a.second,b.second,0f)
            }
    }

    @Test fun emptyAndSinglePageJournalsHaveNoPhantomNeighbourArtwork() {
        assertTrue(AlbumLeafGeometry.visibleLeaves(0, 0).isEmpty())
        for (leaf in -2..2) {
            assertNull(AlbumLeafGeometry.frontPage(leaf, 0))
            assertNull(AlbumLeafGeometry.backPage(leaf, 0))
        }
        assertEquals(listOf(-1, 0), AlbumLeafGeometry.visibleLeaves(0, 1).toList())
        assertNull(AlbumLeafGeometry.frontPage(-1, 1))
        assertEquals(0, AlbumLeafGeometry.backPage(-1, 1))
        assertEquals(0, AlbumLeafGeometry.frontPage(0, 1))
        assertNull(AlbumLeafGeometry.backPage(0, 1))
    }

    @Test fun paperEdgesKeepTheirPhysicalLengthThroughoutDragAndRebound() {
        // Both distinct panels retain world width, including the shared-tip pose.
        val width = BookGeometry.HalfWidth
        for (direction in listOf(-1, 1)) for (leaf in -1..6) {
            for (progress in listOf(-.05f, 0f, .25f, .5f, .75f, 1f, 1.05f)) {
                val pose = AlbumLeafGeometry.pose(leaf, 3, progress, direction)
                assertTrue(pose.hinge.x.isFinite() && pose.hinge.y.isFinite() && pose.hinge.z.isFinite())
                assertTrue(pose.angle.isFinite() && pose.split.isFinite())
                for(back in listOf(false,true)) {
                    val panel=AlbumLeafGeometry.panel(pose,back)
                    val edge=AlbumLeafGeometry.foldEdge(panel)
                    val dx=edge.x-panel.hinge.x
                    val dz=edge.z-panel.hinge.z
                    assertEquals(width,sqrt(dx*dx+dz*dz),.00001f)
                }
            }
        }
    }

    @Test fun bothPanelsStayJoinedAtTheOuterFoldDuringDragCancelAndRebound() {
        for(direction in listOf(-1,1)) for(leaf in -1..7) for(step in -5..105) {
            val pose=AlbumLeafGeometry.pose(leaf,3,step/100f,direction)
            val a=AlbumLeafGeometry.foldEdge(AlbumLeafGeometry.panel(pose,false))
            val b=AlbumLeafGeometry.foldEdge(AlbumLeafGeometry.panel(pose,true))
            assertEquals(a.x,b.x,.00001f)
            assertEquals(a.y,b.y,.00001f)
            assertEquals(a.z,b.z,.00001f)
        }
    }

    @Test fun planarRestingHalvesCreateTheReferenceVThroughPerspective() {
        val pose=AlbumLeafGeometry.pose(3,3,0f,1)
        val panel=AlbumLeafGeometry.panel(pose,false)
        val edge=AlbumLeafGeometry.foldEdge(panel)
        val camera=BookGeometry.cameraDistance()
        val ratio=(camera-edge.z)/(camera-panel.hinge.z)
        // Static reference: the centre is about 6% shorter than the free edge.
        assertEquals(.938f,ratio,.008f)
        assertTrue(edge.z>panel.hinge.z)
        assertEquals(0f,pose.split,0f)
    }
}
