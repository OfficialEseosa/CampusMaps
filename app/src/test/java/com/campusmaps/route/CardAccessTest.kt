package com.campusmaps.route

import com.campusmaps.data.campus.TestBuildings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// The PantherCard prompt on S1b and the S1 hint, on Student Center East. Surveyed 2026-09-26: one entrance only, public on weekdays
// (Mon-Thu 08-22, Fri 08-20), PantherCard-only at every other time including all weekend (owner). No public door to route around to.
class CardAccessTest {
    private val router = CoreRouter()
    private val cse = TestBuildings.cse
    private val sat21 = LocalDateTime.of(2026, 9, 26, 21, 0)
    private val sat14 = LocalDateTime.of(2026, 9, 26, 14, 0)
    private val tue14 = LocalDateTime.of(2026, 9, 29, 14, 0)
    private val fri21 = LocalDateTime.of(2026, 9, 25, 21, 0)
    private val sat2330 = LocalDateTime.of(2026, 9, 26, 23, 30)

    private fun plan(time: LocalDateTime, hasCard: Boolean) = router.plan(cse, cse.defaultStartId, "R-AUD", time, avoidStairs = false, hasCard = hasCard)

    @Test
    fun withoutCardThereIsNoRouteAndItSaysCardOnly() {
        // Was: West entrance plus the prompt. The West entrance was never surveyed and is gone; the only door is card-only.
        val plan = plan(sat21, hasCard = false)
        assertEquals("No route to Speaker Auditorium: every entrance is card-only at Sat 21:00.", (plan as RoutePlan.NoRoute).message)
        // The S1b prompt only shows over route options, so here it is null (the S1 hint still asks for the card).
        assertNull(CardAccess.prompt(plan, cse, sat21, hasCard = false, routedAround = false))
    }

    @Test
    fun withCardTheRouteUsesMainAndTheCardOnlyOffersRouteAround() {
        val plan = plan(sat21, hasCard = true) as RoutePlan.Options
        assertNull(plan.lockedNotice)
        val main = plan.options.first()
        assertEquals("Main entrance", main.entrance!!.name)
        assertTrue(main.cardNeeded)
        val walk = main.route.steps.first()
        assertEquals(StepKind.WALK_TO_ENTRANCE, walk.kind)
        assertEquals("PantherCard", walk.cardName)
        assertEquals("Tap your PantherCard at the Main entrance", walk.approachText)
        assertEquals(listOf("E-MAIN", "H1", "R-AUD"), main.route.points.map { it.node.id }.filter { it in setOf("E-MAIN", "H1", "R-AUD") })
        val p = CardAccess.prompt(plan, cse, sat21, hasCard = true, routedAround = false)!!
        // Saturday has no public window, so no "after 8 pm".
        assertEquals("Main entrance needs a PantherCard right now", p.title)
        assertFalse(p.canTurnOn)
        // Watch: near the door a LOCKED-style face labelled "PantherCard"; farther away the usual arrow.
        val near = com.campusmaps.guidance.GuidanceEngine.watchStep(walk, 5.0, null, showLocked = false)
        assertEquals(com.campusmaps.shared.WatchStepType.LOCKED, near.type)
        assertEquals("PantherCard", near.label)
        assertEquals(com.campusmaps.shared.WatchStepType.STRAIGHT, com.campusmaps.guidance.GuidanceEngine.watchStep(walk, 30.0, null, false).type)
    }

    @Test
    fun routeMeAroundHidesTheCard() {
        assertNull(CardAccess.prompt(plan(sat21, true), cse, sat21, hasCard = true, routedAround = true))
    }

    @Test
    fun weekdayEveningSaysAfter8pm() {
        // Friday's public window closes at 20:00; after that the card prompt names the time.
        val p = CardAccess.prompt(plan(fri21, true), cse, fri21, hasCard = true, routedAround = false)!!
        assertEquals("Main entrance needs a PantherCard after 8 pm", p.title)
    }

    @Test
    fun weekdayDaytimeShowsNothingAndLateNightStillTakesTheCard() {
        assertNull(CardAccess.prompt(plan(tue14, false), cse, tue14, hasCard = false, routedAround = false))
        assertNull(CardAccess.hint(cse, tue14, hasCard = false))
        // Saturday 14:00 is card-only now (weekends are card-only), so the hint shows.
        assertEquals("After hours: every door needs your PantherCard", CardAccess.hint(cse, sat14, hasCard = false)) // one door, card-only all weekend
        // 23:30 is card-only, not closed (owner): the card still gets you in.
        assertTrue(plan(sat2330, true) is RoutePlan.Options)
        assertTrue(plan(sat2330, false) is RoutePlan.NoRoute)
    }

    @Test
    fun hintOnS1OnlyWhenCardOnlyAndSettingOff() {
        assertEquals("After hours: every door needs your PantherCard", CardAccess.hint(cse, sat21, hasCard = false))
        assertNull(CardAccess.hint(cse, sat21, hasCard = true))
        assertNull(CardAccess.hint(TestBuildings.kl, sat21, hasCard = false)) // no access windows: always public
    }

    @Test
    fun cardNameFollowsTheCampus() {
        assertEquals("PantherCard", CardAccess.cardName("CSE"))
        assertEquals("BuzzCard", CardAccess.cardName("KL"))
        assertEquals("8 pm", CardAccess.clock(20 * 60))
        assertEquals("7:30 am", CardAccess.clock(7 * 60 + 30))
        assertEquals("12 pm", CardAccess.clock(12 * 60))
    }
}
