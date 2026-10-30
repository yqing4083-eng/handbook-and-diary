package com.qingyu.personalaios.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class PageMotionPlanTest {
    @Test fun allFourTabsSlideInBothDirections() {
        val routes=listOf("today","calendar","diary","me")
        for(i in routes.indices) for(j in routes.indices) if(i!=j)
            assertEquals(PageMotionPlan(PageMotionAxis.HORIZONTAL,j.compareTo(i)),pageMotionPlan(routes[i],routes[j]))
    }
    @Test fun tabDirectionIsOrderedAndReversible() {
        assertEquals(PageMotionPlan(PageMotionAxis.HORIZONTAL, 1), pageMotionPlan("today", "diary"))
        assertEquals(PageMotionPlan(PageMotionAxis.HORIZONTAL, -1), pageMotionPlan("me", "today"))
    }
    @Test fun restoringMeSubpageStillUsesTabDirection() {
        assertEquals(PageMotionPlan(PageMotionAxis.HORIZONTAL, 1), pageMotionPlan("diary", "glass-settings"))
        assertEquals(PageMotionPlan(PageMotionAxis.HORIZONTAL, -1), pageMotionPlan("glass-preview", "today"))
    }
    @Test fun settingsEnterAndBackAreOpposite() {
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD, 1), pageMotionPlan("me", "settings"))
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD, -1), pageMotionPlan("settings", "me"))
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD, 1), pageMotionPlan("settings", "glass-settings"))
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD, -1), pageMotionPlan("glass-settings", "settings"))
    }
    @Test fun nestedPreviewBackResolvesDepth() {
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD, 1), pageMotionPlan("glass-settings", "glass-preview"))
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD, -1), pageMotionPlan("glass-preview", "glass-settings"))
    }
    @Test fun repeatedOrUnknownRoutesDoNotInventDirection() {
        assertEquals(PageMotionPlan(PageMotionAxis.NONE), pageMotionPlan("me", "me"))
        assertEquals(PageMotionPlan(PageMotionAxis.NONE), pageMotionPlan(null, "today"))
        assertEquals(PageMotionPlan(PageMotionAxis.NONE), pageMotionPlan("missing", "me"))
    }
    @Test fun glassCategoryUsesChildNavigationAndKeepsMeTabPosition() {
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD,1),pageMotionPlan("glass-settings","glass-settings/BUTTONS"))
        assertEquals(PageMotionPlan(PageMotionAxis.CHILD,-1),pageMotionPlan("glass-settings/{target}","glass-settings"))
        assertEquals(PageMotionPlan(PageMotionAxis.HORIZONTAL,-1),pageMotionPlan("glass-settings/NUMBERS","calendar"))
    }
}

