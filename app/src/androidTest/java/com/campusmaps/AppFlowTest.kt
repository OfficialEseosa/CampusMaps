package com.campusmaps

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.campusmaps.data.AppClock
import com.campusmaps.data.ClockMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

// Drives the real app on a device or emulator through the flows in section 3 of the handoff.
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val container: AppContainer get() = (rule.activity.application as CampusMapsApplication).container

    // Every test starts from a clean S1: Classroom South (real building file), demo off, stairs allowed, Sat 21:00.
    @Before
    fun resetState() {
        runBlocking {
            container.settings.setDemoMode(false)
            container.settings.setAvoidStairs(false)
            container.settings.setBuilding("CS")
            container.settings.clearRecents("CS")
            container.settings.clearRecents("CSE")
        }
        container.clock.set(AppClock.DEFAULT_SIMULATED)
        rule.runOnUiThread { rule.activity.recreate() }
        rule.waitForIdle()
        // The app opens on the campus picker (S0): Georgia State, then Classroom South, lands on S1.
        waitForTag("campus_GSU")
        rule.onNodeWithTag("campus_GSU").performClick()
        waitForTag("building_CS")
        settle()
        rule.onNodeWithTag("building_CS").performClick()
        waitForText("Where to?")
        settle()
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 5_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 5_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun openRoutesFor608() {
        waitForTag("destination_R-608")
        rule.onNodeWithText("Pick a destination").assertIsNotEnabled()
        rule.onNodeWithTag("destination_R-608").performScrollTo().performClick()
        waitForText("Route to Room 608")
        rule.onNodeWithText("Route to Room 608").assertIsEnabled().performClick()
        waitForText("To Room 608")
        settle()
    }

    // MainViewModel ignores a tap that lands on a new screen within 600 ms (double-tap guard, docs/20 QA #1-#2).
    private fun settle() {
        Thread.sleep(700)
        rule.waitForIdle()
    }

    @Test
    fun destinationToRouteOptionsToGuidanceAndBack() {
        openRoutesFor608()
        // S1b: header, honest simulated time chip, best card from P1 (core: Library South entrance, floor 2, elevator).
        rule.onNodeWithText("Routed for", substring = true).assertIsDisplayed()
        rule.onNodeWithText("FASTEST").assertIsDisplayed()
        rule.onNodeWithText("also via: Classroom South main (floor 2)").assertIsDisplayed()

        // Tap the best card: S2 opens with the compact outside banner.
        // The card selects the route; Start follows it with the chosen mode (Phone AR).
        rule.onNodeWithTag("routeCard_E-LM2-elevator-0").performClick()
        rule.onNodeWithTag("startButton").performClick()
        waitForTag("guidanceScreen")
        rule.onNodeWithTag("instructionText").assertIsDisplayed()
        rule.onNodeWithText("Walk to Library South entrance (floor 2)").assertIsDisplayed()
        rule.onNodeWithTag("minimap").assertIsDisplayed()

        // End route goes back to S1b.
        rule.onNodeWithTag("endRoute").performClick()
        waitForText("Route options")
        rule.onNodeWithText("To Room 608").assertIsDisplayed()
    }

    // Demo C: Student Center East at Sat 21:00, the banner names both entrances.
    @Test
    fun lockedEntranceBannerNamesBothEntrances() {
        runBlocking { container.settings.setBuilding("CSE") }
        waitForTag("destination_R-220")
        rule.onNodeWithTag("destination_R-220").performScrollTo().performClick()
        waitForText("Route to Room 220")
        rule.onNodeWithText("Route to Room 220").performClick()
        waitForTag("lockedBanner")
        rule.onNodeWithText("Main entrance is card-only now. Using West entrance instead.", substring = true).assertIsDisplayed()
    }

    @Test
    fun avoidStairsRemovesStairsCards() {
        openRoutesFor608()
        assertTrue(rule.onAllNodesWithText("by stairs", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Avoid stairs").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("by stairs", substring = true).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("FASTEST").assertIsDisplayed()
    }

    @Test
    fun everyEntranceLockedShowsNoRoute() {
        // Student Center East closes both entrances at 23:00 on Saturday.
        runBlocking { container.settings.setBuilding("CSE") }
        container.clock.set(ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(23, 0)))
        waitForTag("destination_R-220")
        rule.onNodeWithTag("destination_R-220").performScrollTo().performClick()
        waitForTag("routeError")
        rule.onNodeWithText("No route to Room 220: every entrance is card-only at Sat 23:00.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Route to Room 220").performClick()
        waitForTag("noRoute")
        rule.onNodeWithText("Pick another room").performClick()
        waitForText("Where to?")
    }

    @Test
    fun glassesModeStartsAndStops() {
        openRoutesFor608()
        rule.onNodeWithTag("glassesButton").performClick()
        rule.onNodeWithTag("startButton").performClick()
        waitForTag("glassesScreen")
        rule.onNodeWithText("GLASSES MODE").assertIsDisplayed()
        rule.onNodeWithText("Glasses connected").assertIsDisplayed()
        rule.onNodeWithTag("stopButton").performClick()
        waitForText("Route options")
    }

    @Test
    fun demoModeHidesSearchAndShortcuts() {
        runBlocking { container.settings.setDemoMode(true) }
        waitForText("Demo destinations")
        rule.onNodeWithText("DEMO").assertIsDisplayed()
        assertTrue(rule.onAllNodes(hasTestTag("search")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Found a faster way? Add a shortcut").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun addShortcutScreenExplainsWhatIsMissing() {
        waitForText("Found a faster way? Add a shortcut")
        rule.onNodeWithText("Found a faster way? Add a shortcut").performScrollTo().performClick()
        waitForText("Add a shortcut")
        rule.onNodeWithText("Walk it, snap a few photos", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Pick From and To to submit").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Your submissions").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsSheetShowsTheTeamSwitches() {
        waitForText("Where to?")
        rule.onNodeWithText("Where to?").assertIsDisplayed()
        rule.onNodeWithTag("settingsSheet").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForTag("settingsSheet")
        rule.onNodeWithText("Speak instructions").assertIsDisplayed()
        rule.onNodeWithText("Hides debug, keeps screen on, demo destinations only").assertIsDisplayed()
        rule.onNodeWithText("Long press the title to toggle the debug overlay.").assertIsDisplayed()
    }
}
