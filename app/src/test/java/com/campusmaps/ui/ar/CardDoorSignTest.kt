package com.campusmaps.ui.ar

import com.campusmaps.data.AccessRule
import com.campusmaps.data.AccessWindow
import com.campusmaps.data.campus.CoreBridge
import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.NodeKind
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.guidance.Pose
import com.campusmaps.guidance.Progress
import com.campusmaps.loc.BuildingToWorld
import com.campusmaps.loc.Vec3
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.LockedNotice
import com.campusmaps.route.Route
import com.campusmaps.route.RoutePlan
import com.campusmaps.route.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// The floating PantherCard sign at a card-only door (ArInputsAdapter.cardDoorSign, CardDoorPlacement).
// Classroom South has no door hours in its file, so the card test makes every outdoor door card-only in memory.
class CardDoorSignTest {
    private val router = CoreRouter()
    private val sat21 = LocalDateTime.of(2026, 9, 26, 21, 0)
    private val cs = TestBuildings.cs

    /** Classroom South with every outdoor entrance card-only all day. */
    private val csCardOnly: Building by lazy {
        val core = cs.core
        val card = listOf(AccessWindow("Mon-Sun", "00:00", "24:00", AccessRule.CARD))
        CoreBridge.fromCore(core.copy(nodes = core.nodes.map { if (it.isOutdoorEntrance) it.copy(access = card) else it }))
    }

    private fun route(b: Building, hasCard: Boolean): Route {
        val plan = router.plan(b, b.defaultStartId, "R-608", sat21, avoidStairs = false, hasCard = hasCard)
        return (plan as RoutePlan.Options).options.first().route
    }

    private fun state(b: Building, r: Route) = GuidanceState(
        building = b, route = r, destination = r.destination, step = r.steps.first(), nextStep = r.steps.getOrNull(1),
        bannerText = r.steps.first().text, distanceToStepM = 20.0,
        pose = Pose(r.points.first().position, r.points.first().floor, 0.0, 1f), progress = Progress(),
        locating = false, arrived = false, rerouteCount = 0, showRerouteChip = false,
    )

    @Test
    fun cardHolderGetsAnAmberSignAtTheEntranceNode() {
        val r = route(csCardOnly, hasCard = true)
        assertTrue(r.startsOutside)
        val walk = r.steps.first()
        assertEquals(StepKind.WALK_TO_ENTRANCE, walk.kind)
        assertEquals("PantherCard", walk.cardName)
        val door = r.points[walk.startIndex]
        assertEquals(NodeKind.ENTRANCE, door.node.kind)
        val sign = state(csCardOnly, r).toArRouteInput()!!.cardDoor
        assertNotNull(sign)
        sign!!
        assertEquals("Tap your PantherCard", sign.label)
        assertEquals(DoorSignTone.ATTENTION, sign.tone)
        assertEquals(door.position.x, sign.x, 1e-9)
        assertEquals(-door.position.y, sign.y, 1e-9) // AR layer is y-up
        assertEquals(door.floor, sign.floor)
    }

    @Test
    fun noCardOnlyDoorGivesNoSign() {
        val r = route(cs, hasCard = false)
        assertNull(state(cs, r).toArRouteInput()!!.cardDoor)
        // Card setting on but the doors are public: still no sign.
        assertNull(state(cs, route(cs, hasCard = true)).toArRouteInput()!!.cardDoor)
    }

    @Test
    fun routeStillAtTheLockedDoorGetsARedSign() {
        val r = route(cs, hasCard = false)
        val door = r.points[r.steps.first().startIndex].node.name
        val notice = LockedNotice(door, null, "Heads up: $door is card-only now. Taking another way.")
        val sign = cardDoorSign(r, notice)!!
        assertEquals("PantherCard required", sign.label)
        assertEquals(DoorSignTone.BLOCKED, sign.tone)
        assertNull(cardDoorSign(r, LockedNotice("Some other door", "West entrance", "x")))
    }

    @Test
    fun placementPrefersTheOutdoorAnchorThenTheBuildingTransform() {
        val sign = ArDoorSign("Tap your PantherCard", 3.0, 4.0, 1, DoorSignTone.ATTENTION)
        val anchor = Vec3(10.0, -1.2, 5.0)
        val t = BuildingToWorld(0.0, 1.0, 0.0, 2.0, refFloor = 1)
        assertEquals(anchor, CardDoorPlacement.foot(sign, t, anchor))
        assertEquals(t.toWorld(3.0, 4.0, 1), CardDoorPlacement.foot(sign, t, null))
        assertEquals(Vec3(4.0, 0.0, -2.0), CardDoorPlacement.foot(sign, t, null))
        assertNull(CardDoorPlacement.foot(sign, null, null))
    }
}
