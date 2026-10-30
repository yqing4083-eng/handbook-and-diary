package com.qingyu.personalaios.feature.diary

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class BookOpeningGeometryTest {
    @Test fun openingPassesEdgeOnBeforeItUnfolds() {
        assertEquals(0f,BookOpeningGeometry.spread(0f),0f)
        assertEquals(0f,BookOpeningGeometry.yaw(0f),0f)
        assertEquals(-90f,BookOpeningGeometry.yaw(.22f),.0001f)
        assertEquals(0f,BookOpeningGeometry.spread(.22f),.0001f)
        assertTrue(BookOpeningGeometry.lift(.28f)>0f)
        assertEquals(1f,BookOpeningGeometry.spread(1f),0f)
        assertEquals(0f,BookOpeningGeometry.yaw(1f),0f)
        assertEquals(0f,BookOpeningGeometry.lift(1f),.0001f)
        assertEquals(1f,BookOpeningGeometry.scale(1f),.0001f)
    }
    @Test fun openingCoverKeepsItsTopBelowTheTitleAtEveryPhase() {
        // Project the raised cover's corners with the same rigid transform order
        // as the renderer, at the regression device's full portrait viewport.
        val width=1080f;val height=2400f
        for(step in 0..200) {
            val p=step/200f;val open=BookOpeningGeometry.spread(p)
            val rootAngle=BookOpeningGeometry.yaw(p)*PI.toFloat()/180
            val fan=((open-.62f)/.38f).coerceIn(0f,1f)
            val cover=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(-1,0,0f,1,opening=p),true)
            val coverAngle=(-180f*open+(cover.angle+180f)*fan)*PI.toFloat()/180
            val coverZ=.23f*(1-open)+.055f*open
            val scale=BookOpeningGeometry.scale(p)
            val scene=BookScene(open=p,portrait=true,closedCentreY=height*.513f,openCentreY=height*.513f)
            val focal=BookGeometry.focal(width,height,true,p)
            for(x in listOf(-.625f,.625f)) for(y in listOf(-.92f,.92f)) {
                val cx=(x+.625f)*cos(coverAngle)+coverZ*sin(coverAngle)-.625f*(1-open)+cover.hinge.x*fan
                val cz=-(x+.625f)*sin(coverAngle)+coverZ*cos(coverAngle)+cover.hinge.z*fan
                val z=(-cx*sin(rootAngle)+cz*cos(rootAngle))*scale+BookLibraryGeometry.HoverDepth*(1-open)
                val py=y*scale+BookLibraryGeometry.HoverHeight*(1-open)+BookOpeningGeometry.lift(p)
                val sy=height/2-(py+BookLibraryGeometry.cameraBaseY(width,height,scene))*focal/(BookGeometry.cameraDistance(p)-z)
                assertTrue("cover clipped into header at p=$p y=$sy",sy>height*.225f)
                assertTrue("cover covered toolbar at p=$p y=$sy",sy<height*.81f)
            }
        }
    }
    @Test fun openingPathHasNoDiscontinuitiesAtItsPhaseBoundaries() {
        for(p in listOf(.22f,.38f,.56f,.70f,.88f,1f)) {
            assertEquals(BookOpeningGeometry.yaw(p-.0001f),BookOpeningGeometry.yaw(p),.06f)
            assertEquals(BookOpeningGeometry.lift(p-.0001f),BookOpeningGeometry.lift(p),.002f)
            assertEquals(BookOpeningGeometry.scale(p-.0001f),BookOpeningGeometry.scale(p),.002f)
        }
    }
    @Test fun aLeafTurnsEdgeOnAndLandsExactlyOnTheOppositeHalf() {
        for(side in listOf(-1,1)) {
            val angle=BookGeometry.turningAngle(side,.5f)*PI.toFloat()/180
            assertEquals(0f,cos(angle),.00001f)
            val end=BookGeometry.turningAngle(side,1f)*PI.toFloat()/180
            val next=BookGeometry.angle(-side,1f)*PI.toFloat()/180
            assertEquals(-side*1.2f*cos(next),side*1.2f*cos(end),.00001f)
            assertEquals(side*1.2f*sin(next),-side*1.2f*sin(end),.00001f)
        }
    }
    @Test fun everySheetKeepsItsPlacementWhenTheTurnCommits() {
        for(dir in listOf(-1,1)) for(delta in -4..4) {
            val relative=BookPageMotion.relative(delta,1f,dir)
            assertEquals(BookPageMotion.pose((delta-dir).toFloat()),BookPageMotion.pose(relative))
            for(side in listOf(-1,1)) assertEquals(BookGeometry.angle(side,1f,delta-dir),BookPageMotion.angle(side,relative),.00001f)
        }
    }
    @Test fun springOvershootKeepsFanStableAndFlexesOnlyTheTurningLeaf() {
        for(dir in listOf(-1,1)) for(delta in -4..4)
            assertEquals(BookPageMotion.relative(delta,1f,dir),BookPageMotion.relative(delta,1.04f,dir),0f)
        assertEquals(.018f,BookPageMotion.flapBend(0f),.00001f)
        assertEquals(.018f,BookPageMotion.flapBend(1f),.00001f)
        assertTrue(BookPageMotion.flapBend(.5f)>.05f)
    }
}
