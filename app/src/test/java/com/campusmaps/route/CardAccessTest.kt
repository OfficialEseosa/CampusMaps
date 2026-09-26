package com.campusmaps.route

import com.campusmaps.data.campus.TestBuildings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// The PantherCard prompt on S1b and the S1 hint, on Student Center East (Main entrance card-only 20:00 to 23:00 on Saturday).
class CardAccessTest {
    private val router = CoreRouter()
    private val cse = TestBuildings.cse
    private val sat21 = LocalDateTime.of(2026, 9, 26, 21, 0)
    private val sat14 = LocalDateTime.of(2026, 9, 26, 14, 0)
    private val sat2330 = LocalDateTime.of(2026, 9, 26, 23, 30)

    private fun plan(time: LocalDateTime, hasCard: Boolean) = router.plan(cse, cse.defaultStartId, "R-220", time, avoidStairs = false, hasCard = hasCard)

    @Test
    fun withoutCardTheCardAsksAndOffersBothButtons() {
        val plan = plan(sat21, hasCard = false)
        val p = CardAccess.prompt(plan, cse, sat21, hasCard = false, routedAround = false)!!
        assertEquals("Main entrance needs a PantherCard after 8 pm", p.title)
        assertTrue(p.canTurnOn)
        assertEquals("West entrance", (plan as RoutePlan.Options).options.first().entrance!!.name)
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
        val p = CardAccess.prompt(plan, cse, sat21, hasCard = true, routedAround = false)!!
        assertFalse(p.canTurnOn)
        // Watch: near the door a LOCKED-style face labelled "PantherCard"; farther away the usual arrow.
        val near = com.campusmaps.guidance.GuidanceEngine.watchStep(walk, 5.0, null, showLocked = false)
        assertEquals(com.campusmaps.shared.WatchStepType.LOCKED, near.type)
        assertEquals("PantherCard", near.label)
        assertEquals(com.campusmaps.shared.WatchStepType.STRAIGHT, com.campusmaps.guidance.GuidanceEngine.watchStep(walk, 30.0, null, false).type)
    }

    @Test
    fun routeMeAroundHidesTheCard() {
        assertNull(CardAccess.prompt(plan(sat21, false), cse, sat21, hasCard = false, routedAround = true))
    }

    @Test
    fun daytimeAndClosedShowNothing() {
        assertNull(CardAccess.prompt(plan(sat14, false), cse, sat14, hasCard = false, routedAround = false))
        assertNull(CardAccess.hint(cse, sat14, hasCard = false))
        // 23:30: every door closed, the card opens nothing; no prompt, no hint.
        assertTrue(plan(sat2330, true) is RoutePlan.NoRoute)
        assertNull(CardAccess.prompt(plan(sat2330, false), cse, sat2330, hasCard = false, routedAround = false))
        assertNull(CardAccess.hint(cse, sat2330, hasCard = false))
    }

    @Test
    fun hintOnS1OnlyWhenCardOnlyAndSettingOff() {
        assertEquals("After hours: bring your PantherCard or we route you to the public door", CardAccess.hint(cse, sat21, hasCard = false))
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
