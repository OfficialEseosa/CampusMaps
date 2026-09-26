package com.campusmaps.data

import com.campusmaps.data.model.EdgeSource
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.data.model.Point
import com.campusmaps.data.shortcuts.PathPoint
import com.campusmaps.data.shortcuts.ShortcutRules
import com.campusmaps.data.shortcuts.ShortcutStatus
import com.campusmaps.data.shortcuts.ShortcutSubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutRulesTest {

    @Test
    fun submitButtonExplainsWhatIsMissing() {
        assertEquals("Pick From and To to submit", ShortcutRules.missingReason(false, true, true, 3))
        assertEquals("Record your walk to submit", ShortcutRules.missingReason(true, true, false, 3))
        assertEquals("Add 2 photos to submit", ShortcutRules.missingReason(true, true, true, 0))
        assertEquals("Add 1 photo to submit", ShortcutRules.missingReason(true, true, true, 1))
        assertNull(ShortcutRules.missingReason(true, true, true, 2))
        assertEquals("Walk all the way from From to To to submit", ShortcutRules.missingReason(true, true, true, 3, walkConnects = false))
    }

    @Test
    fun walkMustStartAtFromAndEndAtToOnTheirFloors() {
        val from = GraphNode("r150", "Room 150", NodeKind.ROOM, 1, Point(0.0, 0.0))
        val to = GraphNode("r608", "Room 608", NodeKind.ROOM, 6, Point(20.0, 0.0))
        val stoppedEarlyOnFloor1 = listOf(PathPoint(0.0, 0.0, 1, 0), PathPoint(20.0, 0.0, 1, 20_000))
        val reachedFloor6 = listOf(PathPoint(1.0, 0.0, 1, 0), PathPoint(20.0, 0.0, 1, 20_000), PathPoint(19.0, 2.0, 6, 40_000))
        val endedFarAway = listOf(PathPoint(0.0, 0.0, 1, 0), PathPoint(60.0, 0.0, 6, 40_000))
        assertFalse(ShortcutRules.walkConnects(stoppedEarlyOnFloor1, from, to))
        assertTrue(ShortcutRules.walkConnects(reachedFloor6, from, to))
        assertFalse(ShortcutRules.walkConnects(endedFarAway, from, to))
    }

    @Test
    fun pathLengthIgnoresFloorChanges() {
        val path = listOf(
            PathPoint(0.0, 0.0, 1, 0),
            PathPoint(3.0, 4.0, 1, 1_000), // 5 m
            PathPoint(3.0, 4.0, 2, 2_000), // stairs, no walking
            PathPoint(3.0, 10.0, 2, 3_000), // 6 m
        )
        assertEquals(11.0, ShortcutRules.pathLengthM(path), 1e-9)
    }

    @Test
    fun approvedShortcutBecomesAStudentEdge() {
        val s = ShortcutSubmission(
            id = "1", submitterId = "me", name = "Library cut through", fromNodeId = "a", toNodeId = "b",
            path = listOf(PathPoint(0.0, 0.0, 1, 0), PathPoint(30.0, 40.0, 1, 40_000)),
            photos = emptyList(), note = null, status = ShortcutStatus.APPROVED, reviewerNote = null,
            createdAt = 0, buildingId = "cs", routeLabel = "A to B",
        )
        val edge = ShortcutRules.toEdge(s)
        assertEquals(EdgeSource.STUDENT, edge.source)
        assertEquals(50.0, edge.lengthM, 1e-9)
        assertEquals("Library cut through", edge.shortcutName)
    }

    @Test
    fun autoNameDropsTheWordEntrance() {
        assertEquals("Library South cut through", ShortcutRules.autoName("Library South entrance"))
        assertEquals("Lobby cut through", ShortcutRules.autoName("Lobby"))
    }
}
