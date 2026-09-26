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

    // Every test starts from a clean S1: Classroom South, demo off, stairs allowed, Sat 21:00.
    @Before
    fun resetState() {
        runBlocking {
            container.settings.setDemoMode(false)
            container.settings.setAvoidStairs(false)
            container.settings.setBuilding("cs")
            container.settings.clearRecents("cs")
        }
        container.clock.set(AppClock.DEMO_PRESETS.first())
        rule.runOnUiThread { rule.activity.recreate() }
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 5_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 5_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun openRoutesFor608() {
        waitForTag("destination_cs_r608")
        rule.onNodeWithText("Pick a destination").assertIsNotEnabled()
        rule.onNodeWithTag("destination_cs_r608").performScrollTo().performClick()
        rule.onNodeWithText("Route to Room 608").assertIsEnabled().performClick()
        waitForText("To Room 608")
    }

    @Test
    fun destinationToRouteOptionsToGuidanceAndBack() {
        openRoutesFor608()
        // S1b: header, honest simulated time chip, locked entrance banner, best card.
        rule.onNodeWithText("Routed for", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("lockedBanner").assertIsDisplayed()
        rule.onNodeWithText("Main entrance is card-only now. Using Library South entrance instead.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Fastest").assertIsDisplayed()
        rule.onNodeWithText("also via: 95 Decatur Street entrance").assertIsDisplayed()

        // Tap the best card: S2 opens with the compact outside banner.
        rule.onNodeWithTag("routeCard_cs_lib-ELEVATOR").performClick()
        waitForTag("guidanceScreen")
        rule.onNodeWithTag("instructionText").assertIsDisplayed()
        rule.onNodeWithText("Walk to Library South entrance").assertIsDisplayed()
        rule.onNodeWithTag("minimap").assertIsDisplayed()

        // End route goes back to S1b.
        rule.onNodeWithTag("endRoute").performClick()
        waitForText("Route options")
        rule.onNodeWithText("To Room 608").assertIsDisplayed()
    }

    @Test
    fun avoidStairsRemovesStairsCards() {
        openRoutesFor608()
        assertTrue(rule.onAllNodesWithText("by stairs", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Avoid stairs").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("by stairs", substring = true).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Fastest").assertIsDisplayed()
    }

    @Test
    fun everyEntranceLockedShowsNoRoute() {
        container.clock.set(ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(23, 0)))
        waitForTag("destination_cs_r608")
        rule.onNodeWithTag("destination_cs_r608").performScrollTo().performClick()
        waitForTag("routeError")
        rule.onNodeWithText("No route to Room 608: every entrance is card-only at Sat 23:00.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Route to Room 608").performClick()
        waitForTag("noRoute")
        rule.onNodeWithText("Pick another room").performClick()
        waitForText("Where to?")
    }

    @Test
    fun glassesModeStartsAndStops() {
        openRoutesFor608()
        rule.onNodeWithTag("glassesButton").performClick()
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
